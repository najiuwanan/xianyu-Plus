package com.xianyusmart.controller.dto;

import lombok.Data;

@Data
public class KamiExportReqDTO {
    private Long kamiConfigId;
    private Boolean includeUnused;
    private Boolean includeUsed;
}
