package com.xianyusmart.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GoodsSkuPreferencesReqDTO {

    @NotNull(message = "账号不能为空")
    private Long xianyuAccountId;

    @NotBlank(message = "商品ID不能为空")
    private String xyGoodsId;

    @Valid
    @NotEmpty(message = "规格列表不能为空")
    private List<GoodsSkuPreferenceItemDTO> items = new ArrayList<>();
}
