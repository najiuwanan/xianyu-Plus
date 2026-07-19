package com.xianyusmart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.controller.dto.ChatAvatarQueryReqDTO;
import com.xianyusmart.controller.dto.ChatAvatarQueryRespDTO;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuChatUserProfile;
import com.xianyusmart.entity.XianyuCookie;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuChatUserProfileMapper;
import com.xianyusmart.mapper.XianyuCookieMapper;
import com.xianyusmart.service.ChatAvatarProfileService;
import com.xianyusmart.utils.XianyuApiUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 头像查询是纯展示增强：任何网络、Cookie 或解析错误都只返回空头像，不能影响客服与自动化。
 */
@Slf4j
@Service
public class ChatAvatarProfileServiceImpl implements ChatAvatarProfileService {
    private static final String USER_QUERY_API = "mtop.taobao.idlemessage.pc.user.query";
    private static final int MAX_BATCH_SIZE = 3;
    private static final long REQUEST_INTERVAL_MILLIS = 400L;
    private static final long FAILURE_CACHE_MILLIS = 5 * 60_000L;

    private final XianyuAccountMapper accountMapper;
    private final XianyuCookieMapper cookieMapper;
    private final XianyuChatUserProfileMapper profileMapper;
    private final ObjectMapper objectMapper;
    private final Map<Long, Object> accountLocks = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastRequestAt = new ConcurrentHashMap<>();
    private final Map<String, Long> failedUntil = new ConcurrentHashMap<>();

