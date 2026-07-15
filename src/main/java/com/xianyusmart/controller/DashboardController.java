package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import com.xianyusmart.controller.dto.DashboardStatsRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 首页仪表板控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private XianyuGoodsOrderMapper orderMapper;

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
}
