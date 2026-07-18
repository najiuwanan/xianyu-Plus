package com.xianyusmart.entity.bo;

import lombok.Data;

import java.util.List;

/**
 * 自动回复触发上下文，保存本次触发的消息、固定资料和商品详情。
 */
@Data
public class AutoReplyTriggerContext {

    private List<TriggerMessage> triggerMessages;
    private String contextMessages;
    private String fixedMaterial;
    private String goodsDetail;

    @Data
    public static class TriggerMessage {
        private String pnmId;
        private String senderUserId;
        private String senderUserName;
        private String msgContent;
        private Long messageTime;
    }
}
