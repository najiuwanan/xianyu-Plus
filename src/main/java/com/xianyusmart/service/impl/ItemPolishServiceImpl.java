package com.xianyusmart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.AllItemsReqDTO;
import com.xianyusmart.controller.dto.RefreshItemsRespDTO;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuGoodsInfo;
import com.xianyusmart.entity.XianyuItemPolishConfig;
import com.xianyusmart.entity.XianyuItemPolishRecord;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuGoodsInfoMapper;
import com.xianyusmart.mapper.XianyuItemPolishConfigMapper;
import com.xianyusmart.mapper.XianyuItemPolishRecordMapper;
import com.xianyusmart.service.AccountService;
import com.xianyusmart.service.ItemService;
import com.xianyusmart.service.ItemPolishService;
import com.xianyusmart.utils.XianyuApiCallUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 自动擦亮服务。
 * 仅使用本项目已存在的 H5 签名、Cookie 自动更新与重试能力，不启动浏览器进程。
 */
@Slf4j
@Service
public class ItemPolishServiceImpl implements ItemPolishService {

    private static final String PRIMARY_API = "mtop.taobao.idle.item.polish";
    private static final String BACKUP_API = "mtop.idle.item.polish";
    private static final String MANUAL_TRIGGER = "MANUAL";
    private static final String SCHEDULED_TRIGGER = "SCHEDULED";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final int DEFAULT_RECORD_LIMIT = 30;
    private static final int MAX_RECORD_LIMIT = 100;

    private final XianyuAccountMapper accountMapper;
    private final XianyuGoodsInfoMapper goodsInfoMapper;
    private final XianyuItemPolishConfigMapper configMapper;
    private final XianyuItemPolishRecordMapper recordMapper;
    private final AccountService accountService;
    private final ItemService itemService;
    private final XianyuApiCallUtils xianyuApiCallUtils;
    private final Executor taskExecutor;
    private final Set<Long> runningAccountIds = ConcurrentHashMap.newKeySet();

    public ItemPolishServiceImpl(XianyuAccountMapper accountMapper,
                                 XianyuGoodsInfoMapper goodsInfoMapper,
                                 XianyuItemPolishConfigMapper configMapper,
                                 XianyuItemPolishRecordMapper recordMapper,
                                 AccountService accountService,
                                 ItemService itemService,
                                 XianyuApiCallUtils xianyuApiCallUtils,
                                 @Qualifier("taskExecutor") Executor taskExecutor) {
        this.accountMapper = accountMapper;
        this.goodsInfoMapper = goodsInfoMapper;
        this.configMapper = configMapper;
        this.recordMapper = recordMapper;
        this.accountService = accountService;
        this.itemService = itemService;
        this.xianyuApiCallUtils = xianyuApiCallUtils;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public Map<String, Object> getOverview(Long accountId, int recordLimit) {
        validateAccount(accountId);
        XianyuItemPolishConfig config = getOrCreateConfig(accountId);
        int limit = recordLimit <= 0 ? DEFAULT_RECORD_LIMIT : Math.min(recordLimit, MAX_RECORD_LIMIT);
        List<XianyuItemPolishRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<XianyuItemPolishRecord>()
                        .eq(XianyuItemPolishRecord::getXianyuAccountId, accountId)
                        .orderByDesc(XianyuItemPolishRecord::getCreateTime)
                        .last("LIMIT " + limit));
        long onSaleCount = goodsInfoMapper.selectCount(
                new LambdaQueryWrapper<XianyuGoodsInfo>()
                        .eq(XianyuGoodsInfo::getXianyuAccountId, accountId)
                        .eq(XianyuGoodsInfo::getStatus, 0));

        Map<String, Object> result = new HashMap<>();
        result.put("config", config);
        result.put("records", records);
        result.put("onSaleCount", onSaleCount);
        result.put("running", runningAccountIds.contains(accountId));
        return result;
    }

    @Override
    public XianyuItemPolishConfig saveConfig(Long accountId, Integer enabled, String scheduleTime) {
        validateAccount(accountId);
        XianyuItemPolishConfig config = getOrCreateConfig(accountId);
        if (enabled != null) {
            config.setEnabled(enabled == 0 ? 0 : 1);
        }
        if (StringUtils.hasText(scheduleTime)) {
            config.setScheduleTime(normalizeScheduleTime(scheduleTime));
        }
        configMapper.updateById(config);
        return configMapper.selectById(config.getId());
    }

    @Override
    public Map<String, Object> startManualRun(Long accountId) {
        ensureAccountActive(accountId);
        getOrCreateConfig(accountId);
        boolean started = startRun(accountId, MANUAL_TRIGGER, false);
        Map<String, Object> result = new HashMap<>();
        result.put("started", started);
        result.put("message", started ? "一键擦亮任务已开始，将先同步在售商品再依次擦亮" : "该账号正在同步或擦亮，请等待当前任务完成");
        return result;
    }

