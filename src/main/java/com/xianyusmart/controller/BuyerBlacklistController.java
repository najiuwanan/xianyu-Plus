package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.BuyerBlacklistReqDTO;
import com.xianyusmart.entity.XianyuBuyerBlacklist;
import com.xianyusmart.service.BuyerBlacklistService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/blacklist")
public class BuyerBlacklistController {

    private final BuyerBlacklistService blacklistService;

    public BuyerBlacklistController(BuyerBlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    @PostMapping("/list")
    public ResultObject<List<XianyuBuyerBlacklist>> list(@RequestBody(required = false) BuyerBlacklistReqDTO request) {
        return ResultObject.success(blacklistService.list(
                request == null ? null : request.getXianyuAccountId(),
                request == null ? null : request.getKeyword()));
    }

    @PostMapping("/save")
    public ResultObject<XianyuBuyerBlacklist> save(@RequestBody BuyerBlacklistReqDTO request) {
        try {
            return ResultObject.success(blacklistService.save(request));
        } catch (IllegalArgumentException e) {
            return ResultObject.validateFailed(e.getMessage());
        }
    }

    @PostMapping("/delete")
    public ResultObject<String> delete(@RequestBody Map<String, Long> request) {
        try {
            blacklistService.delete(request.get("id"));
            return ResultObject.success("已解除黑名单");
        } catch (IllegalArgumentException e) {
            return ResultObject.validateFailed(e.getMessage());
        }
    }

    @PostMapping("/check")
    public ResultObject<XianyuBuyerBlacklist> check(@RequestBody BuyerBlacklistReqDTO request) {
        return ResultObject.success(blacklistService.findActive(request.getXianyuAccountId(), request.getBuyerUserId()));
    }
}
