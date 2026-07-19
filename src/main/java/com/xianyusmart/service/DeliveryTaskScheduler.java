package com.xianyusmart.service;

import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.enums.KamiStatus;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import com.xianyusmart.mapper.XianyuKamiItemMapper;
import com.xianyusmart.service.impl.AutoDeliveryServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.TaskScheduler;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Duration;

@Slf4j
@Component
public class DeliveryTaskScheduler {

    private final DeliveryTaskService deliveryTaskService;
    private final AutoDeliveryService autoDeliveryService;
    private final XianyuGoodsOrderMapper orderMapper;
    private final XianyuKamiItemMapper kamiItemMapper;
    private final XianyuAccountMapper accountMapper;
    private final OrderService orderService;
    private final PendingOrderPollService pendingOrderPollService;
    private final WebSocketService webSocketService;
    private final Executor taskExecutor;
    private final TaskScheduler taskScheduler;
    private final AutomationScheduleService automationScheduleService;
    private final BuyerBlacklistService blacklistService;
    private final String workerId = buildWorkerId();
    private final AtomicBoolean discoveringOrders = new AtomicBoolean(false);

    @Autowired(required = false)
    private AutomationRiskGuardService automationRiskGuardService;

    @Value("${app.delivery.claim-batch-size:20}")
    private int claimBatchSize;

    @Value("${app.delivery.lease-seconds:120}")
    private int leaseSeconds;

    public DeliveryTaskScheduler(DeliveryTaskService deliveryTaskService,
                                 AutoDeliveryService autoDeliveryService,
                                 XianyuGoodsOrderMapper orderMapper,
                                 XianyuKamiItemMapper kamiItemMapper,
                                 XianyuAccountMapper accountMapper,
                                 OrderService orderService,
                                 PendingOrderPollService pendingOrderPollService,
                                 WebSocketService webSocketService,
                                 @Qualifier("taskExecutor") Executor taskExecutor,
                                 @Qualifier("taskScheduler") TaskScheduler taskScheduler,
                                 AutomationScheduleService automationScheduleService,
                                 BuyerBlacklistService blacklistService) {
        this.deliveryTaskService = deliveryTaskService;
        this.autoDeliveryService = autoDeliveryService;
        this.orderMapper = orderMapper;
        this.kamiItemMapper = kamiItemMapper;
        this.accountMapper = accountMapper;
        this.orderService = orderService;
        this.pendingOrderPollService = pendingOrderPollService;
        this.webSocketService = webSocketService;
        this.taskExecutor = taskExecutor;
        this.taskScheduler = taskScheduler;
        this.automationScheduleService = automationScheduleService;
        this.blacklistService = blacklistService;
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    public void dispatchDueTasks() {
        if (!automationScheduleService.tryAcquire(AutomationScheduleService.DELIVERY_DISPATCH)) {
            return;
        }
        deliveryTaskService.claimDueTasks(workerId, claimBatchSize)
                .forEach(task -> taskExecutor.execute(() -> executeTask(task)));
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 60000)
    public void discoverOrdersFromApi() {
        if (!automationScheduleService.tryAcquire(AutomationScheduleService.ORDER_DISCOVERY)
                || !discoveringOrders.compareAndSet(false, true)) {
            return;
        }
        taskExecutor.execute(() -> {
            try {
                discoverOrders();
            } finally {
                discoveringOrders.set(false);
            }
        });
    }

    private void discoverOrders() {
        List<XianyuAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<XianyuAccount>()
                .eq(XianyuAccount::getStatus, 1));
        if (accounts == null) {
            return;
        }
        for (XianyuAccount account : accounts) {
            if (automationRiskGuardService != null && automationRiskGuardService.isPaused(account.getId())) {
                continue;
            }
            try {
                List<Map<String, Object>> pendingOrders = orderService.queryPendingOrders(account.getId());
                if (pendingOrders != null && !pendingOrders.isEmpty()) {
                    pendingOrderPollService.syncOrdersToDb(account.getId(), pendingOrders);
                }
            } catch (Exception e) {
                log.warn("【账号{}】待发货订单发现失败: {}", account.getId(), e.getMessage());
            }
        }
    }

