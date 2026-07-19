package com.xianyusmart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xianyusmart.controller.dto.BuyerBlacklistReqDTO;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuBuyerBlacklist;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuBuyerBlacklistMapper;
import com.xianyusmart.service.BuyerBlacklistService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class BuyerBlacklistServiceImpl implements BuyerBlacklistService {

    private final XianyuBuyerBlacklistMapper blacklistMapper;
    private final XianyuAccountMapper accountMapper;

    public BuyerBlacklistServiceImpl(XianyuBuyerBlacklistMapper blacklistMapper,
                                     XianyuAccountMapper accountMapper) {
        this.blacklistMapper = blacklistMapper;
        this.accountMapper = accountMapper;
    }

    @Override
    public boolean isBlacklisted(Long accountId, String buyerUserId) {
        return accountId != null && StringUtils.hasText(buyerUserId)
                && blacklistMapper.countActive(accountId, buyerUserId.trim()) > 0;
    }

    @Override
    public XianyuBuyerBlacklist findActive(Long accountId, String buyerUserId) {
        if (accountId == null || !StringUtils.hasText(buyerUserId)) return null;
        return blacklistMapper.findActive(accountId, buyerUserId.trim());
    }

    @Override
    public List<XianyuBuyerBlacklist> list(Long accountId, String keyword) {
        return blacklistMapper.findAll(accountId, StringUtils.hasText(keyword) ? keyword.trim() : null);
    }

    @Override
    @Transactional
    public XianyuBuyerBlacklist save(BuyerBlacklistReqDTO request) {
        if (request == null || !StringUtils.hasText(request.getBuyerUserId())) {
            throw new IllegalArgumentException("买家 ID 不能为空");
        }
        if (request.getXianyuAccountId() != null) {
            XianyuAccount account = accountMapper.selectById(request.getXianyuAccountId());
            if (account == null) throw new IllegalArgumentException("指定的闲鱼账号不存在");
        }
        String buyerId = request.getBuyerUserId().trim();
        LambdaQueryWrapper<XianyuBuyerBlacklist> existingQuery = new LambdaQueryWrapper<XianyuBuyerBlacklist>()
                .eq(XianyuBuyerBlacklist::getBuyerUserId, buyerId);
        if (request.getXianyuAccountId() == null) {
            existingQuery.isNull(XianyuBuyerBlacklist::getXianyuAccountId);
        } else {
            existingQuery.eq(XianyuBuyerBlacklist::getXianyuAccountId, request.getXianyuAccountId());
        }
        XianyuBuyerBlacklist existing = blacklistMapper.selectOne(existingQuery);
        XianyuBuyerBlacklist entity = existing != null ? existing : new XianyuBuyerBlacklist();
        entity.setXianyuAccountId(request.getXianyuAccountId());
        entity.setBuyerUserId(buyerId);
        entity.setBuyerUserName(trimToNull(request.getBuyerUserName(), 200));
        entity.setReason(trimToNull(request.getReason(), 500));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled() != 0 ? 1 : 0);
        if (existing == null) blacklistMapper.insert(entity); else blacklistMapper.updateById(entity);
        return blacklistMapper.selectById(entity.getId());
    }

    @Override
    public void delete(Long id) {
        if (id == null || blacklistMapper.deleteById(id) == 0) {
            throw new IllegalArgumentException("黑名单记录不存在");
        }
    }

    @Override
    public String blockedMessage(Long accountId, String buyerUserId) {
        XianyuBuyerBlacklist entry = findActive(accountId, buyerUserId);
        if (entry == null) return null;
        return StringUtils.hasText(entry.getReason())
                ? "该买家已被黑名单拦截：" + entry.getReason()
                : "该买家已被黑名单拦截";
    }

    private String trimToNull(String value, int maxLength) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(trimmed.length(), maxLength));
    }
}
