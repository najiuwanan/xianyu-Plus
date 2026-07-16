package com.xianyusmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.mapper.OrderAutomationRecordMapper;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.utils.XianyuApiCallUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 调用闲鱼专用接口为符合条件的订单求小红花。
 */
@Slf4j
@Service
public class RedFlowerService {

    private static final String RED_FLOWER_API = "mtop.taobao.idlemessage.red.flower";

    private final XianyuAccountMapper accountMapper;
    private final AccountService accountService;
    private final OrderAutomationRecordMapper automationRecordMapper;
    private final XianyuApiCallUtils xianyuApiCallUtils;

    @Value("${app.red-flower.lookback-days:10}")
    private int lookbackDays = 10;

    @Value("${app.red-flower.batch-size:50}")
    private int batchSize = 50;

    public RedFlowerService(XianyuAccountMapper accountMapper,
                            AccountService accountService,
                            OrderAutomationRecordMapper automationRecordMapper,
                            XianyuApiCallUtils xianyuApiCallUtils) {
        this.accountMapper = accountMapper;
        this.accountService = accountService;
        this.automationRecordMapper = automationRecordMapper;
        this.xianyuApiCallUtils = xianyuApiCallUtils;
    }

    /**
     * 扫描已成功自动发货、尚未求小红花的近期订单。
     */
    public void processPendingRedFlowers() {
        List<XianyuAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<XianyuAccount>()
                .eq(XianyuAccount::getStatus, 1)
                .eq(XianyuAccount::getAutoAskFlower, 1));
        for (XianyuAccount account : accounts) {
            processAccount(account.getId());
        }
    }

    void processAccount(Long accountId) {
        if (accountId == null) {
            return;
        }

        List<XianyuGoodsOrder> orders = automationRecordMapper.findRedFlowerCandidates(
                accountId, lookbackDays, batchSize);
        if (orders.isEmpty()) {
            return;
        }

        String cookie = accountService.getCookieByAccountId(accountId);
        if (cookie == null || cookie.isBlank()) {
            String error = "Cookie 不可用，请重新登录该闲鱼账号后重试";
            for (XianyuGoodsOrder order : orders) {
                automationRecordMapper.markRedFlowerFailure(accountId, order.getOrderId(), error);
            }
            log.warn("【账号{}】求小红花失败：Cookie 不可用", accountId);
            return;
        }

        for (XianyuGoodsOrder order : orders) {
            requestRedFlower(accountId, order.getOrderId(), cookie);
        }
    }

    /**
     * 在执行中心中对单笔订单立即补发小红花请求。
     */
    public boolean retryRedFlower(Long accountId, String orderId) {
        if (accountId == null || orderId == null || orderId.isBlank()) {
            return false;
        }
        String cookie = accountService.getCookieByAccountId(accountId);
        if (cookie == null || cookie.isBlank()) {
            automationRecordMapper.markRedFlowerFailure(accountId, orderId,
                    "Cookie 不可用，请重新登录该闲鱼账号后重试");
            log.warn("【账号{}】手动重试小红花失败：Cookie 不可用，orderId={}", accountId, orderId);
            return false;
        }
        return requestRedFlower(accountId, orderId, cookie);
    }

    private boolean requestRedFlower(Long accountId, String orderId, String cookie) {
        if (orderId == null || orderId.isBlank()) {
            return false;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("channel", "list");

        Map<String, String> headers = new HashMap<>();
        headers.put("Origin", "https://www.goofish.com");
        headers.put("Referer", "https://www.goofish.com/");

        Map<String, String> query = new HashMap<>();
        query.put("v", "4.0");
        query.put("type", "originaljson");

        XianyuApiCallUtils.ApiCallResult result = xianyuApiCallUtils.callApiWithRetry(
                accountId, RED_FLOWER_API, payload, cookie, "1.0", headers, query);
        if (result.isSuccess()) {
            automationRecordMapper.markRedFlowerSuccess(accountId, orderId);
            log.info("【账号{}】订单求小红花成功：orderId={}", accountId, orderId);
            return true;
        }

        String error = truncate(result.getErrorMessage());
        automationRecordMapper.markRedFlowerFailure(accountId, orderId, error);
        log.warn("【账号{}】订单求小红花失败：orderId={}, reason={}", accountId, orderId, error);
        return false;
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "接口调用失败，未返回错误信息";
        }
        return message.substring(0, Math.min(message.length(), 500));
    }
}
