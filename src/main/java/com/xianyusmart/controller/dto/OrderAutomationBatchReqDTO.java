package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 自动评价批量检查或提交请求。
 */
@Data
public class OrderAutomationBatchReqDTO {

    /** 为空时处理全部已启用且开启自动评价的账号。 */
    private Long accountId;

    /** CHECK：仅检查；RATE：检查后对平台待评价订单提交评价。 */
    private String action;
}
