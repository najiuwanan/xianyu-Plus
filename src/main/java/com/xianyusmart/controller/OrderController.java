package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.ConfirmShipmentReqDTO;
import com.xianyusmart.controller.dto.OrderDetailReqDTO;
import com.xianyusmart.controller.dto.OrderListReqDTO;
import com.xianyusmart.controller.dto.OrderListRespDTO;
import com.xianyusmart.controller.dto.OrderDTO;
import com.xianyusmart.controller.dto.OrderTimelineRespDTO;
import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import com.xianyusmart.service.OrderService;
import com.xianyusmart.service.RedFlowerService;
import com.xianyusmart.service.OrderTimelineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * 订单控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private RedFlowerService redFlowerService;

    @Autowired
    private XianyuGoodsOrderMapper orderMapper;

    @Autowired
    private com.xianyusmart.service.PendingOrderPollService pendingOrderPollService;

    @Autowired
    private OrderTimelineService orderTimelineService;

    /**
     * 查询订单列表（第三方调用）
     */
    @PostMapping("/list")
    public ResultObject<OrderListRespDTO> listOrders(@RequestBody OrderListReqDTO reqDTO) {
        try {
            int pageSize = reqDTO.getPageSize() != null ? reqDTO.getPageSize() : 20;
            int pageNum = reqDTO.getPageNum() != null ? reqDTO.getPageNum() : 1;
            if (pageNum < 1) pageNum = 1;

            int offset = (pageNum - 1) * pageSize;
            long total = orderMapper.countByCondition(
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), reqDTO.getOrderStatus(), reqDTO.getKeyword());

            List<XianyuGoodsOrder> orders = orderMapper.selectByConditionWithPage(
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), reqDTO.getOrderStatus(), reqDTO.getKeyword(),
                    pageSize, offset);

            OrderListRespDTO respDTO = new OrderListRespDTO();
            respDTO.setTotal(total);
            respDTO.setPageNum(pageNum);
            respDTO.setPageSize(pageSize);

            List<OrderDTO> orderDTOs = new ArrayList<>();
            for (XianyuGoodsOrder order : orders) {
                OrderDTO dto = new OrderDTO();
                dto.setId(order.getId());
                dto.setXianyuAccountId(order.getXianyuAccountId());
                dto.setXyGoodsId(order.getXyGoodsId());
                dto.setOrderId(order.getOrderId());
                dto.setBuyerUserName(order.getBuyerUserName());
                dto.setSid(order.getSid());
                dto.setContent(order.getContent());
                dto.setState(order.getState());
                dto.setFailReason(order.getFailReason());
                dto.setConfirmState(order.getConfirmState());
                dto.setGoodsTitle(order.getGoodsTitle());
                dto.setSkuName(order.getSkuName());
                dto.setOrderCreateTime(order.getOrderCreateTime());
                dto.setPaySuccessTime(order.getPaySuccessTime());
                dto.setConsignTime(order.getConsignTime());
                dto.setTotalPrice(order.getTotalPrice());
                dto.setBuyNum(order.getBuyNum());
                dto.setDeliveryStatus(order.getDeliveryStatus());
                dto.setDeliveryChannel(order.getDeliveryChannel());
                dto.setLastErrorMessage(order.getLastErrorMessage());
                dto.setTradeStatus(order.getTradeStatus());
                dto.setTradeStatusText(order.getTradeStatusText());
                dto.setRateEnabled(order.getRateEnabled());
                dto.setRateStatus(order.getRateStatus());
                dto.setRateError(order.getRateError());
                dto.setRedFlowerEnabled(order.getRedFlowerEnabled());
                dto.setRedFlowerStatus(order.getRedFlowerStatus());
                dto.setRedFlowerError(order.getRedFlowerError());
                dto.setCreateTime(order.getCreateTime());
                orderDTOs.add(dto);
            }
            respDTO.setRecords(orderDTOs);

            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("查询订单列表失败", e);
            return ResultObject.failed("查询订单列表失败: " + e.getMessage());
        }
    }

    /**
     * 确认发货
     */
    @PostMapping("/confirmShipment")
    public ResultObject<String> confirmShipment(@RequestBody ConfirmShipmentReqDTO reqDTO) {
        try {
            log.info("确认发货请求: xianyuAccountId={}, orderId={}", 
                    reqDTO.getXianyuAccountId(), reqDTO.getOrderId());

            if (reqDTO.getXianyuAccountId() == null) {
                return ResultObject.failed("账号ID不能为空");
            }
            if (reqDTO.getOrderId() == null || reqDTO.getOrderId().isEmpty()) {
                return ResultObject.failed("订单ID不能为空");
            }

            String result = orderService.confirmShipment(
                    reqDTO.getXianyuAccountId(),
                    reqDTO.getOrderId()
            );

            if (result != null) {
                orderMapper.updateConfirmState(reqDTO.getXianyuAccountId(), reqDTO.getOrderId());
                redFlowerService.requestAfterShipmentConfirmed(reqDTO.getXianyuAccountId(), reqDTO.getOrderId());
                return ResultObject.success(result);
            } else {
                return ResultObject.failed("确认发货失败");
            }

        } catch (Exception e) {
            log.error("确认发货失败", e);
            return ResultObject.failed("确认发货失败: " + e.getMessage());
        }
    }

    @PostMapping("/detail")
    public ResultObject<String> getOrderDetail(@RequestBody OrderDetailReqDTO reqDTO) {
        try {
            log.info("获取订单详情请求: xianyuAccountId={}, orderId={}, fromServer={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getOrderId(), reqDTO.getFromServer());

            if (reqDTO.getXianyuAccountId() == null) {
                return ResultObject.failed("账号ID不能为空");
            }
            if (reqDTO.getOrderId() == null || reqDTO.getOrderId().isEmpty()) {
                return ResultObject.failed("订单ID不能为空");
            }

            boolean fromServer = reqDTO.getFromServer() != null && reqDTO.getFromServer();
            String detail;
            if (fromServer) {
                detail = orderService.getOrderDetail(reqDTO.getXianyuAccountId(), reqDTO.getOrderId());
            } else {
                detail = orderService.getOrderDetailFromLocal(reqDTO.getXianyuAccountId(), reqDTO.getOrderId());
            }
            if (detail != null) {
                return ResultObject.success(detail);
            } else {
                return ResultObject.failed("获取订单详情失败");
            }
        } catch (Exception e) {
            log.error("获取订单详情失败", e);
            return ResultObject.failed("获取订单详情失败: " + e.getMessage());
        }
    }

    /** 获取本地订单、自动发货、评价和小红花组成的生命周期时间线。 */
    @PostMapping("/timeline")
    public ResultObject<OrderTimelineRespDTO> getOrderTimeline(
            @RequestBody OrderDetailReqDTO reqDTO) {
        if (reqDTO == null || reqDTO.getXianyuAccountId() == null) {
            return ResultObject.failed("账号ID不能为空");
        }
        if (reqDTO.getOrderId() == null || reqDTO.getOrderId().isBlank()) {
            return ResultObject.failed("订单ID不能为空");
        }
        try {
            return ResultObject.success(orderTimelineService.getTimeline(
                    reqDTO.getXianyuAccountId(), reqDTO.getOrderId()));
        } catch (IllegalArgumentException e) {
            return ResultObject.failed(e.getMessage());
        } catch (Exception e) {
            log.error("获取订单生命周期失败: accountId={}, orderId={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getOrderId(), e);
            return ResultObject.failed("获取订单生命周期失败: " + e.getMessage());
        }
    }

    @lombok.Data
    public static class SyncOrderHistoryReqDTO {
        private Long xianyuAccountId;
    }

    /**
     * 手动同步订单管理数据：包含全部售出订单、退款中和已退款订单。
     * 同步过程不会触发自动发货。
     */
    @PostMapping("/syncHistory")
    public ResultObject<Map<String, Integer>> syncOrderHistory(@RequestBody SyncOrderHistoryReqDTO reqDTO) {
        if (reqDTO.getXianyuAccountId() == null) {
            return ResultObject.failed("账号ID不能为空");
        }
        try {
            List<Map<String, Object>> soldOrders = orderService.querySoldOrders(reqDTO.getXianyuAccountId(), 10);
            List<Map<String, Object>> refundOrders = orderService.queryRefundOrders(reqDTO.getXianyuAccountId());
            List<Map<String, Object>> recentSoldOrders = pendingOrderPollService.filterRecentHistoryOrders(soldOrders);
            List<Map<String, Object>> recentRefundOrders = pendingOrderPollService.filterRecentHistoryOrders(refundOrders);
            List<Map<String, Object>> allOrders = new ArrayList<>(recentSoldOrders);
            // 退款数据后写入，确保同一订单以退款状态为准。
            allOrders.addAll(recentRefundOrders);
            int synced = pendingOrderPollService.syncOrderHistoryToDb(reqDTO.getXianyuAccountId(), allOrders);
            return ResultObject.success(Map.of(
                    "soldCount", recentSoldOrders.size(),
                    "refundCount", recentRefundOrders.size(),
                    "syncedCount", synced,
                    "skippedCount", soldOrders.size() + refundOrders.size() - allOrders.size()
            ));
        } catch (Exception e) {
            log.error("同步订单历史失败", e);
            return ResultObject.failed("同步订单失败: " + e.getMessage());
        }
    }

}
