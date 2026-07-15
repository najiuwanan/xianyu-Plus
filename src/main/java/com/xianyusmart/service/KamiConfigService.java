package com.xianyusmart.service;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.*;
import com.xianyusmart.entity.XianyuKamiConfig;
import com.xianyusmart.entity.XianyuKamiItem;

import java.util.List;

public interface KamiConfigService {

    ResultObject<KamiConfigRespDTO> createOrUpdateConfig(KamiConfigReqDTO reqDTO);

    ResultObject<List<KamiConfigRespDTO>> getConfigsByAccountId(Long xianyuAccountId);

    ResultObject<KamiConfigRespDTO> getConfigById(Long id);

    ResultObject<Void> deleteConfig(Long id);

    ResultObject<KamiItemRespDTO> addKamiItem(KamiItemReqDTO reqDTO);

    ResultObject<Integer> batchImportKamiItems(KamiBatchImportReqDTO reqDTO);

    ResultObject<List<KamiItemRespDTO>> getKamiItemsByConfigId(Long kamiConfigId);

    ResultObject<List<KamiItemRespDTO>> getKamiItemsByConfigIdWithFilter(KamiItemQueryReqDTO reqDTO);

    ResultObject<Void> deleteKamiItem(Long id);

    ResultObject<Void> resetKamiItem(Long id);

    XianyuKamiItem acquireKami(Long kamiConfigId, String orderId);

    List<XianyuKamiItem> reserveKami(Long kamiConfigId, String orderId, int quantity);

    void commitReservation(String orderId, Long accountId, String xyGoodsId, String buyerUserId, String buyerUserName);

    void releaseReservation(String orderId);

    void markReservationReviewRequired(String orderId);

    XianyuKamiConfig getConfig(Long kamiConfigId);

    ResultObject<List<KamiItemRespDTO>> exportKamiItems(KamiExportReqDTO reqDTO);
}
