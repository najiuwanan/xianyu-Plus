package com.xianyusmart.service;

import com.xianyusmart.controller.dto.AutomationScheduleConfigDTO;
import com.xianyusmart.controller.dto.AutomationScheduleSaveReqDTO;
import com.xianyusmart.service.bo.SaveSettingReqBO;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps the user-facing business automation schedules in one place.
 *
 * <p>System maintenance jobs such as Cookie refresh and log cleanup deliberately do not use
 * this service. Their intervals are safety defaults and must not be accidentally made high-frequency.</p>
 */
@Service
public class AutomationScheduleService {

    public static final String AUTO_RATE = "AUTO_RATE";
    public static final String RED_FLOWER = "RED_FLOWER";
    public static final String ORDER_DISCOVERY = "ORDER_DISCOVERY";
    public static final String DELIVERY_DISPATCH = "DELIVERY_DISPATCH";
    public static final String ITEM_POLISH = "ITEM_POLISH";
    public static final String DELAYED_REPLY = "DELAYED_REPLY";

    private static final String SETTING_PREFIX = "automation.schedule.";

    private final SysSettingService sysSettingService;
    private final Map<String, TaskDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, Integer> intervalCache = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lastRunAt = new ConcurrentHashMap<>();

    public AutomationScheduleService(SysSettingService sysSettingService) {
        this.sysSettingService = sysSettingService;
        register(AUTO_RATE, "自动评价扫描", "仅检查近 30 天本地订单，再核验闲鱼待评价状态。", 120, 60);
        register(RED_FLOWER, "自动求小红花", "处理已确认发货、尚未成功请求小红花的订单。", 300, 120);
        register(ORDER_DISCOVERY, "待发货订单同步", "从闲鱼同步待发货订单到本地发货队列。", 25, 10);
        register(DELIVERY_DISPATCH, "自动发货队列", "检查并执行已经进入本地队列的发货任务。", 1, 1);
        register(ITEM_POLISH, "自动擦亮计划检查", "到设定时间后同步在售商品并执行自动擦亮。", 30, 15);
        register(DELAYED_REPLY, "延迟回复队列", "恢复服务重启前尚未执行的延迟回复任务。", 1, 1);
    }

    @PostConstruct
    public void loadSavedIntervals() {
        definitions.forEach((taskKey, definition) -> intervalCache.put(taskKey, readInterval(definition)));
    }

    public boolean tryAcquire(String taskKey) {
        TaskDefinition definition = requireDefinition(taskKey);
        long now = System.currentTimeMillis();
        long intervalMs = intervalSeconds(definition.taskKey()) * 1000L;
        AtomicLong previousRun = lastRunAt.computeIfAbsent(definition.taskKey(), ignored -> new AtomicLong(0));

        while (true) {
            long previous = previousRun.get();
            if (now - previous < intervalMs) {
                return false;
            }
            if (previousRun.compareAndSet(previous, now)) {
                return true;
            }
        }
    }

    public List<AutomationScheduleConfigDTO> list() {
        List<AutomationScheduleConfigDTO> result = new ArrayList<>();
        definitions.values().forEach(definition -> result.add(toDto(definition)));
        return result;
    }

    public List<AutomationScheduleConfigDTO> save(AutomationScheduleSaveReqDTO request) {
        if (request == null || request.getTasks() == null || request.getTasks().isEmpty()) {
            throw new IllegalArgumentException("请至少保留一项定时任务设置");
        }

        Map<String, Integer> updates = new LinkedHashMap<>();
        for (AutomationScheduleSaveReqDTO.TaskInterval task : request.getTasks()) {
            if (task == null || !StringUtils.hasText(task.getTaskKey())) {
                throw new IllegalArgumentException("任务标识不能为空");
            }
            TaskDefinition definition = requireDefinition(task.getTaskKey());
            int seconds = task.getIntervalSeconds() == null ? 0 : task.getIntervalSeconds();
            if (seconds < definition.minIntervalSeconds()) {
                throw new IllegalArgumentException(definition.name() + "最短只能设置为 "
                        + definition.minIntervalSeconds() + " 秒");
            }
            updates.put(definition.taskKey(), seconds);
        }

        for (Map.Entry<String, Integer> update : updates.entrySet()) {
            TaskDefinition definition = definitions.get(update.getKey());
            SaveSettingReqBO setting = new SaveSettingReqBO();
            setting.setSettingKey(settingKey(definition.taskKey()));
            setting.setSettingValue(String.valueOf(update.getValue()));
            setting.setSettingDesc(definition.name() + "执行间隔（秒）");
            sysSettingService.saveSetting(setting);
            intervalCache.put(definition.taskKey(), update.getValue());
            lastRunAt.remove(definition.taskKey());
        }
        return list();
    }

    private void register(String taskKey, String name, String description, int defaultSeconds, int minSeconds) {
        definitions.put(taskKey, new TaskDefinition(taskKey, name, description, defaultSeconds, minSeconds));
    }

    private int intervalSeconds(String taskKey) {
        TaskDefinition definition = requireDefinition(taskKey);
        return intervalCache.computeIfAbsent(taskKey, ignored -> readInterval(definition));
    }

    private int readInterval(TaskDefinition definition) {
        String savedValue = sysSettingService.getSettingValue(settingKey(definition.taskKey()));
        if (!StringUtils.hasText(savedValue)) {
            return definition.defaultIntervalSeconds();
        }
        try {
            return Math.max(definition.minIntervalSeconds(), Integer.parseInt(savedValue.trim()));
        } catch (NumberFormatException ignored) {
            return definition.defaultIntervalSeconds();
        }
    }

    private AutomationScheduleConfigDTO toDto(TaskDefinition definition) {
        AutomationScheduleConfigDTO dto = new AutomationScheduleConfigDTO();
        dto.setTaskKey(definition.taskKey());
        dto.setName(definition.name());
        dto.setDescription(definition.description());
        dto.setIntervalSeconds(intervalSeconds(definition.taskKey()));
        dto.setDefaultIntervalSeconds(definition.defaultIntervalSeconds());
        dto.setMinIntervalSeconds(definition.minIntervalSeconds());
        return dto;
    }

    private TaskDefinition requireDefinition(String taskKey) {
        TaskDefinition definition = definitions.get(taskKey);
        if (definition == null) {
            throw new IllegalArgumentException("不支持的定时任务：" + taskKey);
        }
        return definition;
    }

    private String settingKey(String taskKey) {
        return SETTING_PREFIX + taskKey.toLowerCase() + ".seconds";
    }

    private record TaskDefinition(String taskKey, String name, String description,
                                  int defaultIntervalSeconds, int minIntervalSeconds) {
    }
}
