package com.xianyusmart.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import com.microsoft.playwright.options.Cookie;
import com.xianyusmart.config.PlaywrightManager;
import com.xianyusmart.controller.dto.ItemDTO;
import com.xianyusmart.controller.dto.SyncProgressRespDTO;
import com.xianyusmart.controller.dto.SyncSingleItemRespDTO;
import com.xianyusmart.entity.XianyuGoodsSku;
import com.xianyusmart.entity.XianyuGoodsSkuProperty;
import com.xianyusmart.service.AccountService;
import com.xianyusmart.service.GoodsInfoService;
import com.xianyusmart.service.GoodsSkuService;
import com.xianyusmart.service.GoodsSkuPropertyService;
import com.xianyusmart.service.ItemDetailSyncService;
import com.xianyusmart.utils.ItemDetailUtils;
import com.xianyusmart.utils.XianyuApiUtils;
import com.xianyusmart.utils.XianyuSignUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ItemDetailSyncServiceImpl implements ItemDetailSyncService {

    @Autowired
    private AccountService accountService;

    @Autowired
    private GoodsInfoService goodsInfoService;

    @Autowired
    private GoodsSkuService goodsSkuService;

    @Autowired
    private GoodsSkuPropertyService goodsSkuPropertyService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlaywrightManager playwrightManager;

    @Autowired
    @Lazy
    private ItemDetailSyncServiceImpl self;

    private final ConcurrentHashMap<String, SyncProgress> progressMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> accountSyncMap = new ConcurrentHashMap<>();

    private static final String GOOFISH_COOKIE_DOMAIN = ".goofish.com";
    private static final String TAOBAO_COOKIE_DOMAIN = ".taobao.com";
    private static final int H5_DETAIL_TIMEOUT_MS = 15_000;
    private static final long MAX_SYNC_RUNNING_MS = 90_000;
    private static final long MAX_SYNC_STALE_MS = 60_000;

    private static class SyncProgress {
        String syncId;
        Long accountId;
        int totalCount;
        int completedCount = 0;
        int successCount = 0;
        int failedCount = 0;
        int deferredCount = 0;
        boolean verificationRequired = false;
        String captchaUrl = null;
        boolean isCompleted = false;
        boolean isRunning = true;
        String currentItemId = null;
        String message = "同步中...";
        long startTime;
        long lastProgressTime;
        boolean cancelled = false;
    }

    @Override
    public String startSync(Long accountId, List<ItemDTO> items) {
        if (isSyncing(accountId)) {
            String existingSyncId = accountSyncMap.get(accountId);
            log.info("账号已有同步任务进行中: accountId={}, syncId={}", accountId, existingSyncId);
            return existingSyncId;
        }

        String syncId = UUID.randomUUID().toString();
        SyncProgress progress = new SyncProgress();
        progress.syncId = syncId;
        progress.accountId = accountId;
        progress.totalCount = items.size();
        progress.startTime = System.currentTimeMillis();
        progress.lastProgressTime = progress.startTime;

        progressMap.put(syncId, progress);
        accountSyncMap.put(accountId, syncId);

        if (items.isEmpty()) {
            progress.isCompleted = true;
            progress.isRunning = false;
            progress.message = "No items need detail sync";
            accountSyncMap.remove(accountId);
            return syncId;
        }

        String cookieStr = accountService.getCookieByAccountId(accountId);

        self.executeSync(syncId, accountId, items, cookieStr);

        log.info("启动异步详情同步: syncId={}, accountId={}, itemCount={}", syncId, accountId, items.size());
        return syncId;
    }

    @Async
    public void executeSync(String syncId, Long accountId, List<ItemDTO> items, String cookieStr) {
        SyncProgress progress = progressMap.get(syncId);
        if (progress == null) {
            log.error("同步进度不存在: syncId={}", syncId);
            return;
        }

        try {
            for (ItemDTO item : items) {
                if (progress.cancelled) {
                    progress.message = "同步已取消";
                    progress.lastProgressTime = System.currentTimeMillis();
                    break;
                }

                String itemId = item.getDetailParams() != null ? item.getDetailParams().getItemId() : item.getId();
                if (itemId == null || itemId.isEmpty()) {
                    progress.completedCount++;
                    progress.failedCount++;
                    progress.lastProgressTime = System.currentTimeMillis();
                    continue;
                }

                progress.currentItemId = itemId;
                progress.lastProgressTime = System.currentTimeMillis();

                try {
                    Thread.sleep(new Random().nextInt(501));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                DetailSyncResult result = fetchAndSaveDetail(itemId, cookieStr, accountId);
                
                progress.completedCount++;
                progress.lastProgressTime = System.currentTimeMillis();
                if (result.success()) {
                    progress.successCount++;
                } else {
                    progress.failedCount++;
                }

                if (result.verificationRequired()) {
                    progress.verificationRequired = true;
                    progress.captchaUrl = result.captchaUrl();
                    progress.deferredCount = Math.max(0, progress.totalCount - progress.completedCount);
                    progress.message = String.format(
                            "商品基础信息已同步；闲鱼要求安全验证，详情同步暂停（已完成 %d/%d，待补全 %d 个）",
                            progress.completedCount, progress.totalCount, progress.deferredCount + 1);
                    log.warn("商品详情同步触发闲鱼安全验证，停止继续请求避免重复触发: accountId={}, itemId={}, reason={}",
                            accountId, itemId, result.message());
                    break;
                }

                progress.message = String.format("同步进度: %d/%d", progress.completedCount, progress.totalCount);
            }

            progress.isCompleted = true;
            progress.isRunning = false;
            progress.currentItemId = null;
            if (!progress.verificationRequired) {
                progress.message = String.format("详情同步完成: 成功%d, 失败%d", progress.successCount, progress.failedCount);
            }

        } catch (Exception e) {
            log.error("异步同步异常: syncId={}", syncId, e);
            progress.isCompleted = true;
            progress.isRunning = false;
            progress.message = "同步失败: " + e.getMessage();
        } finally {
            accountSyncMap.remove(accountId);
        }
    }

    /**
     * 详情接口仍是首选；只有接口失败、返回空内容或进入验证态时才读取商品 H5 页面。
     * H5 读取仅用于获取页面上的正文，不会点击、拖动或尝试绕过任何安全验证。
     */
    private DetailSyncResult fetchAndSaveDetail(String itemId, String cookieStr, Long accountId) {
        DetailSyncResult mtopResult = fetchAndSaveDetailFromMtop(itemId, cookieStr, accountId);
        if (mtopResult.verificationRequired()) {
            return DetailSyncResult.verificationRequired(mtopResult.message(), buildH5ItemUrl(itemId));
        }

        String detailInfo = goodsInfoService.getDetailInfoByGoodsId(itemId);
        if (mtopResult.success() && detailInfo != null && !detailInfo.isBlank()) {
            return mtopResult;
        }

        H5DetailResult h5Result = fetchDescriptionFromH5(itemId, cookieStr);
        if (h5Result.verificationRequired()) {
            return DetailSyncResult.verificationRequired(h5Result.message(), h5Result.captchaUrl());
        }
        if (h5Result.description() != null && !h5Result.description().isBlank()) {
            goodsInfoService.updateDetailInfo(itemId, h5Result.description());
            log.info("商品详情已通过 H5 页面补全: itemId={}", itemId);
            return DetailSyncResult.successful();
        }
        return mtopResult.success()
                ? DetailSyncResult.failed("商品详情未返回可读取的文字内容")
                : mtopResult;
    }

    private DetailSyncResult fetchAndSaveDetailFromMtop(String itemId, String cookieStr, Long accountId) {
        try {
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("itemId", itemId);

            String response = XianyuApiUtils.callApi(
                "mtop.taobao.idle.pc.detail",
                dataMap,
                cookieStr
            );

            if (response == null) {
                log.warn("获取商品详情失败，响应为空: itemId={}", itemId);
                return DetailSyncResult.failed("商品详情响应为空");
            }

            log.debug("商品详情同步接口已返回响应: itemId={}, 长度={}（内容不写入日志）", itemId, response.length());

            String businessError = getBusinessError(response);
            if (businessError != null) {
                boolean verificationRequired = isVerificationRequired(businessError);
                log.warn("商品详情接口返回业务失败: itemId={}, verificationRequired={}, error={}",
                        itemId, verificationRequired, businessError);
                return verificationRequired
                        ? DetailSyncResult.verificationRequired(businessError)
                        : DetailSyncResult.failed(businessError);
            }

            String extractedDesc = extractDescFromDetailJson(response);
            
            if (extractedDesc != null && !extractedDesc.isEmpty()) {
                goodsInfoService.updateDetailInfo(itemId, extractedDesc);
            }

            List<XianyuGoodsSku> skuList = ItemDetailUtils.extractSkuList(response);
            if (!skuList.isEmpty()) {
                goodsSkuService.saveSkus(itemId, accountId, skuList);
                goodsInfoService.updateSkuCount(itemId, skuList.size());
                List<XianyuGoodsSkuProperty> propertyList = ItemDetailUtils.extractSkuPropertyList(response);
                if (!propertyList.isEmpty()) {
                    goodsSkuPropertyService.saveProperties(itemId, accountId, propertyList);
                }
            } else {
                goodsSkuService.deleteByAccountIdAndXyGoodsId(accountId, itemId);
                goodsSkuPropertyService.deleteByAccountIdAndXyGoodsId(accountId, itemId);
                goodsInfoService.updateSkuCount(itemId, 0);
            }

            log.debug("商品详情同步成功: itemId={}", itemId);
            return DetailSyncResult.successful();

        } catch (Exception e) {
            log.error("获取商品详情异常: itemId={}", itemId, e);
            return DetailSyncResult.failed("商品详情解析失败: " + e.getMessage());
        }
    }

    private H5DetailResult fetchDescriptionFromH5(String itemId, String cookieStr) {
        String h5ItemUrl = buildH5ItemUrl(itemId);
        try (BrowserContext context = playwrightManager.createContext()) {
            context.addCookies(buildBrowserCookies(cookieStr));
            Page page = context.newPage();
            page.navigate(h5ItemUrl, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(H5_DETAIL_TIMEOUT_MS));
            page.waitForTimeout(900);

            String pageText = getPageText(page);
            if (isVerificationRequired(page.url() + "\n" + pageText)) {
                log.info("商品 H5 页面要求人工验证: itemId={}, url={}", itemId, page.url());
                return H5DetailResult.verificationRequired("闲鱼要求安全验证", page.url());
            }

            String description = extractDescriptionFromH5(page);
            if (description != null && !description.isBlank()) {
                return H5DetailResult.successful(description);
            }
            log.info("商品 H5 页面未找到可读取的文字详情: itemId={}", itemId);
            return H5DetailResult.failed();
        } catch (Exception e) {
            log.warn("读取商品 H5 页面失败: itemId={}, message={}", itemId, e.getMessage());
            return H5DetailResult.failed();
        }
    }

    private List<Cookie> buildBrowserCookies(String cookieStr) {
        Map<String, String> cookieMap = XianyuSignUtils.parseCookies(cookieStr);
        List<Cookie> browserCookies = new ArrayList<>();
        for (Map.Entry<String, String> entry : cookieMap.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            browserCookies.add(new Cookie(entry.getKey(), entry.getValue()).setDomain(GOOFISH_COOKIE_DOMAIN).setPath("/"));
            browserCookies.add(new Cookie(entry.getKey(), entry.getValue()).setDomain(TAOBAO_COOKIE_DOMAIN).setPath("/"));
        }
        return browserCookies;
    }

    private String getPageText(Page page) {
        Object value = page.evaluate("() => document.body ? document.body.innerText : ''");
        return value == null ? "" : String.valueOf(value);
    }

    private String extractDescriptionFromH5(Page page) {
        Object value = page.evaluate("""
                () => {
                  const selectors = [
                    '[class*="detail-desc"]', '[class*="detailDesc"]',
                    '[class*="goods-detail"]', '[class*="goodsDetail"]',
                    '[class*="item-detail"]', '[class*="itemDetail"]',
                    '[data-testid*="detail"]', '[data-testid*="desc"]'
                  ];
                  for (const selector of selectors) {
                    for (const node of document.querySelectorAll(selector)) {
                      const text = (node.innerText || node.textContent || '').trim();
                      if (text.length >= 20) return text;
                    }
                  }
                  return '';
                }
                """);
        String description = value == null ? "" : String.valueOf(value).trim();
        return description.length() >= 20 ? description : null;
    }

    private String buildH5ItemUrl(String itemId) {
        // m.goofish.com is not a public web host and may not resolve from Docker.
        return "https://www.goofish.com/item?id=" + itemId;
    }

    @Override
    public SyncSingleItemRespDTO syncSingleItem(Long accountId, String itemId) {
        if (accountId == null || itemId == null || itemId.isEmpty()) {
            log.warn("同步单个商品参数无效: accountId={}, itemId={}", accountId, itemId);
            return buildSingleSyncResult(false, false, "商品信息不完整，无法同步详情");
        }
        String cookieStr = accountService.getCookieByAccountId(accountId);
        if (cookieStr == null || cookieStr.isEmpty()) {
            log.warn("账号Cookie不存在: accountId={}", accountId);
            return buildSingleSyncResult(false, false, "账号登录信息已失效，请重新连接账号后再试");
        }
        log.info("同步单个商品: accountId={}, itemId={}", accountId, itemId);
        DetailSyncResult result = fetchAndSaveDetail(itemId, cookieStr, accountId);
        if (result.success()) {
            return buildSingleSyncResult(true, false, "商品详情同步成功");
        }
        if (result.verificationRequired()) {
            return buildSingleSyncResult(false, true,
                    "闲鱼要求安全验证，商品基础信息已同步。请在闲鱼客户端确认账号状态后，在账号管理中使用“凭证更新”重新扫码，再重试商品详情同步。");
        }
        if (result.verificationRequired() && result.captchaUrl() != null && !result.captchaUrl().isBlank()) {
            return buildSingleSyncResult(false, true,
                    "闲鱼要求安全验证，请在页面中完成验证后自动重试", result.captchaUrl());
        }
        if (result.verificationRequired()) {
            return buildSingleSyncResult(false, true,
                    "闲鱼要求安全验证，暂时无法读取商品详情；请在闲鱼客户端完成验证后稍后重试");
        }
        return buildSingleSyncResult(false, false,
                result.message() == null || result.message().isBlank() ? "商品详情同步失败，请稍后重试" : result.message());
    }

    private SyncSingleItemRespDTO buildSingleSyncResult(boolean success, boolean verificationRequired, String message) {
        return buildSingleSyncResult(success, verificationRequired, message, null);
    }

    private SyncSingleItemRespDTO buildSingleSyncResult(boolean success, boolean verificationRequired, String message, String captchaUrl) {
        SyncSingleItemRespDTO result = new SyncSingleItemRespDTO();
        result.setSuccess(success);
        result.setVerificationRequired(verificationRequired);
        result.setMessage(message);
        result.setCaptchaUrl(captchaUrl);
        return result;
    }

    private String getBusinessError(String response) {
        try {
            JsonNode retNode = objectMapper.readTree(response).path("ret");
            if (!retNode.isArray()) {
                return null;
            }
            for (JsonNode ret : retNode) {
                String value = ret.asText();
                if (value != null && !value.toUpperCase(Locale.ROOT).startsWith("SUCCESS")) {
                    return value;
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("无法识别商品详情接口的业务状态: {}", e.getMessage());
            return "商品详情接口返回格式异常";
        }
    }

    private boolean isVerificationRequired(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("fail_sys_user_validate")
                || normalized.contains("rgv587_error")
                || normalized.contains("captcha")
                || normalized.contains("slider")
                || normalized.contains("security verification")
                || normalized.contains("\u5b89\u5168\u9a8c\u8bc1")
                || normalized.contains("\u6ed1\u52a8\u9a8c\u8bc1")
                || normalized.contains("\u8bf7\u5b8c\u6210\u9a8c\u8bc1")
                || normalized.contains("安全验证");
    }

    private record DetailSyncResult(boolean success, boolean verificationRequired, String message, String captchaUrl) {
        static DetailSyncResult successful() {
            return new DetailSyncResult(true, false, null, null);
        }

        static DetailSyncResult failed(String message) {
            return new DetailSyncResult(false, false, message, null);
        }

        static DetailSyncResult verificationRequired(String message) {
            return verificationRequired(message, null);
        }

        static DetailSyncResult verificationRequired(String message, String captchaUrl) {
            return new DetailSyncResult(false, true, message, captchaUrl);
        }
    }

    private record H5DetailResult(String description, boolean verificationRequired, String message, String captchaUrl) {
        static H5DetailResult successful(String description) {
            return new H5DetailResult(description, false, null, null);
        }

        static H5DetailResult failed() {
            return new H5DetailResult(null, false, null, null);
        }

        static H5DetailResult verificationRequired(String message, String captchaUrl) {
            return new H5DetailResult(null, true, message, captchaUrl);
        }
    }

    private String extractDescFromDetailJson(String detailJson) {
        try {
            JsonNode rootNode = objectMapper.readTree(detailJson);
            
            JsonNode dataNode = rootNode.get("data");
            if (dataNode == null || dataNode.isNull()) {
                log.warn("未找到data字段");
                return null;
            }

            JsonNode itemDONode = dataNode.get("itemDO");
            if (itemDONode == null || itemDONode.isNull()) {
                log.warn("未找到itemDO字段");
                return null;
            }

            JsonNode descNode = itemDONode.get("desc");
            if (descNode != null && !descNode.isNull()) {
                return descNode.asText();
            } else {
                log.warn("itemDO中未找到desc字段");
                return null;
            }

        } catch (Exception e) {
            log.error("解析商品详情JSON失败", e);
            return null;
        }
    }

    @Override
    public SyncProgressRespDTO getProgress(String syncId) {
        SyncProgress progress = progressMap.get(syncId);
        if (progress == null) {
            return null;
        }
        closeStaleProgress(progress);

        SyncProgressRespDTO dto = new SyncProgressRespDTO();
        dto.setSyncId(progress.syncId);
        dto.setAccountId(progress.accountId);
        dto.setTotalCount(progress.totalCount);
        dto.setCompletedCount(progress.completedCount);
        dto.setSuccessCount(progress.successCount);
        dto.setFailedCount(progress.failedCount);
        dto.setDeferredCount(progress.deferredCount);
        dto.setVerificationRequired(progress.verificationRequired);
        dto.setCaptchaUrl(progress.captchaUrl);
        dto.setIsCompleted(progress.isCompleted);
        dto.setIsRunning(progress.isRunning);
        dto.setCurrentItemId(progress.currentItemId);
        dto.setMessage(progress.message);
        dto.setStartTime(progress.startTime);

        if (progress.completedCount > 0 && progress.totalCount > 0) {
            long elapsed = System.currentTimeMillis() - progress.startTime;
            long avgTimePerItem = elapsed / progress.completedCount;
            long remainingItems = progress.totalCount - progress.completedCount;
            dto.setEstimatedRemainingTime(avgTimePerItem * remainingItems);
        }

        return dto;
    }

    @Override
    public void cancelSync(String syncId) {
        SyncProgress progress = progressMap.get(syncId);
        if (progress != null) {
            progress.cancelled = true;
            progress.message = "正在取消同步...";
            progress.lastProgressTime = System.currentTimeMillis();
            log.info("取消同步: syncId={}", syncId);
        }
    }

    @Override
    public boolean isSyncing(Long accountId) {
        String syncId = accountSyncMap.get(accountId);
        if (syncId == null) {
            return false;
        }
        SyncProgress progress = progressMap.get(syncId);
        closeStaleProgress(progress);
        return progress != null && progress.isRunning && !progress.isCompleted;
    }

    private void closeStaleProgress(SyncProgress progress) {
        if (progress == null || progress.isCompleted || !progress.isRunning) {
            return;
        }
        long now = System.currentTimeMillis();
        long startedAt = progress.startTime > 0 ? progress.startTime : now;
        long lastChangedAt = progress.lastProgressTime > 0 ? progress.lastProgressTime : startedAt;
        if (now - startedAt < MAX_SYNC_RUNNING_MS && now - lastChangedAt < MAX_SYNC_STALE_MS) {
            return;
        }
        progress.isCompleted = true;
        progress.isRunning = false;
        progress.failedCount += Math.max(0, progress.totalCount - progress.completedCount);
        progress.message = "详情同步超时，已保留商品基础信息";
        accountSyncMap.remove(progress.accountId);
        log.warn("Detail sync timed out and was closed: syncId={}, accountId={}, completed={}/{}",
                progress.syncId, progress.accountId, progress.completedCount, progress.totalCount);
    }
}