    private void executeTask(XianyuGoodsOrder task) {
        if (blacklistService.isBlacklisted(task.getXianyuAccountId(), task.getBuyerUserId())) {
            String reason = blacklistService.blockedMessage(task.getXianyuAccountId(), task.getBuyerUserId());
            orderMapper.blockClaimedTaskByBlacklist(task.getId(), workerId, reason);
            log.warn("【账号{}】黑名单买家发货任务已终止: taskId={}, buyerUserId={}",
                    task.getXianyuAccountId(), task.getId(), task.getBuyerUserId());
            return;
        }
        XianyuAccount account = accountMapper.selectById(task.getXianyuAccountId());
        if (account == null || !Integer.valueOf(1).equals(account.getStatus())) {
            deliveryTaskService.pauseClaimedTask(task.getId(), workerId);
            log.info("【账号{}】已禁用或不可用，跳过自动发货任务 taskId={}", task.getXianyuAccountId(), task.getId());
            return;
        }
        if (automationRiskGuardService != null && automationRiskGuardService.isPaused(task.getXianyuAccountId())) {
            automationRiskGuardService.pauseClaimedDeliveryTask(task.getId(), workerId, task.getXianyuAccountId());
            log.warn("【账号{}】自动化保护已暂停，跳过自动发货任务 taskId={}", task.getXianyuAccountId(), task.getId());
            return;
        }
        long renewalSeconds = Math.max(10, leaseSeconds / 2L);
        ScheduledFuture<?> renewal = taskScheduler.scheduleAtFixedRate(
                () -> {
                    if (!deliveryTaskService.renewLease(task.getId(), workerId)) {
                        log.warn("发货任务续租失败，任务可能已被其他工作线程接管: taskId={}", task.getId());
                    }
                }, Duration.ofSeconds(renewalSeconds));
        try {
            String sId = task.getSid();
            if (sId == null || sId.isBlank()) {
                String receiverId = task.getBuyerUserId() != null ? task.getBuyerUserId() : task.getOrderId();
                sId = receiverId + "@goofish";
            }
            autoDeliveryService.executeDelivery(
                    task.getId(), task.getXianyuAccountId(), task.getXyGoodsId(), sId,
                    task.getOrderId(), task.getBuyerUserName(), false);

            XianyuGoodsOrder result = orderMapper.selectById(task.getId());
            if (result != null && Integer.valueOf(1).equals(result.getState())) {
                deliveryTaskService.complete(task.getId(), workerId);
            } else if (requiresManualReview(task, result)) {
                deliveryTaskService.markReviewRequired(task.getId(), workerId, result != null ? result.getFailReason() : null);
            } else {
                deliveryTaskService.retryOrFail(task.getId(), workerId, result != null ? result.getFailReason() : null);
            }
        } catch (Exception e) {
            log.error("订单任务执行异常: taskId={}, orderId={}", task.getId(), task.getOrderId(), e);
            deliveryTaskService.retryOrFail(task.getId(), workerId, e.getMessage());
        } finally {
            renewal.cancel(false);
        }
    }

    private boolean requiresManualReview(XianyuGoodsOrder task, XianyuGoodsOrder result) {
        return kamiItemMapper.countByOrderAndStatus(task.getOrderId(), KamiStatus.REVIEW_REQUIRED.getCode()) > 0
                || (result != null && result.getFailReason() != null
                && result.getFailReason().startsWith(AutoDeliveryServiceImpl.PARTIAL_DELIVERY_REVIEW_PREFIX));
    }

    private String buildWorkerId() {
        String host = Optional.ofNullable(System.getenv("HOSTNAME"))
                .orElse(Optional.ofNullable(System.getenv("COMPUTERNAME")).orElse("local"));
        return host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
