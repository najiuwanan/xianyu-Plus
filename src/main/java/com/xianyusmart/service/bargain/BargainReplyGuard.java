package com.xianyusmart.service.bargain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Final programmatic barrier applied after the model responds. */
@Component
public class BargainReplyGuard {

    private static final Pattern MONEY = Pattern.compile("(?<!\\d)(\\d{1,8}(?:\\.\\d{1,2})?)\\s*(?:元|块)");
    private static final Pattern FALSE_PRICE_CHANGE = Pattern.compile("已改价|已经改价|改好价|直接拍|直接下单");

    public boolean isSafe(String reply, BigDecimal floor, BigDecimal listPrice, BigDecimal offer) {
        if (reply == null || reply.isBlank() || reply.length() > 240 || FALSE_PRICE_CHANGE.matcher(reply).find()) {
            return false;
        }
        Matcher matcher = MONEY.matcher(reply);
        while (matcher.find()) {
            BigDecimal amount;
            try {
                amount = new BigDecimal(matcher.group(1));
            } catch (NumberFormatException error) {
                return false;
            }
            if (amount.compareTo(floor) < 0) return false;
            if (amount.compareTo(offer) != 0 && amount.compareTo(listPrice) != 0) return false;
        }
        return true;
    }
}
