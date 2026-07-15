package com.xianyusmart.service.bo;

import lombok.Data;

@Data
public class BackupImportReqBO {
    private String jsonData;
    private java.util.List<String> modules;
}
