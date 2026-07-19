package com.xianyusmart.controller.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

@Data
public class KamiBatchImportReqDTO {

    @NotNull(message = "卡密配置ID不能为空")
    private Long kamiConfigId;

    @NotBlank(message = "卡券内容不能为空")
    private String kamiContents;
}
