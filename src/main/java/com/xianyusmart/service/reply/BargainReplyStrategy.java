package com.xianyusmart.service.reply;

import com.xianyusmart.event.chatMessageEvent.ChatMessageData;
import com.xianyusmart.service.AIService;
import com.xianyusmart.service.bargain.BargainDecision;
import com.xianyusmart.service.bargain.BargainDecisionService;
import com.xianyusmart.service.bargain.BargainReplyGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class BargainReplyStrategy implements ReplyStrategy {

    public static final int REPLY_TYPE_BARGAIN = 4;

    private final BargainDecisionService decisionService;
    private final BargainReplyGuard replyGuard;
    private final AIService aiService;

    public BargainReplyStrategy(BargainDecisionService decisionService,
                                BargainReplyGuard replyGuard,
                                AIService aiService) {
        this.decisionService = decisionService;
        this.replyGuard = replyGuard;
        this.aiService = aiService;
    }

    @Override
    public ReplyResult execute(List<ChatMessageData> messageList) {
        ChatMessageData last = messageList.get(messageList.size() - 1);
        if (last.getSenderUserId() == null || last.getSenderUserId().isBlank()) return ReplyResult.fail();
        String buyerMessage = messageList.stream()
                .map(ChatMessageData::getMsgContent)
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        try {
            BargainDecision decision = decisionService.nextDecision(
                    last.getXianyuAccountId(), last.getXyGoodsId(), last.getSenderUserId(),
                    last.getSId(), buyerMessage);
            if (decision == null) return ReplyResult.fail();

            String reply = generateReply(buyerMessage, decision);
            if (!replyGuard.isSafe(reply, decision.getFloorPrice(), decision.getListPrice(), decision.getOfferPrice())) {
                log.warn("AI 议价回复未通过价格安全校验，使用程序兜底: accountId={}, goodsId={}, offer={}",
                        last.getXianyuAccountId(), last.getXyGoodsId(), decision.getOfferPrice());
                reply = fallbackReply(decision);
            }
            return ReplyResult.of(Collections.singletonList(
                    ReplyResult.ReplyItem.text(reply, REPLY_TYPE_BARGAIN)));
        } catch (Exception error) {
            log.error("AI 议价策略执行失败: accountId={}, goodsId={}",
                    last.getXianyuAccountId(), last.getXyGoodsId(), error);
            return ReplyResult.fail();
        }
    }

    private String generateReply(String buyerMessage, BargainDecision decision) {
        if (decision.isFloorReached() && decision.getFloorReply() != null) {
            String configured = decision.getFloorReply()
                    .replace("{price}", formatPrice(decision.getOfferPrice()));
            if (replyGuard.isSafe(configured, decision.getFloorPrice(),
                    decision.getListPrice(), decision.getOfferPrice())) {
                return configured;
            }
        }

        String prompt = """
                你是闲鱼卖家的议价客服。请只输出一句简短、自然的中文回复，不要解释规则。
                本轮唯一允许给买家的价格是：%s 元。
                商品当前标价：%s 元。本轮是第 %d/%d 轮。
                议价风格：%s。买家提出的价格本轮是否可接受：%s。
                绝不能输出低于允许价格的金额，不能声称已经改价，不能让买家直接按较低价格下单。
                可以请买家确认需要，确认后由卖家人工处理价格。
                %s
                买家原话：%s
                """.formatted(
                formatPrice(decision.getOfferPrice()),
                formatPrice(decision.getListPrice()),
                decision.getRound(), decision.getMaxRounds(),
                styleText(decision.getStyle()),
                decision.isBuyerOfferAccepted() ? "是" : "否",
                decision.getInstructions() == null ? "" : "补充规则：" + decision.getInstructions(),
                buyerMessage == null ? "" : buyerMessage);
        return aiService.simpleChat(prompt);
    }

    private String fallbackReply(BargainDecision decision) {
        String price = formatPrice(decision.getOfferPrice());
        if (decision.isFloorReached()) {
            return "这个价格已经是目前能给到的最低了，可以按 " + price + " 元谈，您确定需要的话告诉我，我来处理价格。";
        }
        if (decision.isBuyerOfferAccepted()) {
            return "可以按 " + price + " 元谈，您确定需要的话告诉我，我来处理价格。";
        }
        return switch (decision.getStyle()) {
            case "FIRM" -> "价格空间确实不大，这次最多可以按 " + price + " 元谈，需要的话我来处理价格。";
            case "CLOSE" -> "诚心要的话这次可以给到 " + price + " 元，您确认需要我就来处理价格。";
            default -> "可以再给您优惠一点，这次按 " + price + " 元谈，您确认需要的话我来处理价格。";
        };
    }

    private String styleText(String style) {
        return switch (style) {
            case "FIRM" -> "坚定，强调价格空间有限";
            case "CLOSE" -> "积极成交，但不得突破价格规则";
            default -> "适中、友好、逐步让价";
        };
    }

    private String formatPrice(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
