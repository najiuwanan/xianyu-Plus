package com.xianyusmart.service;

import com.xianyusmart.controller.dto.KamiApiTestReqDTO;
import com.xianyusmart.controller.dto.KamiApiTestRespDTO;
import com.xianyusmart.entity.XianyuKamiConfig;
import com.xianyusmart.service.delivery.DeliveryContext;

/** 外部卡券供应商接口调用与订单级结果缓存。 */
public interface ApiKamiDeliveryService {

    String acquire(XianyuKamiConfig config, DeliveryContext context);

    KamiApiTestRespDTO test(KamiApiTestReqDTO request);
}
