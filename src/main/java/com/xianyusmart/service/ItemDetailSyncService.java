package com.xianyusmart.service;

import com.xianyusmart.controller.dto.SyncProgressRespDTO;
import com.xianyusmart.controller.dto.ItemDTO;
import java.util.List;

public interface ItemDetailSyncService {
    String startSync(Long accountId, List<ItemDTO> items);
    SyncProgressRespDTO getProgress(String syncId);
    void cancelSync(String syncId);
    boolean isSyncing(Long accountId);
    boolean syncSingleItem(Long accountId, String itemId);
}
