package com.xianyusmart.service;

import com.xianyusmart.controller.dto.BuyerBlacklistReqDTO;
import com.xianyusmart.entity.XianyuBuyerBlacklist;

import java.util.List;

public interface BuyerBlacklistService {
    boolean isBlacklisted(Long accountId, String buyerUserId);
    XianyuBuyerBlacklist findActive(Long accountId, String buyerUserId);
    List<XianyuBuyerBlacklist> list(Long accountId, String keyword);
    XianyuBuyerBlacklist save(BuyerBlacklistReqDTO request);
    void delete(Long id);
    String blockedMessage(Long accountId, String buyerUserId);
}
