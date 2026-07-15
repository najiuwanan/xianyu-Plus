package com.xianyusmart.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuCookie;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuCookieMapper;
import com.xianyusmart.utils.XianyuApiUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自动评价服务类
 */
@Slf4j
@Service
public class RateService {

    @Autowired
    private XianyuAccountMapper accountMapper;

    @Autowired
    private XianyuCookieMapper cookieMapper;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 对指定的订单进行买家评价
     *
     * @param accountId 闲鱼账号ID
     * @param tradeId   订单号
     * @param feedback  评价内容
     * @return 是否评价成功
     */
    public boolean rateBuyer(Long accountId, String tradeId, String feedback) {
        if (accountId == null || tradeId == null || feedback == null) {
            return false;
        }

        XianyuCookie cookie = cookieMapper.selectOne(new QueryWrapper<XianyuCookie>().eq("xianyu_account_id", accountId));
        if (cookie == null || cookie.getCookiesStr() == null) {
            log.warn("【自动评价】账号 {} Cookie不存在", accountId);
            return false;
        }

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("tradeId", tradeId);
        dataMap.put("rate", 1);
        dataMap.put("feedback", feedback);
        dataMap.put("createOrAppend", 0);

        log.info("【自动评价】尝试给订单 {} 提交好评...", tradeId);
        String response = XianyuApiUtils.callApi("mtop.taobao.idle.rate.create", dataMap, cookie.getCookiesStr());

        if (XianyuApiUtils.isSuccess(response)) {
            log.info("【自动评价】订单 {} 评价成功", tradeId);
            return true;
        } else {
            String error = XianyuApiUtils.extractError(response);
            if (error.contains("已评价") || error.contains("重复评价")) {
                log.info("【自动评价】订单 {} 已经评价过了", tradeId);
                return true;
            }
            log.warn("【自动评价】订单 {} 评价失败: {}", tradeId, error);
            return false;
        }
    }

    /**
     * 获取待评价订单列表
     */
    public List<Map<String, Object>> getPendingRateList(Long accountId) {
        XianyuCookie cookie = cookieMapper.selectOne(new QueryWrapper<XianyuCookie>().eq("xianyu_account_id", accountId));
        if (cookie == null || cookie.getCookiesStr() == null) {
            return null;
        }

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("pageNumber", 1);
        dataMap.put("rowsPerPage", 50);
        dataMap.put("queryType", "ORDER");
        Map<String, String> searchParam = new HashMap<>();
        searchParam.put("sellerRateStatus", "5");
        dataMap.put("rateSearchParam", searchParam);

        String response = XianyuApiUtils.callApi("mtop.taobao.idle.merchant.rate.list", dataMap, cookie.getCookiesStr());
        
        if (XianyuApiUtils.isSuccess(response)) {
            Map<String, Object> data = XianyuApiUtils.extractData(response);
            if (data != null && data.get("module") != null) {
                Map<String, Object> module = (Map<String, Object>) data.get("module");
                return (List<Map<String, Object>>) module.get("items");
            }
        }
        return null;
    }
}
