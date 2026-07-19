package com.xianyusmart.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A buyer's isolated bargain progress for one account and one product. */
@Data
public class XianyuAiBargainSession {
    private Long id;
    private Long xianyuAccountId;
    private String xyGoodsId;
    private String buyerUserId;
    private String sId;
    private BigDecimal currentOffer;
    private Integer bargainRound;
    private Integer reachedFloor;
    private String lastBuyerMessage;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
