package com.xianyusmart.controller.dto;

import lombok.Data;

/** 订单管理“更多操作”中可展示的人工补偿动作。 */
@Data
public class OrderAutomationAvailableActionsDTO {

    /** 闲鱼待评价列表仍包含该订单时才为 true。 */
    private boolean rateAvailable;
    private String rateReason;

    /** 已确认发货、交易正常且此前未成功请求时才为 true。 */
    private boolean redFlowerAvailable;
    private String redFlowerReason;
}