    public ChatAvatarProfileServiceImpl(XianyuAccountMapper accountMapper,
                                        XianyuCookieMapper cookieMapper,
                                        XianyuChatUserProfileMapper profileMapper,
                                        ObjectMapper objectMapper) {
        this.accountMapper = accountMapper;
        this.cookieMapper = cookieMapper;
        this.profileMapper = profileMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatAvatarQueryRespDTO query(ChatAvatarQueryReqDTO request) {
        ChatAvatarQueryRespDTO response = new ChatAvatarQueryRespDTO();
        if (request == null || request.getXianyuAccountId() == null) return response;

        XianyuAccount account = accountMapper.selectById(request.getXianyuAccountId());
        if (account == null) return response;
        response.setAccountAvatarUrl(account.getAvatarUrl());

        String cookie = findValidCookie(account.getId());
        List<ChatAvatarQueryReqDTO.QueryItem> queries = request.getQueries() == null
                ? List.of() : request.getQueries().stream().limit(MAX_BATCH_SIZE).toList();
        for (ChatAvatarQueryReqDTO.QueryItem item : queries) {
            if (item == null || !StringUtils.hasText(item.getSid()) || !StringUtils.hasText(item.getBuyerUserId())) continue;
            ChatAvatarQueryRespDTO.UserProfile profile = findOrFetchBuyer(account.getId(), item, cookie);
            if (profile != null) response.getBuyerProfiles().put(item.getBuyerUserId(), profile);
        }

        boolean forceOwnerRefresh = Boolean.TRUE.equals(request.getForceOwnerRefresh());
        if (forceOwnerRefresh) {
            accountMapper.updateAvatar(account.getId(), null);
            response.setAccountAvatarUrl(null);
        }
        if (Boolean.TRUE.equals(request.getIncludeOwner()) && StringUtils.hasText(cookie)
                && (forceOwnerRefresh || !StringUtils.hasText(response.getAccountAvatarUrl()))) {
            ChatAvatarQueryReqDTO.QueryItem source = queries.stream()
                    .filter(item -> item != null && StringUtils.hasText(item.getSid())).findFirst().orElse(null);
            if (source != null) {
                ChatAvatarQueryRespDTO.UserProfile owner = fetchProfile(account.getId(), source.getSid(), cookie, true);
                if (owner != null && StringUtils.hasText(owner.getAvatarUrl())) {
                    accountMapper.updateAvatar(account.getId(), owner.getAvatarUrl());
                    response.setAccountAvatarUrl(owner.getAvatarUrl());
                }
            }
        }
        return response;
    }

    private ChatAvatarQueryRespDTO.UserProfile findOrFetchBuyer(Long accountId,
                                                                 ChatAvatarQueryReqDTO.QueryItem item,
                                                                 String cookie) {
        boolean forceRefresh = Boolean.TRUE.equals(item.getForceRefresh());
        if (forceRefresh) {
            profileMapper.invalidateBuyerAvatar(accountId, item.getBuyerUserId());
            failedUntil.remove(accountId + ":" + item.getBuyerUserId());
        } else {
            XianyuChatUserProfile cached = profileMapper.findCachedByBuyer(accountId, item.getBuyerUserId());
            if (cached != null && StringUtils.hasText(cached.getAvatarUrl())) {
                return new ChatAvatarQueryRespDTO.UserProfile(cached.getAvatarUrl(), cached.getBuyerUserName());
            }
        }
        if (!StringUtils.hasText(cookie)) return null;

        String failureKey = accountId + ":" + item.getBuyerUserId();
        if (failedUntil.getOrDefault(failureKey, 0L) > System.currentTimeMillis()) return null;

        ChatAvatarQueryRespDTO.UserProfile fetched = fetchProfile(accountId, item.getSid(), cookie, false);
        if (fetched == null || !StringUtils.hasText(fetched.getAvatarUrl())) {
            failedUntil.put(failureKey, System.currentTimeMillis() + FAILURE_CACHE_MILLIS);
            return fetched;
        }

        XianyuChatUserProfile entity = new XianyuChatUserProfile();
        entity.setXianyuAccountId(accountId);
        entity.setSId(item.getSid());
        entity.setBuyerUserId(item.getBuyerUserId());
        entity.setBuyerUserName(fetched.getNick());
        entity.setAvatarUrl(fetched.getAvatarUrl());
        // 头像成功后永久复用；expires_at 保留仅为兼容已发布的 V22 表结构。
        entity.setExpiresAt(LocalDateTime.of(9999, 12, 31, 23, 59, 59));
        profileMapper.upsert(entity);
        failedUntil.remove(failureKey);
        return fetched;
    }

    private ChatAvatarQueryRespDTO.UserProfile fetchProfile(Long accountId, String sid,
                                                             String cookie, boolean owner) {
        try {
            waitForRateLimit(accountId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("type", 0);
            data.put("sessionType", 1);
            data.put("sessionId", sid.replace("@goofish", ""));
            data.put("isOwner", owner);
            XianyuApiUtils.ApiCallResultWithHeaders result = XianyuApiUtils.callApiWithHeaders(
                    USER_QUERY_API, data, cookie, "4.0",
                    "a21ybx.im.0.0", "a21ybx.home.sidebar.2",
                    Map.of("Content-Type", "application/x-www-form-urlencoded"),
                    Map.of("v", "4.0")
            );
            if (result == null || !StringUtils.hasText(result.getBody())) return null;
            JsonNode root = objectMapper.readTree(result.getBody());
            String ret = root.path("ret").isArray() && !root.path("ret").isEmpty()
                    ? root.path("ret").get(0).asText("") : "";
            if (!ret.contains("SUCCESS")) {
                log.debug("头像资料接口未成功: accountId={}, owner={}, ret={}", accountId, owner, ret);
                return null;
            }
            JsonNode userInfo = root.path("data").path("userInfo");
            String avatar = normalizeAvatarUrl(userInfo.path("logo").asText(""));
            String nick = userInfo.path("nick").asText("").trim();
            return StringUtils.hasText(avatar) || StringUtils.hasText(nick)
                    ? new ChatAvatarQueryRespDTO.UserProfile(avatar, nick) : null;
        } catch (Exception e) {
            log.debug("头像资料查询失败: accountId={}, sid={}, owner={}", accountId, sid, owner, e);
            return null;
        }
    }

    private void waitForRateLimit(Long accountId) throws InterruptedException {
        Object lock = accountLocks.computeIfAbsent(accountId, ignored -> new Object());
        synchronized (lock) {
            long wait = REQUEST_INTERVAL_MILLIS - (System.currentTimeMillis() - lastRequestAt.getOrDefault(accountId, 0L));
            if (wait > 0) Thread.sleep(wait);
            lastRequestAt.put(accountId, System.currentTimeMillis());
        }
    }

    private String findValidCookie(Long accountId) {
        XianyuCookie cookie = cookieMapper.selectOne(new LambdaQueryWrapper<XianyuCookie>()
                .eq(XianyuCookie::getXianyuAccountId, accountId)
                .eq(XianyuCookie::getCookieStatus, 1)
                .orderByDesc(XianyuCookie::getUpdatedTime)
                .last("LIMIT 1"));
        return cookie == null ? null : cookie.getCookieText();
    }

    private String normalizeAvatarUrl(String value) {
        String url = value == null ? "" : value.trim();
        if (url.startsWith("//")) url = "https:" + url;
        return url.startsWith("https://") || url.startsWith("http://") ? url : "";
    }
}
