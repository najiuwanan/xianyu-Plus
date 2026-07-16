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

    void requeue(Long taskId);
}
