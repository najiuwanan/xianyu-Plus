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
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<Long, Long> lastStatusReconcileAt = new ConcurrentHashMap<>();

    /** Buyer receipt/refund changes do not always arrive as chat events, so keep a low-frequency reconciliation. */
    private static final long STATUS_RECONCILE_INTERVAL_MS = Duration.ofMinutes(5).toMillis();

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
                boolean connected = webSocketService.isConnected(account.getId());
                if (!connected) {
                    List<Map<String, Object>> pendingOrders = orderService.queryPendingOrders(account.getId());
                    if (pendingOrders != null && !pendingOrders.isEmpty()) {
                        pendingOrderPollService.syncOrdersToDb(account.getId(), pendingOrders);
                    }
                } else {
                    log.debug("【账号{}】WebSocket在线，跳过待发货订单轮询", account.getId());
                }

                // Receipt/refund changes are reconciled at most once every five minutes.
                // This preserves automatic rating without repeatedly scanning order history.
                if (shouldReconcileOrderStatus(account.getId())) {
                    pendingOrderPollService.refreshRecentSoldOrderHistory(account.getId());
                }
            } catch (Exception e) {
                log.warn("【账号{}】待发货订单发现失败: {}", account.getId(), e.getMessage());
            }
        }
    }

    private boolean shouldReconcileOrderStatus(Long accountId) {
        long now = System.currentTimeMillis();
        Long previous = lastStatusReconcileAt.putIfAbsent(accountId, now);
        if (previous == null) {
            return true;
        }
        if (now - previous < STATUS_RECONCILE_INTERVAL_MS) {
            return false;
        }
        return lastStatusReconcileAt.replace(accountId, previous, now);
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
        AtomicBoolean leaseActive = new AtomicBoolean(true);
        AtomicBoolean externalAttemptStarted = new AtomicBoolean(false);
        long renewalSeconds = Math.max(10, leaseSeconds / 2L);
        ScheduledFuture<?> renewal = taskScheduler.scheduleAtFixedRate(
                () -> {
                    if (!deliveryTaskService.renewLease(task.getId(), workerId)) {
                        leaseActive.set(false);
                        log.warn("发货任务续租失败，旧任务将立即停止发送: taskId={}", task.getId());
                    }
                }, Duration.ofSeconds(renewalSeconds));
        try {
            String sId = task.getSid();
            if (sId == null || sId.isBlank()) {
                String receiverId = task.getBuyerUserId() != null ? task.getBuyerUserId() : task.getOrderId();
                sId = receiverId + "@goofish";
            }
            java.util.function.BooleanSupplier executionAllowed = () -> leaseActive.get()
                    && deliveryTaskService.isLeaseActive(task.getId(), workerId);
            java.util.function.BooleanSupplier externalAttemptAllowed = () -> {
                if (!executionAllowed.getAsBoolean()) return false;
                if (externalAttemptStarted.compareAndSet(false, true)) {
                    return deliveryTaskService.beginExternalAttempt(task.getId(), workerId);
                }
                return executionAllowed.getAsBoolean();
            };

            autoDeliveryService.executeDelivery(
                    task.getId(), task.getXianyuAccountId(), task.getXyGoodsId(), sId,
                    task.getOrderId(), task.getBuyerUserName(), false, executionAllowed,
                    externalAttemptAllowed);

            if (!leaseActive.get()) {
                return;
            }
            XianyuGoodsOrder result = orderMapper.selectById(task.getId());
            if (requiresManualReview(task, result)) {
                deliveryTaskService.markReviewRequired(task.getId(), workerId, result != null ? result.getFailReason() : null);
            } else if (result != null && Integer.valueOf(1).equals(result.getState())) {
                deliveryTaskService.complete(task.getId(), workerId);
            } else if (requiresBuyerVerification(result)) {
                deliveryTaskService.deferBuyerVerification(task.getId(), workerId, result.getFailReason());
            } else {
                deliveryTaskService.retryOrFail(task.getId(), workerId, result != null ? result.getFailReason() : null);
            }
        } catch (Exception e) {
            log.error("订单任务执行异常: taskId={}, orderId={}", task.getId(), task.getOrderId(), e);
            XianyuGoodsOrder current = orderMapper.selectById(task.getId());
            if (current != null && "EXTERNAL_SEND_STARTED".equals(current.getLastErrorCode())) {
                deliveryTaskService.markReviewRequired(task.getId(), workerId,
                        "外部发送开始后本地状态提交失败，结果需要人工核对");
            } else {
                deliveryTaskService.retryOrFail(task.getId(), workerId, e.getMessage());
            }
        } finally {
            renewal.cancel(false);
        }
    }

    private boolean requiresBuyerVerification(XianyuGoodsOrder result) {
        return result != null && result.getFailReason() != null
                && result.getFailReason().startsWith(AutoDeliveryServiceImpl.BUYER_VERIFICATION_PENDING_PREFIX);
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
