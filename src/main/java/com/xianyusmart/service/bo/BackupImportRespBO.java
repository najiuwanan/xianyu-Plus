package com.xianyusmart.service.bo;

import lombok.Data;

@Data
public class BackupImportRespBO {
    private int totalCount;
    private int successCount;
    private java.util.List<String> failedModules;
}
