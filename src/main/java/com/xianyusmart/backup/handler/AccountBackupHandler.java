package com.xianyusmart.backup.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xianyusmart.backup.DataBackupHandler;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuCookie;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuCookieMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccountBackupHandler implements DataBackupHandler {

    @Autowired
    private XianyuAccountMapper accountMapper;

    @Autowired
    private XianyuCookieMapper cookieMapper;

    @Override
    public String getModuleKey() {
        return "account";
    }

    @Override
    public String getModuleName() {
        return "闲鱼账号";
    }

    @Override
    public Map<String, Object> exportData() {
        List<XianyuAccount> accounts = accountMapper.selectList(null);

        List<Map<String, Object>> accountList = new ArrayList<>();
        for (XianyuAccount account : accounts) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("unb", account.getUnb());
            map.put("accountNote", account.getAccountNote());
            map.put("deviceId", account.getDeviceId());
            map.put("status", account.getStatus());
            accountList.add(map);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accounts", accountList);
        return data;
    }

    @Override
    public void importData(Map<String, Object> data, Map<String, Object> context) {
        if (data == null) return;

        Map<String, Long> unbToAccountId = new HashMap<>();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> accountMaps = (List<Map<String, Object>>) data.get("accounts");
        if (accountMaps != null) {
            for (Map<String, Object> map : accountMaps) {
                try {
                    String unb = (String) map.get("unb");
                    if (unb == null) continue;

                    LambdaQueryWrapper<XianyuAccount> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(XianyuAccount::getUnb, unb);
                    XianyuAccount existing = accountMapper.selectOne(wrapper);

                    XianyuAccount account;
                    if (existing == null) {
                        account = new XianyuAccount();
                        account.setUnb(unb);
                        account.setAccountNote((String) map.get("accountNote"));
                        account.setDeviceId((String) map.get("deviceId"));
                        account.setStatus(map.get("status") != null ? ((Number) map.get("status")).intValue() : null);
                        accountMapper.insert(account);
                    } else {
                        account = existing;
                        if (map.get("accountNote") != null) {
                            account.setAccountNote((String) map.get("accountNote"));
                        }
                        if (map.get("deviceId") != null) {
                            account.setDeviceId((String) map.get("deviceId"));
                        }
                        if (map.get("status") != null) {
                            account.setStatus(((Number) map.get("status")).intValue());
                        }
                        accountMapper.updateById(account);
                    }
                    unbToAccountId.put(unb, account.getId());
                } catch (Exception e) {
                    log.warn("[AccountBackup] 导入单条账号数据失败: {}", e.getMessage());
                }
            }
        }

        context.put("unbToAccountId", unbToAccountId);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cookieMaps = (List<Map<String, Object>>) data.get("cookies");
        if (cookieMaps != null) {
            for (Map<String, Object> map : cookieMaps) {
                try {
                    String unb = (String) map.get("unb");
                    if (unb == null || !unbToAccountId.containsKey(unb)) continue;

                    Long accountId = unbToAccountId.get(unb);
                    LambdaQueryWrapper<XianyuCookie> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(XianyuCookie::getXianyuAccountId, accountId);
                    XianyuCookie existing = cookieMapper.selectOne(wrapper);

                    XianyuCookie cookie = new XianyuCookie();
                    cookie.setXianyuAccountId(accountId);
                    cookie.setCookieText((String) map.get("cookieText"));
                    cookie.setMH5Tk((String) map.get("mH5Tk"));
                    cookie.setCookieStatus(map.get("cookieStatus") != null ? ((Number) map.get("cookieStatus")).intValue() : null);
                    cookie.setExpireTime((String) map.get("expireTime"));
                    cookie.setWebsocketToken((String) map.get("websocketToken"));
                    cookie.setTokenExpireTime(map.get("tokenExpireTime") != null ? ((Number) map.get("tokenExpireTime")).longValue() : null);

                    if (existing == null) {
                        cookieMapper.insert(cookie);
                    } else {
                        cookie.setId(existing.getId());
                        cookieMapper.updateById(cookie);
                    }
                } catch (Exception e) {
                    log.warn("[AccountBackup] 导入单条Cookie数据失败: {}", e.getMessage());
                }
            }
        }
    }
}
