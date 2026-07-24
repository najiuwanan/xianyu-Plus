package com.xianyusmart.controller.dto;

import com.xianyusmart.entity.XianyuGoodsInfo;
import lombok.Data;

@Data
public class ItemWithConfigDTO {
    
    private XianyuGoodsInfo item;
    
    private Integer xianyuAutoDeliveryOn;
    
    private Integer xianyuAutoReplyOn;
    
    private Integer xianyuAutoReplyContextOn;
    
    private Integer xianyuKeywordReplyOn;

    private Integer productDefaultReplyOn;

    private Integer humanInterventionOn;

    private Integer humanInterventionMinutes;
    
    private Integer autoDeliveryType;
    
    private String autoDeliveryContent;

    /**
     * 默认关联的卡券来源。多规格发货仍由原有规则单独处理。
     */
    private Long kamiConfigId;
}
