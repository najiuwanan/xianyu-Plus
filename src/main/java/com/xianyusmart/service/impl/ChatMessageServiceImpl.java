package com.xianyusmart.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.common.ResultObject;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuChatMessage;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuChatMessageMapper;
import com.xianyusmart.controller.dto.MsgContextReqDTO;
import com.xianyusmart.controller.dto.MsgDTO;
import com.xianyusmart.controller.dto.MsgListReqDTO;
import com.xianyusmart.controller.dto.MsgListRespDTO;
import com.xianyusmart.controller.dto.ChatSessionDTO;
import com.xianyusmart.controller.dto.ChatSessionReqDTO;
import com.xianyusmart.controller.dto.ChatSessionReadReqDTO;
import com.xianyusmart.controller.dto.ChatBuyerTagReqDTO;
import com.xianyusmart.entity.XianyuChatBuyerTag;
import com.xianyusmart.service.ChatMessageService;
import com.xianyusmart.mapper.XianyuChatBuyerTagMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.util.StringUtils;

/**
 * 聊天消息服务实现
 * 
 * <p>职责：提供消息查询相关的服务</p>
 * <p>注意：WebSocket 消息的解析和保存现在由 SyncMessageHandler 直接处理</p>
 */
@Slf4j
@Service
public class ChatMessageServiceImpl implements ChatMessageService {
    
    @Autowired
    private XianyuChatMessageMapper chatMessageMapper;
    
    @Autowired
    private XianyuAccountMapper accountMapper;

    @Autowired
    private XianyuChatBuyerTagMapper buyerTagMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private static final Set<String> AVATAR_KEYS = Set.of(
            "avatar", "avatarurl", "senderavatar", "senderavatarurl",
            "useravatar", "useravatarurl", "headurl", "headpic",
            "headimage", "portrait", "portraiturl", "usericon", "usericonurl"
    );

    private static final long AVATAR_CACHE_MILLIS = 60_000L;
    private final Map<String, AvatarCacheEntry> buyerAvatarCache = new ConcurrentHashMap<>();
    
    @Override
    public List<XianyuChatMessage> getMessagesByAccountId(Long accountId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return chatMessageMapper.findByAccountId(accountId, pageSize, offset);
    }
    
    @Override
    public List<XianyuChatMessage> getMessagesBySessionId(String sessionId) {
        return chatMessageMapper.findBySId(sessionId);
    }
    
