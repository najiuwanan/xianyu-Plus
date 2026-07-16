package com.xianyusmart.backup.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xianyusmart.backup.DataBackupHandler;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuKamiConfig;
import com.xianyusmart.entity.XianyuKamiItem;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuKamiConfigMapper;
import com.xianyusmart.mapper.XianyuKamiItemMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class KamiBackupHandler implements DataBackupHandler {

    @Autowired
    private XianyuKamiConfigMapper kamiConfigMapper;

    @Autowired
    private XianyuKamiItemMapper kamiItemMapper;

    @Autowired
    private XianyuAccountMapper accountMapper;

    @Override
    public String getModuleKey() {
        return "kami";
    }

    @Override
    public String getModuleName() {
        return "卡券管理";
    }

    @Override
    public Map<String, Object> exportData() {
        List<XianyuKamiConfig> kamiConfigs = kamiConfigMapper.selectList(null);

        List<Map<String, Object>> configList = new ArrayList<>();
        Map<Long, String> configIdToBackupKey = new HashMap<>();
        for (XianyuKamiConfig config : kamiConfigs) {
            XianyuAccount account = config.getXianyuAccountId() == null
                    ? null
                    : accountMapper.selectById(config.getXianyuAccountId());

            Map<String, Object> map = new LinkedHashMap<>();
            // 卡券库全局共享，使用来源配置 ID 保持配置与库存项的关联。
            String backupKey = String.valueOf(config.getId());
            map.put("configKey", backupKey);
            if (account != null) {
                map.put("unb", account.getUnb());
            }
            map.put("aliasName", config.getAliasName());
            map.put("alertEnabled", config.getAlertEnabled());
            map.put("alertThresholdType", config.getAlertThresholdType());
            map.put("alertThresholdValue", config.getAlertThresholdValue());
            map.put("alertEmail", config.getAlertEmail());
            configList.add(map);
            configIdToBackupKey.put(config.getId(), backupKey);
        }

        List<Map<String, Object>> itemList = new ArrayList<>();
        for (XianyuKamiConfig config : kamiConfigs) {
            List<XianyuKamiItem> items = kamiItemMapper.findByConfigIdAndStatus(config.getId(), 0);
            String backupKey = configIdToBackupKey.get(config.getId());
            if (backupKey == null) continue;

            for (XianyuKamiItem item : items) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("configKey", backupKey);
                map.put("aliasName", config.getAliasName());
                map.put("kamiContent", item.getKamiContent());
                itemList.add(map);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kamiConfigs", configList);
        data.put("kamiItems", itemList);
        return data;
    }

    @Override
    public void importData(Map<String, Object> data, Map<String, Object> context) {
        if (data == null) return;

        @SuppressWarnings("unchecked")
        Map<String, Long> unbToAccountId = context.get("unbToAccountId") != null
                ? (Map<String, Long>) context.get("unbToAccountId")
                : Collections.emptyMap();

        Map<String, Long> configKeyToId = new HashMap<>();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> configMaps = (List<Map<String, Object>>) data.get("kamiConfigs");
        if (configMaps != null) {
            int skippedCount = 0;
            for (Map<String, Object> map : configMaps) {
                try {
                    String aliasName = (String) map.get("aliasName");
                    String configKey = (String) map.get("configKey");
                    String unb = (String) map.get("unb");
                    if (aliasName == null) continue;

                    // 兼容旧备份：旧格式按账号恢复；新格式一律恢复为全局共享卡券库。
                    Long accountId = configKey == null && unb != null ? unbToAccountId.get(unb) : null;
                    if (configKey == null && unb != null && accountId == null) {
                        log.warn("[KamiBackup] 跳过旧卡券配置: 找不到账号, unb={}, aliasName={}", unb, aliasName);
                        skippedCount++;
                        continue;
                    }

                    LambdaQueryWrapper<XianyuKamiConfig> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(XianyuKamiConfig::getAliasName, aliasName);
                    if (accountId == null) {
                        wrapper.isNull(XianyuKamiConfig::getXianyuAccountId);
                    } else {
                        wrapper.eq(XianyuKamiConfig::getXianyuAccountId, accountId);
                    }
                    List<XianyuKamiConfig> matches = kamiConfigMapper.selectList(wrapper);
                    XianyuKamiConfig existing = matches.size() == 1 ? matches.getFirst() : null;

                    XianyuKamiConfig config = new XianyuKamiConfig();
                    config.setXianyuAccountId(accountId);
                    config.setAliasName(aliasName);
                    config.setAlertEnabled(map.get("alertEnabled") != null ? ((Number) map.get("alertEnabled")).intValue() : null);
                    config.setAlertThresholdType(map.get("alertThresholdType") != null ? ((Number) map.get("alertThresholdType")).intValue() : null);
                    config.setAlertThresholdValue(map.get("alertThresholdValue") != null ? ((Number) map.get("alertThresholdValue")).intValue() : null);
                    config.setAlertEmail((String) map.get("alertEmail"));

                    if (existing == null) {
                        config.setTotalCount(0);
                        config.setUsedCount(0);
                        kamiConfigMapper.insert(config);
                    } else {
                        config.setId(existing.getId());
                        config.setTotalCount(existing.getTotalCount());
                        config.setUsedCount(existing.getUsedCount());
                        kamiConfigMapper.updateById(config);
                    }
                    String mapKey = configKey != null ? configKey : unb + ":" + aliasName;
                    configKeyToId.put(mapKey, config.getId());
                } catch (Exception e) {
                    log.warn("[KamiBackup] 导入单条卡密配置失败: {}", e.getMessage());
                }
            }
            if (skippedCount > 0) {
                log.warn("[KamiBackup] 共跳过 {} 条配置数据（账号不存在）", skippedCount);
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemMaps = (List<Map<String, Object>>) data.get("kamiItems");
        if (itemMaps != null) {
            int skippedCount = 0;
            for (Map<String, Object> map : itemMaps) {
                try {
                    String configKey = (String) map.get("configKey");
                    String unb = (String) map.get("unb");
                    String aliasName = (String) map.get("aliasName");
                    String kamiContent = (String) map.get("kamiContent");
                    if (aliasName == null || kamiContent == null) continue;

                    String mapKey = configKey != null ? configKey : unb + ":" + aliasName;
                    Long configId = configKeyToId.get(mapKey);
                    if (configId == null) {
                        skippedCount++;
                        continue;
                    }

                    LambdaQueryWrapper<XianyuKamiItem> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(XianyuKamiItem::getKamiConfigId, configId)
                           .eq(XianyuKamiItem::getKamiContent, kamiContent);
                    XianyuKamiItem existing = kamiItemMapper.selectOne(wrapper);
                    if (existing != null) continue;

                    XianyuKamiItem item = new XianyuKamiItem();
                    item.setKamiConfigId(configId);
                    item.setKamiContent(kamiContent);
                    item.setStatus(0);
                    item.setSortOrder(0);
                    kamiItemMapper.insert(item);
                } catch (Exception e) {
                    log.warn("[KamiBackup] 导入单条卡密项失败: {}", e.getMessage());
                }
            }
            if (skippedCount > 0) {
                log.warn("[KamiBackup] 共跳过 {} 条卡密项数据（配置不存在）", skippedCount);
            }
        }
    }
}
