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
            map.put("avatarUrl", account.getAvatarUrl());
            map.put("status", account.getStatus());
            map.put("autoRateEnabled", account.getAutoRateEnabled());
            map.put("autoRateText", account.getAutoRateText());
            map.put("autoAskFlower", account.getAutoAskFlower());
            map.put("autoAskFlowerText", account.getAutoAskFlowerText());
            map.put("autoConnectOnStartup", account.getAutoConnectOnStartup());
            map.put("automationRiskPaused", account.getAutomationRiskPaused());
            map.put("automationRiskPauseReason", account.getAutomationRiskPauseReason());
            accountList.add(map);
        }

        List<Map<String, Object>> cookieList = new ArrayList<>();
        for (XianyuAccount account : accounts) {
            List<XianyuCookie> cookies = cookieMapper.selectList(new LambdaQueryWrapper<XianyuCookie>()
                    .eq(XianyuCookie::getXianyuAccountId, account.getId()));
            for (XianyuCookie cookie : cookies) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("unb", account.getUnb());
                map.put("cookieText", cookie.getCookieText());
                map.put("mH5Tk", cookie.getMH5Tk());
                map.put("cookieStatus", cookie.getCookieStatus());
                map.put("expireTime", cookie.getExpireTime());
                map.put("websocketToken", cookie.getWebsocketToken());
                map.put("tokenExpireTime", cookie.getTokenExpireTime());
                cookieList.add(map);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accounts", accountList);
        data.put("cookies", cookieList);
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
                        applyAccountValues(account, map, false);
                        accountMapper.insert(account);
                    } else {
                        account = existing;
                        applyAccountValues(account, map, true);
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

    private void applyAccountValues(XianyuAccount account, Map<String, Object> map, boolean onlyWhenPresent) {
        setString(map, "accountNote", account::setAccountNote, onlyWhenPresent);
        setString(map, "deviceId", account::setDeviceId, onlyWhenPresent);
        setString(map, "avatarUrl", account::setAvatarUrl, onlyWhenPresent);
        setInteger(map, "status", account::setStatus, onlyWhenPresent);
        setInteger(map, "autoRateEnabled", account::setAutoRateEnabled, onlyWhenPresent);
        setString(map, "autoRateText", account::setAutoRateText, onlyWhenPresent);
        setInteger(map, "autoAskFlower", account::setAutoAskFlower, onlyWhenPresent);
        setString(map, "autoAskFlowerText", account::setAutoAskFlowerText, onlyWhenPresent);
        setInteger(map, "autoConnectOnStartup", account::setAutoConnectOnStartup, onlyWhenPresent);
        setInteger(map, "automationRiskPaused", account::setAutomationRiskPaused, onlyWhenPresent);
        setString(map, "automationRiskPauseReason", account::setAutomationRiskPauseReason, onlyWhenPresent);
    }

    private void setString(Map<String, Object> map, String key, java.util.function.Consumer<String> setter,
                           boolean onlyWhenPresent) {
        if (!onlyWhenPresent || map.containsKey(key)) {
            setter.accept((String) map.get(key));
        }
    }

    private void setInteger(Map<String, Object> map, String key, java.util.function.Consumer<Integer> setter,
                            boolean onlyWhenPresent) {
        if (!onlyWhenPresent || map.containsKey(key)) {
            Object value = map.get(key);
            setter.accept(value == null ? null : ((Number) value).intValue());
        }
    }
}
