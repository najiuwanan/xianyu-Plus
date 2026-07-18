package com.xianyusmart.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

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
    @Lazy
    private ItemDetailSyncServiceImpl self;

    private final ConcurrentHashMap<String, SyncProgress> progressMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> accountSyncMap = new ConcurrentHashMap<>();

    private static class SyncProgress {
        String syncId;
        Long accountId;
        int totalCount;
        int completedCount = 0;
        int successCount = 0;
        int failedCount = 0;
        int deferredCount = 0;
        boolean verificationRequired = false;
        boolean isCompleted = false;
        boolean isRunning = true;
        String currentItemId = null;
        String message = "同步中...";
        long startTime;
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

        progressMap.put(syncId, progress);
        accountSyncMap.put(accountId, syncId);

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
                    break;
                }

                String itemId = item.getDetailParams() != null ? item.getDetailParams().getItemId() : item.getId();
                if (itemId == null || itemId.isEmpty()) {
                    progress.completedCount++;
                    progress.failedCount++;
                    continue;
                }

                progress.currentItemId = itemId;

                try {
                    Thread.sleep(new Random().nextInt(501));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                DetailSyncResult result = fetchAndSaveDetail(itemId, cookieStr, accountId);
                
                progress.completedCount++;
                if (result.success()) {
                    progress.successCount++;
                } else {
                    progress.failedCount++;
                }

                if (result.verificationRequired()) {
                    progress.verificationRequired = true;
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

    private DetailSyncResult fetchAndSaveDetail(String itemId, String cookieStr, Long accountId) {
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

            log.info("mtop.taobao.idle.pc.detail 完整响应: itemId={}, response={}", itemId, response);

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
                    "闲鱼要求安全验证，暂时无法读取商品详情；请在闲鱼客户端完成验证后稍后重试");
        }
        return buildSingleSyncResult(false, false,
                result.message() == null || result.message().isBlank() ? "商品详情同步失败，请稍后重试" : result.message());
    }

    private SyncSingleItemRespDTO buildSingleSyncResult(boolean success, boolean verificationRequired, String message) {
        SyncSingleItemRespDTO result = new SyncSingleItemRespDTO();
        result.setSuccess(success);
        result.setVerificationRequired(verificationRequired);
        result.setMessage(message);
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
                || normalized.contains("安全验证");
    }

    private record DetailSyncResult(boolean success, boolean verificationRequired, String message) {
        static DetailSyncResult successful() {
            return new DetailSyncResult(true, false, null);
        }

        static DetailSyncResult failed(String message) {
            return new DetailSyncResult(false, false, message);
        }

        static DetailSyncResult verificationRequired(String message) {
            return new DetailSyncResult(false, true, message);
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

        SyncProgressRespDTO dto = new SyncProgressRespDTO();
        dto.setSyncId(progress.syncId);
        dto.setAccountId(progress.accountId);
        dto.setTotalCount(progress.totalCount);
        dto.setCompletedCount(progress.completedCount);
        dto.setSuccessCount(progress.successCount);
        dto.setFailedCount(progress.failedCount);
        dto.setDeferredCount(progress.deferredCount);
        dto.setVerificationRequired(progress.verificationRequired);
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
        return progress != null && progress.isRunning && !progress.isCompleted;
    }
}
