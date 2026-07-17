package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xianyusmart.controller.dto.DashboardAccountHealthDTO;
import com.xianyusmart.controller.dto.DashboardOverviewRespDTO;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import com.xianyusmart.controller.dto.DashboardStatsRespDTO;
import com.xianyusmart.controller.dto.DashboardUnreadCountDTO;
import com.xianyusmart.controller.dto.ExceptionCenterQueryReqDTO;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuCookie;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuChatMessageMapper;
import com.xianyusmart.mapper.XianyuCookieMapper;
import com.xianyusmart.mapper.XianyuOperationLogMapper;
import com.xianyusmart.service.ExceptionCenterService;
import com.xianyusmart.service.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 首页仪表板控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private XianyuGoodsOrderMapper orderMapper;

    @Autowired
    private XianyuAccountMapper accountMapper;

    @Autowired
    private XianyuCookieMapper cookieMapper;

    @Autowired
    private XianyuChatMessageMapper chatMessageMapper;

    @Autowired
    private XianyuOperationLogMapper operationLogMapper;

    @Autowired
    private WebSocketService webSocketService;

    @Autowired
    private ExceptionCenterService exceptionCenterService;

    /**
     * 获取首页统计数据
     */
    @PostMapping("/stats")
    public ResultObject<DashboardStatsRespDTO> getDashboardStats() {
        try {
            return ResultObject.success(orderMapper.selectDashboardStats());
        } catch (Exception e) {
            log.error("获取首页统计数据失败", e);
            return ResultObject.failed("获取首页统计数据失败");
        }
    }

    /**
     * 运营首页总览。连接状态读取运行时 WebSocket，因此服务器重启、掉线能立即显示出来。
     */
    @PostMapping("/overview")
    public ResultObject<DashboardOverviewRespDTO> getDashboardOverview() {
        try {
            DashboardStatsRespDTO stats = orderMapper.selectDashboardStats();
            if (stats == null) {
                stats = new DashboardStatsRespDTO();
            }

            Map<Long, Integer> unreadByAccount = loadUnreadCounts();
            stats.setUnreadMessageCount(unreadByAccount.values().stream().mapToInt(Integer::intValue).sum());

            DashboardOverviewRespDTO response = new DashboardOverviewRespDTO();
            response.setStats(stats);
            response.setAccountHealth(buildAccountHealth(unreadByAccount));
            response.setAccountIssueCount((int) response.getAccountHealth().stream()
                    .filter(item -> Boolean.TRUE.equals(item.getNeedsAttention()))
                    .count());
            response.setAutomationExceptionCount(loadAutomationExceptionCount());
            response.setTrends(orderMapper.selectRecentDeliveryTrend());
            response.setActivities(operationLogMapper.findRecentActivities(10));
            return ResultObject.success(response);
        } catch (Exception e) {
            log.error("获取运营首页总览失败", e);
            return ResultObject.failed("获取运营首页总览失败");
        }
    }

    private Map<Long, Integer> loadUnreadCounts() {
        try {
            Map<Long, Integer> result = new HashMap<>();
            for (DashboardUnreadCountDTO item : chatMessageMapper.countUnreadMessagesByAccount()) {
                if (item.getAccountId() != null) {
                    result.put(item.getAccountId(), item.getUnreadCount() == null ? 0 : item.getUnreadCount());
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("读取仪表盘未读消息数失败，将按 0 展示", e);
            return Map.of();
        }
    }

    private List<DashboardAccountHealthDTO> buildAccountHealth(Map<Long, Integer> unreadByAccount) {
        List<XianyuAccount> accounts = accountMapper.selectList(Wrappers.lambdaQuery(XianyuAccount.class));
        Map<Long, XianyuCookie> cookies = new HashMap<>();
        for (XianyuCookie cookie : cookieMapper.selectList(Wrappers.lambdaQuery(XianyuCookie.class))) {
            if (cookie.getXianyuAccountId() != null) {
                XianyuCookie current = cookies.get(cookie.getXianyuAccountId());
                if (current == null || Objects.compare(cookie.getId(), current.getId(), Comparator.nullsFirst(Long::compareTo)) > 0) {
                    cookies.put(cookie.getXianyuAccountId(), cookie);
                }
            }
        }

        return accounts.stream().map(account -> {
            DashboardAccountHealthDTO item = new DashboardAccountHealthDTO();
            item.setAccountId(account.getId());
            item.setAccountName(hasText(account.getAccountNote()) ? account.getAccountNote() : account.getUnb());
            item.setAccountStatus(account.getStatus());
            item.setAccountStatusText(accountStatusText(account.getStatus()));
            XianyuCookie cookie = cookies.get(account.getId());
            item.setCookieStatus(cookie == null ? null : cookie.getCookieStatus());
            item.setCookieStatusText(cookieStatusText(cookie == null ? null : cookie.getCookieStatus()));
            boolean connected = Integer.valueOf(1).equals(account.getStatus()) && webSocketService.isConnected(account.getId());
            item.setWebsocketConnected(connected);
            item.setAutomationRiskPaused(Integer.valueOf(1).equals(account.getAutomationRiskPaused()));
            item.setAutomationRiskPauseReason(account.getAutomationRiskPauseReason());
            item.setUnreadMessageCount(unreadByAccount.getOrDefault(account.getId(), 0));
            item.setAutoRateEnabled(Integer.valueOf(1).equals(account.getAutoRateEnabled()));
            item.setAutoAskFlowerEnabled(Integer.valueOf(1).equals(account.getAutoAskFlower()));
            item.setNeedsAttention(needsAttention(account, cookie, connected));
            item.setHealthText(resolveHealthText(account, cookie, connected));
            return item;
        }).sorted(Comparator.comparing(DashboardAccountHealthDTO::getNeedsAttention, Comparator.reverseOrder())
                .thenComparing(DashboardAccountHealthDTO::getAccountId, Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    private int loadAutomationExceptionCount() {
        try {
            Object summary = exceptionCenterService.query(new ExceptionCenterQueryReqDTO()).get("summary");
            if (summary instanceof Map<?, ?> map && map.get("total") instanceof Number count) {
                return count.intValue();
            }
        } catch (Exception e) {
            log.warn("读取仪表盘自动化异常数失败，将按 0 展示", e);
        }
        return 0;
    }

    private boolean needsAttention(XianyuAccount account, XianyuCookie cookie, boolean connected) {
        if (Integer.valueOf(1).equals(account.getAutomationRiskPaused())) {
            return true;
        }
        if (Integer.valueOf(0).equals(account.getStatus())) {
            return false;
        }
        if (!Integer.valueOf(1).equals(account.getStatus())) {
            return true;
        }
        return (cookie != null && (Integer.valueOf(2).equals(cookie.getCookieStatus()) || Integer.valueOf(3).equals(cookie.getCookieStatus())))
                || !connected;
    }

    private String resolveHealthText(XianyuAccount account, XianyuCookie cookie, boolean connected) {
        if (Integer.valueOf(1).equals(account.getAutomationRiskPaused())) {
            return "自动化已暂停";
        }
        if (Integer.valueOf(0).equals(account.getStatus())) {
            return "账号已禁用";
        }
        if (!Integer.valueOf(1).equals(account.getStatus())) {
            return "需要重新登录";
        }
        if (cookie != null && Integer.valueOf(2).equals(cookie.getCookieStatus())) {
            return "Cookie 已过期";
        }
        if (cookie != null && Integer.valueOf(3).equals(cookie.getCookieStatus())) {
            return "Cookie 已失效";
        }
        return connected ? "运行正常" : "实时连接未建立";
    }

    private String accountStatusText(Integer status) {
        if (Integer.valueOf(1).equals(status)) {
            return "已启用";
        }
        if (Integer.valueOf(0).equals(status)) {
            return "已禁用";
        }
        return "需要处理";
    }

    private String cookieStatusText(Integer status) {
        if (Integer.valueOf(1).equals(status)) {
            return "Cookie 有效";
        }
        if (Integer.valueOf(2).equals(status)) {
            return "Cookie 已过期";
        }
        if (Integer.valueOf(3).equals(status)) {
            return "Cookie 已失效";
        }
        return "Cookie 未检测";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
