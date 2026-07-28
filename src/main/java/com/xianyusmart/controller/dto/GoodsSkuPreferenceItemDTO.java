package com.xianyusmart.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoodsSkuPreferenceItemDTO {

    @NotBlank(message = "规格ID不能为空")
    private String skuId;

    /** 自定义显示名；留空时恢复平台规格名称。 */
    private String displayName;
}
