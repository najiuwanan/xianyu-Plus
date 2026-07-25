package com.xianyusmart.service;

import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 重启可恢复的自动确认发货任务。 */
@Slf4j
@Component
public class ConfirmShipmentTaskScheduler {

    private final XianyuGoodsOrderMapper orderMapper;
    private final XianyuAccountMapper accountMapper;
    private final OrderService orderService;
    private final RedFlowerService redFlowerService;
    private final String workerId = "confirm-" + UUID.randomUUID().toString().substring(0, 8);

    @Value("${app.delivery.confirm-lease-seconds:90}")
    private int leaseSeconds;

    public ConfirmShipmentTaskScheduler(XianyuGoodsOrderMapper orderMapper,
                                        XianyuAccountMapper accountMapper,
                                        OrderService orderService,
                                        RedFlowerService redFlowerService) {
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.orderService = orderService;
        this.redFlowerService = redFlowerService;
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    public void poll() {
        for (XianyuGoodsOrder task : orderMapper.findDueConfirmShipmentTasks(20)) {
            if (task.getId() == null || orderMapper.claimConfirmShipmentTask(
                    task.getId(), workerId, Math.max(30, leaseSeconds)) != 1) {
                continue;
            }
            execute(task.getId());
        }
    }

    private void execute(Long taskId) {
        XianyuGoodsOrder task = orderMapper.selectById(taskId);
        if (task == null) return;
        try {
            if ("PICKUP".equalsIgnoreCase(task.getDeliveryChannel())) {
                orderMapper.skipConfirmShipmentTask(taskId, workerId, "自提订单无需确认发货");
                return;
            }
            XianyuAccount account = accountMapper.selectById(task.getXianyuAccountId());
            if (account == null || !Integer.valueOf(1).equals(account.getStatus())) {
                orderMapper.deferConfirmShipmentTask(taskId, workerId, "账号已停用，等待恢复后重试");
                return;
            }
            String result = orderService.confirmShipment(task.getXianyuAccountId(), task.getOrderId());
            if (result == null) {
                orderMapper.retryOrFailConfirmShipmentTask(taskId, workerId, "平台确认发货失败，五分钟后重试");
                return;
            }
            if (orderMapper.completeConfirmShipmentTask(taskId, workerId) == 1) {
                log.info("【账号{}】自动确认发货成功: orderId={}", task.getXianyuAccountId(), task.getOrderId());
                redFlowerService.requestAfterShipmentConfirmed(task.getXianyuAccountId(), task.getOrderId());
            }
        } catch (Exception e) {
            log.error("自动确认发货任务异常: taskId={}, orderId={}", taskId, task.getOrderId(), e);
            orderMapper.retryOrFailConfirmShipmentTask(taskId, workerId,
                    e.getMessage() == null ? "自动确认发货异常" : e.getMessage());
        }
    }
}
