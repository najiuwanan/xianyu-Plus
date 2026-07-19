package com.xianyusmart.service.bargain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xianyusmart.entity.XianyuAiBargainSession;
import com.xianyusmart.entity.XianyuGoodsConfig;
import com.xianyusmart.entity.XianyuGoodsInfo;
import com.xianyusmart.mapper.XianyuAiBargainSessionMapper;
import com.xianyusmart.mapper.XianyuGoodsConfigMapper;
import com.xianyusmart.mapper.XianyuGoodsInfoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class BargainDecisionService {

    private static final int SESSION_RESET_HOURS = 24;

    private final XianyuGoodsConfigMapper configMapper;
    private final XianyuGoodsInfoMapper goodsInfoMapper;
    private final XianyuAiBargainSessionMapper sessionMapper;
    private final BargainIntentDetector intentDetector;

    public BargainDecisionService(XianyuGoodsConfigMapper configMapper,
                                  XianyuGoodsInfoMapper goodsInfoMapper,
                                  XianyuAiBargainSessionMapper sessionMapper,
                                  BargainIntentDetector intentDetector) {
        this.configMapper = configMapper;
        this.goodsInfoMapper = goodsInfoMapper;
        this.sessionMapper = sessionMapper;
        this.intentDetector = intentDetector;
    }

    public boolean shouldNegotiate(XianyuGoodsConfig config, String message) {
        return config != null
                && Integer.valueOf(1).equals(config.getAiBargainOn())
                && validPositive(config.getAiBargainFloorPrice())
                && validPositive(config.getAiBargainStepAmount())
                && intentDetector.isBargainMessage(message);
    }

    @Transactional(rollbackFor = Exception.class)
    public BargainDecision nextDecision(Long accountId, String goodsId, String buyerUserId,
                                        String sessionId, String buyerMessage) {
        XianyuGoodsConfig config = configMapper.selectByAccountAndGoodsId(accountId, goodsId);
        if (!shouldNegotiate(config, buyerMessage)) return null;

        XianyuGoodsInfo goods = goodsInfoMapper.selectOne(new LambdaQueryWrapper<XianyuGoodsInfo>()
                .eq(XianyuGoodsInfo::getXianyuAccountId, accountId)
                .eq(XianyuGoodsInfo::getXyGoodId, goodsId)
                .last("LIMIT 1"));
        BigDecimal listPrice = parsePrice(goods == null ? null : goods.getSoldPrice());
        BigDecimal floor = money(config.getAiBargainFloorPrice());
        BigDecimal step = money(config.getAiBargainStepAmount());
        if (!validPositive(listPrice) || floor.compareTo(listPrice) > 0) return null;

        int maxRounds = config.getAiBargainMaxRounds() == null
                ? 3 : Math.max(1, Math.min(10, config.getAiBargainMaxRounds()));
        XianyuAiBargainSession session = sessionMapper.selectForUpdate(accountId, goodsId, buyerUserId);
        boolean differentConversation = session != null && session.getSId() != null && sessionId != null
                && !session.getSId().equals(sessionId);
        boolean reset = session == null || differentConversation || session.getUpdatedTime() == null
                || session.getUpdatedTime().isBefore(LocalDateTime.now().minusHours(SESSION_RESET_HOURS));
        int previousRound = reset || session.getBargainRound() == null ? 0 : session.getBargainRound();
        BigDecimal storedOffer = reset || session.getCurrentOffer() == null
                ? listPrice : money(session.getCurrentOffer());
        BigDecimal previousOffer = storedOffer.min(listPrice).max(floor);

        int nextRound = Math.min(previousRound + 1, maxRounds);
        BigDecimal normalOffer = previousOffer.subtract(step).max(floor);
        Optional<BigDecimal> proposed = intentDetector.extractProposedPrice(buyerMessage).map(this::money);
        boolean acceptedProposal = proposed.filter(value -> value.compareTo(floor) >= 0)
                .filter(value -> value.compareTo(previousOffer) <= 0)
                .filter(value -> value.compareTo(normalOffer) >= 0)
                .isPresent();
        BigDecimal offer = acceptedProposal ? proposed.get() : normalOffer;
        boolean floorReached = offer.compareTo(floor) <= 0 || nextRound >= maxRounds;

        if (session == null) {
            session = new XianyuAiBargainSession();
            session.setXianyuAccountId(accountId);
            session.setXyGoodsId(goodsId);
            session.setBuyerUserId(buyerUserId);
            applySession(session, sessionId, buyerMessage, offer, nextRound, floorReached);
            sessionMapper.insert(session);
        } else {
            applySession(session, sessionId, buyerMessage, offer, nextRound, floorReached);
            sessionMapper.update(session);
        }

        BargainDecision decision = new BargainDecision();
        decision.setListPrice(listPrice);
        decision.setFloorPrice(floor);
        decision.setOfferPrice(offer);
        decision.setRound(nextRound);
        decision.setMaxRounds(maxRounds);
        decision.setFloorReached(floorReached);
        decision.setBuyerOfferAccepted(acceptedProposal);
        decision.setStyle(normalizeStyle(config.getAiBargainStyle()));
        decision.setFloorReply(trimToNull(config.getAiBargainFloorReply()));
        decision.setInstructions(trimToNull(config.getAiBargainInstructions()));
        return decision;
    }

    private void applySession(XianyuAiBargainSession session, String sessionId, String message,
                              BigDecimal offer, int round, boolean floorReached) {
        session.setSId(sessionId);
        session.setCurrentOffer(offer);
        session.setBargainRound(round);
        session.setReachedFloor(floorReached ? 1 : 0);
        session.setLastBuyerMessage(message == null ? null : message.substring(0, Math.min(500, message.length())));
    }

    private BigDecimal parsePrice(String value) {
        if (value == null) return null;
        String normalized = value.replaceAll("[^0-9.]", "");
        if (normalized.isBlank()) return null;
        try {
            return money(new BigDecimal(normalized));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean validPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private String normalizeStyle(String style) {
        return switch (style == null ? "" : style.trim().toUpperCase()) {
            case "FIRM", "CLOSE" -> style.trim().toUpperCase();
            default -> "BALANCED";
        };
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