    @Override
    public void runDueSchedules() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now().withSecond(0).withNano(0);
        List<XianyuItemPolishConfig> configs = configMapper.selectList(
                new LambdaQueryWrapper<XianyuItemPolishConfig>()
                        .eq(XianyuItemPolishConfig::getEnabled, 1));

        for (XianyuItemPolishConfig config : configs) {
            try {
                Long accountId = config.getXianyuAccountId();
                if (accountId == null || today.equals(config.getLastScheduledDate())) {
                    continue;
                }
                XianyuAccount account = accountMapper.selectById(accountId);
                if (account == null || !Integer.valueOf(1).equals(account.getStatus())) {
                    continue;
                }
                LocalTime scheduledTime = LocalTime.parse(normalizeScheduleTime(config.getScheduleTime()), TIME_FORMATTER);
                if (now.isBefore(scheduledTime)) {
                    continue;
                }
                if (startRun(accountId, SCHEDULED_TRIGGER, true)) {
                    log.info("【自动擦亮】账号 {} 已进入每日定时擦亮队列，计划时间 {}", accountId, scheduledTime);
                }
            } catch (Exception e) {
                log.warn("【自动擦亮】读取定时配置失败，configId={}: {}", config.getId(), e.getMessage());
            }
        }
    }

    private boolean startRun(Long accountId, String triggerType, boolean scheduled) {
        if (!runningAccountIds.add(accountId)) {
            return false;
        }
        try {
            if (scheduled) {
                XianyuItemPolishConfig config = getOrCreateConfig(accountId);
                config.setLastScheduledDate(LocalDate.now());
                configMapper.updateById(config);
            }
            taskExecutor.execute(() -> executeRun(accountId, triggerType));
            return true;
        } catch (Exception e) {
            runningAccountIds.remove(accountId);
            throw e;
        }
    }

    private void executeRun(Long accountId, String triggerType) {
        int total = 0;
        int success = 0;
        int failed = 0;
        String summary = null;
        try {
            XianyuAccount account = validateAccount(accountId);
            if (!Integer.valueOf(1).equals(account.getStatus())) {
                throw new IllegalStateException("账号当前不可用，已跳过自动擦亮");
            }
            String cookie = accountService.getCookieByAccountId(accountId);
            if (!StringUtils.hasText(cookie)) {
                throw new IllegalStateException("账号没有可用 Cookie，请先在连接管理中更新凭证");
            }

            int syncedOnSaleCount = synchronizeOnSaleItems(accountId);
            if (syncedOnSaleCount == 0) {
                summary = "商品同步完成：当前没有在售商品，无需擦亮";
                log.info("【一键擦亮】账号 {} 商品同步完成，当前没有在售商品", accountId);
                return;
            }

            List<XianyuGoodsInfo> items = goodsInfoMapper.selectList(
                    new LambdaQueryWrapper<XianyuGoodsInfo>()
                            .eq(XianyuGoodsInfo::getXianyuAccountId, accountId)
                            .eq(XianyuGoodsInfo::getStatus, 0)
                            .orderByAsc(XianyuGoodsInfo::getId));
            total = items.size();
            if (total == 0) {
                summary = "商品同步完成：发现 " + syncedOnSaleCount + " 件在售商品，但本地没有可擦亮商品";
                log.warn("【一键擦亮】账号 {} 同步到 {} 件在售商品，但本地没有可擦亮商品", accountId, syncedOnSaleCount);
                return;
            }
            log.info("【一键擦亮】账号 {} 商品同步完成，开始执行，触发方式={}, 在售商品={}", accountId, triggerType, total);

            for (int index = 0; index < items.size(); index++) {
                XianyuGoodsInfo item = items.get(index);
                String error = polishItem(accountId, item.getXyGoodId());
                boolean itemSuccess = error == null;
                if (itemSuccess) {
                    success++;
                } else {
                    failed++;
                }
                saveRecord(accountId, item, triggerType, itemSuccess, itemSuccess ? "擦亮成功" : error);
                log.info("【自动擦亮】账号 {} 进度 {}/{}, 商品 {}, {}", accountId, index + 1, total,
                        item.getXyGoodId(), itemSuccess ? "成功" : "失败");
                if (index < items.size() - 1) {
                    sleepBetweenItems();
                }
            }
            summary = "同步后执行完成：共 " + total + " 件，成功 " + success + " 件，失败 " + failed + " 件";
        } catch (Exception e) {
            failed = Math.max(1, failed);
            summary = "执行异常：" + messageOf(e);
            log.error("【自动擦亮】账号 {} 执行异常", accountId, e);
        } finally {
            updateRunSummary(accountId, total, success, failed, summary == null ? "任务结束" : summary);
            runningAccountIds.remove(accountId);
        }
    }

    /**
     * 每次擦亮前从闲鱼拉取账号的全部在售商品，避免新上架或下架的商品继续使用旧的本地列表。
     * 同步异常时直接中止本次任务，宁可不擦亮，也不对过期列表执行操作。
     */
    private int synchronizeOnSaleItems(Long accountId) {
        AllItemsReqDTO request = new AllItemsReqDTO();
        request.setXianyuAccountId(accountId);
        ResultObject<RefreshItemsRespDTO> result = itemService.refreshItems(request);
        if (result == null || result.getCode() == null || result.getCode() != 200) {
            String message = result == null ? "未收到同步结果" : messageOf(result.getMsg());
            throw new IllegalStateException("商品同步失败，已取消本次擦亮：" + message);
        }
        RefreshItemsRespDTO data = result.getData();
        if (data == null) {
            throw new IllegalStateException("商品同步失败，已取消本次擦亮：未返回商品数据");
        }

        int totalCount = data.getTotalCount() == null ? 0 : Math.max(data.getTotalCount(), 0);
        if (!Boolean.TRUE.equals(data.getSuccess()) && totalCount > 0) {
            throw new IllegalStateException("商品同步失败，已取消本次擦亮：" + messageOf(data.getMessage()));
        }
        return totalCount;
    }

    /** 返回 null 表示成功，其余为失败原因。 */
    private String polishItem(Long accountId, String xyGoodsId) {
        if (!StringUtils.hasText(xyGoodsId)) {
            return "商品 ID 为空";
        }
        Map<String, Object> data = Map.of("itemId", xyGoodsId);
        Map<String, String> query = Map.of(
                "spm_cnt", "a21ybx.im.0.0",
                "spm_pre", "a21ybx.collection.menu.1.272b5141NafCNK");
        String cookie = accountService.getCookieByAccountId(accountId);
        XianyuApiCallUtils.ApiCallResult primary = xianyuApiCallUtils.callApiWithRetry(
                accountId, PRIMARY_API, data, cookie, null, query);
        if (primary.isSuccess()) {
            return null;
        }

        String refreshedCookie = accountService.getCookieByAccountId(accountId);
        XianyuApiCallUtils.ApiCallResult backup = xianyuApiCallUtils.callApiWithRetry(
                accountId, BACKUP_API, data, refreshedCookie, null, query);
        if (backup.isSuccess()) {
            return null;
        }
        String primaryMessage = messageOf(primary.getErrorMessage());
        String backupMessage = messageOf(backup.getErrorMessage());
        return "主接口：" + primaryMessage + "；备用接口：" + backupMessage;
    }

    private void saveRecord(Long accountId, XianyuGoodsInfo item, String triggerType,
                            boolean success, String message) {
        XianyuItemPolishRecord record = new XianyuItemPolishRecord();
        record.setXianyuAccountId(accountId);
        record.setXyGoodsId(item.getXyGoodId());
        record.setGoodsTitle(item.getTitle());
        record.setTriggerType(triggerType);
        record.setSuccess(success ? 1 : 0);
        record.setMessage(trimMessage(message));
        recordMapper.insert(record);
    }

    private void updateRunSummary(Long accountId, int total, int success, int failed, String message) {
        try {
            XianyuItemPolishConfig config = getOrCreateConfig(accountId);
            config.setLastRunAt(LocalDateTime.now());
            config.setLastRunTotal(total);
            config.setLastRunSuccess(success);
            config.setLastRunFailed(failed);
            config.setLastRunMessage(trimMessage(message));
            configMapper.updateById(config);
        } catch (Exception e) {
            log.error("【自动擦亮】保存账号 {} 的执行汇总失败", accountId, e);
        }
    }

    private XianyuItemPolishConfig getOrCreateConfig(Long accountId) {
        XianyuItemPolishConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<XianyuItemPolishConfig>()
                        .eq(XianyuItemPolishConfig::getXianyuAccountId, accountId));
        if (config != null) {
            return config;
        }
        XianyuItemPolishConfig created = new XianyuItemPolishConfig();
        created.setXianyuAccountId(accountId);
        created.setEnabled(0);
        created.setScheduleTime("09:00");
        created.setLastRunTotal(0);
        created.setLastRunSuccess(0);
        created.setLastRunFailed(0);
        configMapper.insert(created);
        return created;
    }

    private XianyuAccount validateAccount(Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("账号不能为空");
        }
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("账号不存在");
        }
        return account;
    }

    private XianyuAccount ensureAccountActive(Long accountId) {
        XianyuAccount account = validateAccount(accountId);
        if (!Integer.valueOf(1).equals(account.getStatus())) {
            throw new IllegalStateException("账号已禁用或不可用，请先在账号管理中启用账号");
        }
        return account;
    }

    private String normalizeScheduleTime(String value) {
        try {
            return LocalTime.parse(value == null ? "" : value.trim(), TIME_FORMATTER).format(TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("执行时间格式应为 HH:mm，例如 09:00");
        }
    }

    private void sleepBetweenItems() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(1000, 3001));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("任务被中断");
        }
    }

    private String messageOf(String message) {
        return StringUtils.hasText(message) ? trimMessage(message) : "未知错误";
    }

    private String messageOf(Exception exception) {
        return messageOf(exception.getMessage());
    }

    private String trimMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
