package com.xianyusmart.service.bargain;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BargainDecision {
    private BigDecimal listPrice;
    private BigDecimal floorPrice;
    private BigDecimal offerPrice;
    private int round;
    private int maxRounds;
    private boolean floorReached;
    private boolean buyerOfferAccepted;
    private String style;
    private String floorReply;
    private String instructions;
}
