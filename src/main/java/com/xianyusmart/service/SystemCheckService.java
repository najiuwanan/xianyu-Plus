package com.xianyusmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xianyusmart.config.rag.DynamicAIChatClientManager;
import com.xianyusmart.controller.dto.DashboardStatsRespDTO;
import com.xianyusmart.controller.dto.ExceptionCenterQueryReqDTO;
import com.xianyusmart.entity.SysNotificationChannel;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.mapper.SysNotificationChannelMapper;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import com.xianyusmart.mapper.XianyuKamiConfigMapper;
import com.xianyusmart.mapper.XianyuKamiItemMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 提供给前端的轻量系统自检；只读查询，不会自动改变账号或任务状态。 */
@Service
public class SystemCheckService {

    private final XianyuAccountMapper accountMapper;
    private final WebSocketService webSocketService;
    private final DynamicAIChatClientManager aiChatClientManager;
    private final SysNotificationChannelMapper notificationChannelMapper;
    private final XianyuKamiConfigMapper kamiConfigMapper;
    private final XianyuKamiItemMapper kamiItemMapper;
    private final XianyuGoodsOrderMapper orderMapper;
    private final ExceptionCenterService exceptionCenterService;

    public SystemCheckService(XianyuAccountMapper accountMapper,
                              WebSocketService webSocketService,
                              DynamicAIChatClientManager aiChatClientManager,
                              SysNotificationChannelMapper notificationChannelMapper,
                              XianyuKamiConfigMapper kamiConfigMapper,
                              XianyuKamiItemMapper kamiItemMapper,
                              XianyuGoodsOrderMapper orderMapper,
                              ExceptionCenterService exceptionCenterService) {
        this.accountMapper = accountMapper;
        this.webSocketService = webSocketService;
        this.aiChatClientManager = aiChatClientManager;
        this.notificationChannelMapper = notificationChannelMapper;
        this.kamiConfigMapper = kamiConfigMapper;
        this.kamiItemMapper = kamiItemMapper;
        this.orderMapper = orderMapper;
        this.exceptionCenterService = exceptionCenterService;
    }

    public Map<String, Object> overview() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(checkAccounts());
        items.add(checkAi());
        items.add(checkNotifications());
        items.add(checkKami());
        items.add(checkAutomation());

        long pass = items.stream().filter(item -> "PASS".equals(item.get("status"))).count();
        long warn = items.stream().filter(item -> "WARN".equals(item.get("status"))).count();
        long fail = items.stream().filter(item -> "FAIL".equals(item.get("status"))).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", LocalDateTime.now());
        result.put("passCount", pass);
        result.put("warnCount", warn);
        result.put("failCount", fail);
        result.put("items", items);
        return result;
    }

    private Map<String, Object> checkAccounts() {
        List<XianyuAccount> accounts = accountMapper.selectList(null);
        List<XianyuAccount> active = accounts.stream().filter(account -> Integer.valueOf(1).equals(account.getStatus())).toList();
        if (active.isEmpty()) {
            return item("accounts", "账号连接", "WARN", "没有已启用账号", "请先在账号管理添加并启用账号。", "/accounts");
        }
        long connected = active.stream().filter(account -> webSocketService.isConnected(account.getId())).count();
        if (connected == active.size()) {
            return item("accounts", "账号连接", "PASS", "已连接 " + connected + " / " + active.size() + " 个启用账号", "消息、自动回复和自动发货可正常接收实时事件。", "/connection");
        }
        return item("accounts", "账号连接", "WARN", "已连接 " + connected + " / " + active.size() + " 个启用账号", "未连接的账号不会接收实时消息；可到连接管理重新连接。", "/connection");
    }

    private Map<String, Object> checkAi() {
        DynamicAIChatClientManager.AIStatusInfo ai = aiChatClientManager.getStatusInfo();
        if (ai.isAvailable()) {
            return item("ai", "AI 回复", "PASS", "AI 服务可用", "当前模型：" + safe(ai.getModel()), "/settings");
        }
        return item("ai", "AI 回复", "WARN", "AI 服务未就绪", safe(ai.getMessage()), "/settings");
    }

    private Map<String, Object> checkNotifications() {
        long enabled = notificationChannelMapper.selectCount(new LambdaQueryWrapper<SysNotificationChannel>()
                .eq(SysNotificationChannel::getStatus, 1));
        if (enabled == 0) {
            return item("notifications", "通知渠道", "WARN", "未启用通知渠道", "异常中心仍会记录失败；配置渠道后可收到提醒。", "/notifications");
        }
        return item("notifications", "通知渠道", "PASS", "已启用 " + enabled + " 个通知渠道", "可在通知渠道内按事件选择接收范围。", "/notifications");
    }

    private Map<String, Object> checkKami() {
        long configs = kamiConfigMapper.selectCount(null);
        if (configs == 0) {
            return item("kami", "卡券库存", "WARN", "未配置卡券", "若商品使用固定文本发货，此项无需处理。", "/kami-config");
        }
        long available = kamiItemMapper.selectCount(new LambdaQueryWrapper<com.xianyusmart.entity.XianyuKamiItem>()
                .eq(com.xianyusmart.entity.XianyuKamiItem::getStatus, 0));
        if (available == 0) {
            return item("kami", "卡券库存", "WARN", "卡券可用库存为 0", "请在卡券管理补充库存，避免自动发货失败。", "/kami-config");
        }
        return item("kami", "卡券库存", "PASS", "当前可用卡券 " + available + " 条", "已配置 " + configs + " 个卡券库。", "/kami-config");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> checkAutomation() {
        Map<String, Object> response = exceptionCenterService.query(new ExceptionCenterQueryReqDTO());
        Map<String, Long> summary = (Map<String, Long>) response.get("summary");
        long total = summary == null ? 0L : summary.getOrDefault("total", 0L);
        DashboardStatsRespDTO dashboard = orderMapper.selectDashboardStats();
        long reviewRequired = dashboard == null || dashboard.getReviewRequiredCount() == null ? 0L : dashboard.getReviewRequiredCount();
        if (total == 0 && reviewRequired == 0) {
            return item("automation", "自动化任务", "PASS", "暂无待处理异常", "自动发货、评价、小红花和擦亮失败会在异常中心集中显示。", "/exception-center");
        }
        return item("automation", "自动化任务", "WARN", "有 " + total + " 条异常待处理", reviewRequired > 0
                ? "其中 " + reviewRequired + " 笔发货结果需要人工核对。"
                : "可到异常中心查看失败原因并重试。", "/exception-center");
    }

    private Map<String, Object> item(String id, String title, String status, String summary, String detail, String path) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("title", title);
        item.put("status", status);
        item.put("summary", summary);
        item.put("detail", detail);
        item.put("path", path);
        return item;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "未配置" : value;
    }
}
