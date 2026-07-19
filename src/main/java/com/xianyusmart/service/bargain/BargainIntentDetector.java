package com.xianyusmart.service.bargain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fast deterministic gate so normal product questions never enter the price engine. */
@Component
public class BargainIntentDetector {

    private static final Pattern BARGAIN_WORDS = Pattern.compile(
            "便宜|优惠|最低|少点|少一点|刀|砍价|议价|讲价|降价|打折|贵了|贵吗|价格.*(?:低|少)|能.*(?:少|便宜)|多少出|包邮");
    private static final Pattern PROPOSED_PRICE = Pattern.compile(
            "(?<!\\d)(\\d{1,8}(?:\\.\\d{1,2})?)\\s*(?:元|块|可以|行吗|卖吗|出吗)");
    private static final Pattern STANDALONE_PRICE_OFFER = Pattern.compile(
            "(?<!\\d)\\d{1,8}(?:\\.\\d{1,2})?\\s*(?:元|块)?\\s*(?:可以吗?|行吗|卖吗|出吗)");

    public boolean isBargainMessage(String message) {
        if (message == null) return false;
        String value = message.trim();
        return BARGAIN_WORDS.matcher(value).find() || STANDALONE_PRICE_OFFER.matcher(value).find();
    }

    public Optional<BigDecimal> extractProposedPrice(String message) {
        if (!isBargainMessage(message)) return Optional.empty();
        Matcher matcher = PROPOSED_PRICE.matcher(message);
        if (!matcher.find()) return Optional.empty();
        try {
            BigDecimal value = new BigDecimal(matcher.group(1));
            return value.signum() > 0 ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
