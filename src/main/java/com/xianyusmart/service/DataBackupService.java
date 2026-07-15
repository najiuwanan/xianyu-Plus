package com.xianyusmart.service;

import com.xianyusmart.service.bo.*;

import java.util.List;

public interface DataBackupService {

    List<BackupModuleRespBO> getModules();

    BackupExportRespBO exportData(BackupExportReqBO reqBO);

    BackupImportRespBO importData(BackupImportReqBO reqBO);
}
