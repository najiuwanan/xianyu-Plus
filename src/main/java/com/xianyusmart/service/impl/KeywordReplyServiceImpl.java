package com.xianyusmart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xianyusmart.entity.XianyuGoodsConfig;
import com.xianyusmart.entity.XianyuKeywordReplyContent;
import com.xianyusmart.entity.XianyuKeywordReplyRule;
import com.xianyusmart.entity.bo.KeywordReplyRuleBO;
import com.xianyusmart.mapper.XianyuGoodsConfigMapper;
import com.xianyusmart.mapper.XianyuKeywordReplyContentMapper;
import com.xianyusmart.mapper.XianyuKeywordReplyRuleMapper;
import com.xianyusmart.service.KeywordReplyService;
import com.xianyusmart.service.reply.KeywordTriggerMatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KeywordReplyServiceImpl implements KeywordReplyService {

    @Autowired
    private XianyuKeywordReplyRuleMapper ruleMapper;

    @Autowired
    private XianyuKeywordReplyContentMapper contentMapper;

    @Autowired
    private XianyuGoodsConfigMapper goodsConfigMapper;

    @Autowired
    private KeywordTriggerMatcher triggerMatcher;

    @Override
    public List<KeywordReplyRuleBO> getRules(Long accountId, String xyGoodsId) {
        List<XianyuKeywordReplyRule> rules = ruleMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
        if (rules == null || rules.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<XianyuKeywordReplyContent>> contentMap = loadContents(rules);

        return rules.stream().map(rule -> toRuleBO(rule, contentMap.getOrDefault(rule.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    @Override
    public KeywordReplyRuleBO addRule(Long accountId, String xyGoodsId, String keyword, Integer matchMode) {
        List<String> triggers = requireTriggers(keyword);
        ensureNoDuplicateTriggers(accountId, xyGoodsId, triggers, null);

        XianyuKeywordReplyRule rule = new XianyuKeywordReplyRule();
        rule.setXianyuAccountId(accountId);
        rule.setXyGoodsId(xyGoodsId);
        rule.setKeyword(triggers.get(0));
        rule.setKeywords(triggerMatcher.serialize(triggers));
        rule.setMatchMode(normalizeMatchMode(matchMode));
        rule.setIsFallback(0);
        ruleMapper.insert(rule);
        return toRuleBO(rule, Collections.emptyList());
    }

    @Override
    public void deleteRule(Long ruleId) {
        contentMapper.deleteByRuleId(ruleId);
        ruleMapper.deleteById(ruleId);
    }

    @Override
    public void updateKeyword(Long ruleId, String keyword) {
        XianyuKeywordReplyRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            throw new RuntimeException("规则不存在: id=" + ruleId);
        }
        List<String> triggers = requireTriggers(keyword);
        ensureNoDuplicateTriggers(rule.getXianyuAccountId(), rule.getXyGoodsId(), triggers, ruleId);
        rule.setKeyword(triggers.get(0));
        rule.setKeywords(triggerMatcher.serialize(triggers));
        ruleMapper.updateById(rule);
    }

    @Override
    public void updateMatchMode(Long ruleId, Integer matchMode) {
        XianyuKeywordReplyRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            throw new RuntimeException("规则不存在: id=" + ruleId);
        }
        rule.setMatchMode(normalizeMatchMode(matchMode));
        ruleMapper.updateById(rule);
    }

    @Override
    public KeywordReplyRuleBO ensureFallbackRule(Long accountId, String xyGoodsId) {
        XianyuKeywordReplyRule existing = ruleMapper.selectFallback(accountId, xyGoodsId);
        if (existing != null) {
            List<XianyuKeywordReplyContent> contents = contentMapper.selectByRuleId(existing.getId());
            return toRuleBO(existing, contents != null ? contents : Collections.emptyList());
        }

        XianyuKeywordReplyRule rule = new XianyuKeywordReplyRule();
        rule.setXianyuAccountId(accountId);
        rule.setXyGoodsId(xyGoodsId);
        rule.setKeyword("__fallback__");
        rule.setKeywords("__fallback__");
        rule.setMatchMode(1);
        rule.setIsFallback(1);
        ruleMapper.insert(rule);

        KeywordReplyRuleBO bo = new KeywordReplyRuleBO();
        bo.setId(rule.getId());
        bo.setXianyuAccountId(accountId);
        bo.setXyGoodsId(xyGoodsId);
        bo.setKeyword("__fallback__");
        bo.setMatchMode(1);
        bo.setIsFallback(1);
        bo.setContents(Collections.emptyList());
        return bo;
    }

    @Override
    public KeywordReplyRuleBO.KeywordReplyContentBO addContent(Long ruleId, String replyText, String replyImageUrl) {
        XianyuKeywordReplyContent content = new XianyuKeywordReplyContent();
        content.setRuleId(ruleId);
        content.setReplyText(replyText);
        content.setReplyImageUrl(replyImageUrl);
        contentMapper.insert(content);

        KeywordReplyRuleBO.KeywordReplyContentBO bo = new KeywordReplyRuleBO.KeywordReplyContentBO();
        bo.setId(content.getId());
        bo.setRuleId(ruleId);
        bo.setReplyText(replyText);
        bo.setReplyImageUrl(replyImageUrl);
        return bo;
    }

    @Override
    public void updateContent(Long contentId, String replyText, String replyImageUrl) {
        XianyuKeywordReplyContent content = contentMapper.selectById(contentId);
        if (content == null) {
            throw new RuntimeException("回复内容不存在: id=" + contentId);
        }
        content.setReplyText(replyText);
        content.setReplyImageUrl(replyImageUrl);
        contentMapper.updateById(content);
    }

    @Override
    public void deleteContent(Long contentId) {
        contentMapper.deleteById(contentId);
    }

    @Override
    public List<KeywordReplyRuleBO> matchKeyword(Long accountId, String xyGoodsId, String message) {
        if (message == null || message.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<XianyuKeywordReplyRule> allRules = ruleMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
        List<XianyuKeywordReplyRule> allMatches = allRules == null ? Collections.emptyList() : allRules.stream()
                .filter(rule -> !Integer.valueOf(1).equals(rule.getIsFallback()))
                .filter(rule -> triggerMatcher.matches(rule.getMatchMode(), message, triggerMatcher.triggersOf(rule)))
                .toList();

        if (!allMatches.isEmpty()) {
            Map<Long, List<XianyuKeywordReplyContent>> contentMap = loadContents(allMatches);
            return allMatches.stream()
                    .map(rule -> toRuleBO(rule, contentMap.getOrDefault(rule.getId(), Collections.emptyList())))
                    .collect(Collectors.toList());
        }

        XianyuKeywordReplyRule fallback = ruleMapper.selectFallback(accountId, xyGoodsId);
        if (fallback != null) {
            List<XianyuKeywordReplyContent> contents = contentMapper.selectByRuleId(fallback.getId());
            if (contents != null && !contents.isEmpty()) {
                return Collections.singletonList(toRuleBO(fallback, contents));
            }
        }

        return Collections.emptyList();
    }

    @Override
    public boolean isKeywordReplyEnabled(Long accountId, String xyGoodsId) {
        if (accountId == null || xyGoodsId == null) {
            return false;
        }
        try {
            XianyuGoodsConfig config = goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
            return config != null && config.getXianyuKeywordReplyOn() != null && config.getXianyuKeywordReplyOn() == 1;
        } catch (Exception e) {
            log.error("检查关键词回复开关异常: accountId={}, xyGoodsId={}", accountId, xyGoodsId, e);
            return false;
        }
    }

    private KeywordReplyRuleBO toRuleBO(XianyuKeywordReplyRule rule, List<XianyuKeywordReplyContent> contents) {
        KeywordReplyRuleBO bo = new KeywordReplyRuleBO();
        bo.setId(rule.getId());
        bo.setXianyuAccountId(rule.getXianyuAccountId());
        bo.setXyGoodsId(rule.getXyGoodsId());
        bo.setKeyword(rule.getKeyword());
        bo.setKeywords(triggerMatcher.triggersOf(rule));
        bo.setMatchMode(rule.getMatchMode());
        bo.setIsFallback(rule.getIsFallback());
        bo.setContents(contents.stream().map(c -> {
            KeywordReplyRuleBO.KeywordReplyContentBO cbo = new KeywordReplyRuleBO.KeywordReplyContentBO();
            cbo.setId(c.getId());
            cbo.setRuleId(c.getRuleId());
            cbo.setReplyText(c.getReplyText());
            cbo.setReplyImageUrl(c.getReplyImageUrl());
            return cbo;
        }).collect(Collectors.toList()));
        return bo;
    }

    private Map<Long, List<XianyuKeywordReplyContent>> loadContents(List<XianyuKeywordReplyRule> rules) {
        List<Long> ruleIds = rules.stream().map(XianyuKeywordReplyRule::getId).toList();
        if (ruleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<XianyuKeywordReplyContent> contents = contentMapper.selectByRuleIds(ruleIds);
        if (contents == null || contents.isEmpty()) {
            return Collections.emptyMap();
        }
        return contents.stream().collect(Collectors.groupingBy(XianyuKeywordReplyContent::getRuleId));
    }

    private List<String> requireTriggers(String input) {
        List<String> triggers = triggerMatcher.normalize(input);
        if (triggers.isEmpty()) throw new IllegalArgumentException("请至少填写一个关键词");
        return triggers;
    }

    private int normalizeMatchMode(Integer matchMode) {
        return matchMode != null && matchMode >= 0 && matchMode <= 2 ? matchMode : 0;
    }

    private void ensureNoDuplicateTriggers(Long accountId, String goodsId, List<String> requested, Long excludedRuleId) {
        Set<String> requestedKeys = requested.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<XianyuKeywordReplyRule> existingRules = ruleMapper.selectByAccountAndGoodsId(accountId, goodsId);
        if (existingRules == null) return;
        for (XianyuKeywordReplyRule existing : existingRules) {
            if (Integer.valueOf(1).equals(existing.getIsFallback()) || Objects.equals(existing.getId(), excludedRuleId)) continue;
            for (String existingTrigger : triggerMatcher.triggersOf(existing)) {
                if (requestedKeys.contains(existingTrigger.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("关键词「" + existingTrigger + "」已存在于其他规则中");
                }
            }
        }
    }
}
