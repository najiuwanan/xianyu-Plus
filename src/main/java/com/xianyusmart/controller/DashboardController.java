package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.DashboardOverviewRespDTO;
import com.xianyusmart.controller.dto.DashboardStatsRespDTO;
import com.xianyusmart.controller.dto.DashboardTrendPointDTO;
import com.xianyusmart.controller.dto.DashboardUnreadCountDTO;
import com.xianyusmart.controller.dto.ExceptionCenterQueryReqDTO;
import com.xianyusmart.mapper.XianyuChatMessageMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import com.xianyusmart.service.ExceptionCenterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Provides only the data currently rendered by the dashboard. */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final XianyuGoodsOrderMapper orderMapper;
    private final XianyuChatMessageMapper chatMessageMapper;
    private final ExceptionCenterService exceptionCenterService;

    public DashboardController(XianyuGoodsOrderMapper orderMapper,
                               XianyuChatMessageMapper chatMessageMapper,
                               ExceptionCenterService exceptionCenterService) {
        this.orderMapper = orderMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.exceptionCenterService = exceptionCenterService;
    }

    @PostMapping("/overview")
    public ResultObject<DashboardOverviewRespDTO> getDashboardOverview() {
        try {
            DashboardStatsRespDTO stats = orderMapper.selectDashboardStats();
            if (stats == null) {
                stats = new DashboardStatsRespDTO();
            }
            stats.setUnreadMessageCount(loadUnreadMessageCount());

            DashboardOverviewRespDTO response = new DashboardOverviewRespDTO();
            response.setStats(stats);
            response.setAutomationExceptionCount(loadAutomationExceptionCount());
            response.setTrends(loadRecentDeliveryTrend());
            return ResultObject.success(response);
        } catch (Exception exception) {
            log.error("获取运营首页总览失败", exception);
            return ResultObject.failed("获取运营首页总览失败");
        }
    }

    private int loadUnreadMessageCount() {
        try {
            List<DashboardUnreadCountDTO> unreadCounts = chatMessageMapper.countUnreadMessagesByAccount();
            if (unreadCounts == null) {
                return 0;
            }
            return unreadCounts.stream()
                    .map(DashboardUnreadCountDTO::getUnreadCount)
                    .filter(java.util.Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();
        } catch (Exception exception) {
            log.warn("读取仪表盘未读消息数失败，将按 0 展示", exception);
            return 0;
        }
    }

    private List<DashboardTrendPointDTO> loadRecentDeliveryTrend() {
        try {
            return orderMapper.selectRecentDeliveryTrend();
        } catch (Exception exception) {
            log.warn("读取仪表盘近三十日交付趋势失败，将暂不展示趋势", exception);
            return List.of();
        }
    }

    private int loadAutomationExceptionCount() {
        try {
            Object summary = exceptionCenterService.query(new ExceptionCenterQueryReqDTO()).get("summary");
            if (summary instanceof java.util.Map<?, ?> map && map.get("total") instanceof Number count) {
                return count.intValue();
            }
        } catch (Exception exception) {
            log.warn("读取仪表盘自动化异常数失败，将按 0 展示", exception);
        }
        return 0;
    }
}
