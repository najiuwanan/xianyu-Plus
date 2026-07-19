package com.xianyusmart.service.delivery;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/** Renders card-library delivery templates and splits explicitly separated messages. */
@Component
public class DeliveryMessageTemplateRenderer {

    public static final String MESSAGE_SEPARATOR = "######";

    public String render(String template, String deliveryContent, DeliveryContext context) {
        String content = safe(deliveryContent);
        if (template == null || template.isBlank()) {
            return content;
        }
        return template
                .replace("{order_id}", safe(context.getOrderId()))
                .replace("{item_id}", safe(context.getXyGoodsId()))
                .replace("{item_title}", safe(context.getGoodsTitle()))
                .replace("{buyer_name}", safe(context.getBuyerUserName()))
                .replace("{buyer_id}", safe(context.getBuyerUserId()))
                .replace("{seller_name}", safe(context.getSellerName()))
                .replace("{sku_name}", safe(context.getSkuName()))
                .replace("{DELIVERY_CONTENT}", content)
                .replace("{kmKey}", content);
    }

    public List<String> splitMessages(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        return Arrays.stream(content.split("\\s*" + MESSAGE_SEPARATOR + "\\s*"))
                .map(String::trim)
                .filter(message -> !message.isEmpty())
                .toList();
    }

    public String joinForSingleMessageChannel(String content) {
        return String.join("\n", splitMessages(content));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