    @Override
    public ResultObject<MsgListRespDTO> getMessageList(MsgListReqDTO reqDTO) {
        try {
            // 参数验证
            if (reqDTO.getXianyuAccountId() == null) {
                return ResultObject.validateFailed("xianyuAccountId不能为空");
            }
            
            // 设置默认值
            int pageNum = reqDTO.getPageNum() != null && reqDTO.getPageNum() > 0 ? reqDTO.getPageNum() : 1;
            int pageSize = reqDTO.getPageSize() != null && reqDTO.getPageSize() > 0 ? reqDTO.getPageSize() : 20;
            
            // 计算偏移量
            int offset = (pageNum - 1) * pageSize;
            
            // 获取当前账号的UNB（用于过滤）
            String currentAccountUnb = null;
            if (reqDTO.getFilterCurrentAccount() != null && reqDTO.getFilterCurrentAccount()) {
                XianyuAccount account = accountMapper.selectById(reqDTO.getXianyuAccountId());
                if (account != null) {
                    currentAccountUnb = account.getUnb();
                }
            }
            
            // 查询总数
            int totalCount = chatMessageMapper.countMessages(
                    reqDTO.getXianyuAccountId(),
                    reqDTO.getXyGoodsId(),
                    currentAccountUnb
            );
            
            // 查询分页数据
            List<XianyuChatMessage> messages = chatMessageMapper.findMessagesByPage(
                    reqDTO.getXianyuAccountId(),
                    reqDTO.getXyGoodsId(),
                    currentAccountUnb,
                    pageSize,
                    offset
            );
            
            // 转换为DTO
            List<MsgDTO> msgDTOList = new ArrayList<>();
            if (messages != null) {
                for (XianyuChatMessage message : messages) {
                    MsgDTO msgDTO = new MsgDTO();
                    msgDTO.setId(message.getId());
                    msgDTO.setSId(message.getSId());
                    msgDTO.setContentType(message.getContentType());
                    msgDTO.setMsgContent(message.getMsgContent());
                    msgDTO.setXyGoodsId(message.getXyGoodsId());
                    msgDTO.setReminderUrl(message.getReminderUrl());
                    msgDTO.setSenderUserName(message.getSenderUserName());
                    msgDTO.setSenderUserId(message.getSenderUserId());
                    msgDTO.setMessageTime(message.getMessageTime());
                    msgDTOList.add(msgDTO);
                }
            }
            
            // 计算总页数
            int totalPage = (int) Math.ceil((double) totalCount / pageSize);
            if (totalPage == 0 && totalCount > 0) {
                totalPage = 1;
            }
            
            // 构建响应
            MsgListRespDTO respDTO = new MsgListRespDTO();
            respDTO.setList(msgDTOList);
            respDTO.setTotalCount(totalCount);
            respDTO.setPageNum(pageNum);
            respDTO.setPageSize(pageSize);
            respDTO.setTotalPage(totalPage);
            
            return ResultObject.success(respDTO);
            
        } catch (Exception e) {
            log.error("查询消息列表失败: accountId={}, xyGoodsId={}, filterCurrentAccount={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), reqDTO.getFilterCurrentAccount(), e);
            return ResultObject.failed("查询消息列表失败: " + e.getMessage());
        }
    }
    
    @Override
    public ResultObject<?> getContextMessages(MsgContextReqDTO reqDTO) {
        try {
            if (reqDTO.getSid() == null || reqDTO.getSid().isEmpty()) {
                return ResultObject.validateFailed("sid不能为空");
            }
            
            int limit = reqDTO.getLimit() != null && reqDTO.getLimit() > 0 ? reqDTO.getLimit() : 20;
            int offset = reqDTO.getOffset() != null && reqDTO.getOffset() >= 0 ? reqDTO.getOffset() : 0;
            
            List<XianyuChatMessage> messages = reqDTO.getXianyuAccountId() == null
                    ? chatMessageMapper.findRecentBySId(reqDTO.getSid(), limit, offset)
                    : chatMessageMapper.findRecentByAccountAndSId(
                            reqDTO.getXianyuAccountId(), reqDTO.getSid(), limit, offset);
            
            List<MsgDTO> msgDTOList = new ArrayList<>();
            if (messages != null) {
                for (XianyuChatMessage message : messages) {
                    MsgDTO msgDTO = new MsgDTO();
                    msgDTO.setId(message.getId());
                    msgDTO.setSId(message.getSId());
                    msgDTO.setContentType(message.getContentType());
                    msgDTO.setMsgContent(message.getMsgContent());
                    msgDTO.setXyGoodsId(message.getXyGoodsId());
                    msgDTO.setReminderUrl(message.getReminderUrl());
                    msgDTO.setSenderUserName(message.getSenderUserName());
                    msgDTO.setSenderUserId(message.getSenderUserId());
                    msgDTO.setMessageTime(message.getMessageTime());
                    msgDTOList.add(msgDTO);
                }
            }
            
            return ResultObject.success(msgDTOList);
            
        } catch (Exception e) {
            log.error("查询上下文消息失败: sid={}", reqDTO.getSid(), e);
            return ResultObject.failed("查询上下文消息失败: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<List<ChatSessionDTO>> getSessionList(ChatSessionReqDTO reqDTO) {
        try {
            if (reqDTO.getXianyuAccountId() == null) {
                return ResultObject.validateFailed("xianyuAccountId不能为空");
            }
            XianyuAccount account = accountMapper.selectById(reqDTO.getXianyuAccountId());
            if (account == null) {
                return ResultObject.validateFailed("账号不存在");
            }
            int limit = reqDTO.getLimit() == null ? 80 : Math.max(1, Math.min(reqDTO.getLimit(), 200));
            List<ChatSessionDTO> sessions = chatMessageMapper.findRecentSessions(
                    reqDTO.getXianyuAccountId(), account.getUnb(), limit);
            populateBuyerAvatars(reqDTO.getXianyuAccountId(), account.getUnb(), sessions);
            return ResultObject.success(sessions);
        } catch (Exception e) {
            log.error("查询在线客服会话失败: accountId={}", reqDTO.getXianyuAccountId(), e);
            return ResultObject.failed("查询在线客服会话失败: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<String> markSessionRead(ChatSessionReadReqDTO reqDTO) {
        if (reqDTO == null || reqDTO.getXianyuAccountId() == null || !StringUtils.hasText(reqDTO.getSid())) {
            return ResultObject.validateFailed("账号和会话不能为空");
        }
        try {
            long lastMessageId = chatMessageMapper.findLatestMessageIdBySession(
                    reqDTO.getXianyuAccountId(), reqDTO.getSid());
            chatMessageMapper.markSessionRead(reqDTO.getXianyuAccountId(), reqDTO.getSid(), lastMessageId);
            return ResultObject.success("已标记为已读");
        } catch (Exception e) {
            log.error("标记客服会话已读失败: accountId={}, sid={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getSid(), e);
            return ResultObject.failed("标记已读失败: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<String> addBuyerTag(ChatBuyerTagReqDTO reqDTO) {
        String validationError = validateBuyerTagRequest(reqDTO);
        if (validationError != null) {
            return ResultObject.validateFailed(validationError);
        }
        try {
            XianyuChatBuyerTag tag = new XianyuChatBuyerTag();
            tag.setXianyuAccountId(reqDTO.getXianyuAccountId());
            tag.setBuyerUserId(reqDTO.getBuyerUserId().trim());
            tag.setTagName(reqDTO.getTagName().trim());
            buyerTagMapper.insert(tag);
            return ResultObject.success("买家标签已添加");
        } catch (Exception e) {
            log.error("添加买家标签失败: accountId={}, buyerUserId={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getBuyerUserId(), e);
            return ResultObject.failed("添加标签失败: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<String> removeBuyerTag(ChatBuyerTagReqDTO reqDTO) {
        String validationError = validateBuyerTagRequest(reqDTO);
        if (validationError != null) {
            return ResultObject.validateFailed(validationError);
        }
        try {
            buyerTagMapper.delete(reqDTO.getXianyuAccountId(), reqDTO.getBuyerUserId().trim(), reqDTO.getTagName().trim());
            return ResultObject.success("买家标签已删除");
        } catch (Exception e) {
            log.error("删除买家标签失败: accountId={}, buyerUserId={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getBuyerUserId(), e);
            return ResultObject.failed("删除标签失败: " + e.getMessage());
        }
    }

    private String validateBuyerTagRequest(ChatBuyerTagReqDTO reqDTO) {
        if (reqDTO == null || reqDTO.getXianyuAccountId() == null || !StringUtils.hasText(reqDTO.getBuyerUserId())) {
            return "账号和买家不能为空";
        }
        if (!StringUtils.hasText(reqDTO.getTagName())) {
            return "标签不能为空";
        }
        String tag = reqDTO.getTagName().trim();
        if (tag.length() > 20) {
            return "标签最多 20 个字符";
        }
        if (tag.contains(",")) {
            return "标签不能包含英文逗号";
        }
        return null;
    }

    private String extractBuyerAvatarUrl(String completeMsg) {
        if (!StringUtils.hasText(completeMsg)) {
            return null;
        }
        try {
            return findAvatarUrl(objectMapper.readValue(completeMsg, Object.class));
        } catch (Exception e) {
            log.debug("解析买家头像失败", e);
            return null;
        }
    }

    private String findAvatarUrl(Object node) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = normalizeKey(entry.getKey());
                if (AVATAR_KEYS.contains(key)) {
                    String avatarUrl = extractImageUrl(entry.getValue());
                    if (avatarUrl != null) {
                        return avatarUrl;
                    }
                }
            }
            for (Object value : map.values()) {
                String avatarUrl = findAvatarUrl(value);
                if (avatarUrl != null) {
                    return avatarUrl;
                }
            }
        } else if (node instanceof List<?> list) {
            for (Object value : list) {
                String avatarUrl = findAvatarUrl(value);
                if (avatarUrl != null) {
                    return avatarUrl;
                }
            }
        } else if (node instanceof String text) {
            String nestedJson = text.trim();
            if ((nestedJson.startsWith("{") && nestedJson.endsWith("}"))
                    || (nestedJson.startsWith("[") && nestedJson.endsWith("]"))) {
                try {
                    return findAvatarUrl(objectMapper.readValue(nestedJson, Object.class));
                } catch (Exception ignored) {
                    // 普通聊天文本可能恰好带有大括号，无法解析时继续忽略。
                }
            }
        }
        return null;
    }

    private String extractImageUrl(Object value) {
        if (value instanceof String text) {
            return normalizeImageUrl(text);
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = normalizeKey(entry.getKey());
                if (key.equals("url") || key.equals("imageurl") || key.equals("picurl")
                        || key.equals("resourceurl")) {
                    String imageUrl = extractImageUrl(entry.getValue());
                    if (imageUrl != null) {
                        return imageUrl;
                    }
                }
            }
        } else if (value instanceof List<?> list) {
            for (Object item : list) {
                String imageUrl = extractImageUrl(item);
                if (imageUrl != null) {
                    return imageUrl;
                }
            }
        }
        return null;
    }

    private String normalizeKey(Object key) {
        return String.valueOf(key).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String normalizeImageUrl(String value) {
        String url = value == null ? "" : value.trim();
        if (url.startsWith("//")) {
            url = "https:" + url;
        }
        return url.startsWith("https://") || url.startsWith("http://") ? url : null;
    }

    private void populateBuyerAvatars(Long accountId, String sellerUserId, List<ChatSessionDTO> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean needsRefresh = false;
        for (ChatSessionDTO session : sessions) {
            AvatarCacheEntry cached = buyerAvatarCache.get(avatarCacheKey(accountId, session.getSid()));
            if (cached != null && cached.expiresAt() > now) {
                session.setBuyerAvatarUrl(cached.avatarUrl());
            } else {
                needsRefresh = true;
            }
        }
        if (!needsRefresh) {
            return;
        }

        Map<String, String> resolvedAvatars = new ConcurrentHashMap<>();
        List<ChatSessionDTO> candidates = chatMessageMapper.findBuyerAvatarCandidates(accountId, sellerUserId);
        if (candidates != null) {
            for (ChatSessionDTO candidate : candidates) {
                if (!StringUtils.hasText(candidate.getSid()) || resolvedAvatars.containsKey(candidate.getSid())) {
                    continue;
                }
                String avatarUrl = extractBuyerAvatarUrl(candidate.getBuyerCompleteMsg());
                if (avatarUrl != null) {
                    resolvedAvatars.put(candidate.getSid(), avatarUrl);
                }
            }
        }

        long expiresAt = now + AVATAR_CACHE_MILLIS;
        for (ChatSessionDTO session : sessions) {
            String cacheKey = avatarCacheKey(accountId, session.getSid());
            String avatarUrl = resolvedAvatars.get(session.getSid());
            AvatarCacheEntry cached = buyerAvatarCache.get(cacheKey);
            if (avatarUrl == null && cached != null && cached.expiresAt() > now) {
                avatarUrl = cached.avatarUrl();
            }
            buyerAvatarCache.put(cacheKey, new AvatarCacheEntry(avatarUrl, expiresAt));
            session.setBuyerAvatarUrl(avatarUrl);
        }
    }

    private String avatarCacheKey(Long accountId, String sid) {
        return accountId + ":" + sid;
    }

    private record AvatarCacheEntry(String avatarUrl, long expiresAt) {
    }
}
