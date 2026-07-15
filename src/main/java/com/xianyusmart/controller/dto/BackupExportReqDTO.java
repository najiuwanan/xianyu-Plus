package com.xianyusmart.controller.dto;

import lombok.Data;

import java.util.List;

@Data
public class BackupExportReqDTO {
    private List<String> modules;
}
