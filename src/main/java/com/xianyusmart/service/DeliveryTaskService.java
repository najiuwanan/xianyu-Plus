package com.xianyusmart.service;

import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.enums.DeliveryChannel;

import java.util.List;

public interface DeliveryTaskService {

    XianyuGoodsOrder discover(XianyuGoodsOrder order, DeliveryChannel channel);

    List<XianyuGoodsOrder> claimDueTasks(String workerId, int limit);

    void complete(Long taskId, String workerId);

    void retryOrFail(Long taskId, String workerId, String errorMessage);

    void markReviewRequired(Long taskId, String workerId, String errorMessage);

    boolean renewLease(Long taskId, String workerId);

    boolean requeue(Long taskId);

    /** 暂停账号尚未完成的自动发货任务，重新启用后会在下一次订单轮询中自动恢复。 */
    void pauseAccountTasks(Long accountId);

    /** 暂停当前刚被工作线程领取、但尚未开始执行的任务。 */
    void pauseClaimedTask(Long taskId, String workerId);
}
