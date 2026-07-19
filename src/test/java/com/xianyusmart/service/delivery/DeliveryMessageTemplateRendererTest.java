package com.xianyusmart.service.delivery;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeliveryMessageTemplateRendererTest {

    private final DeliveryMessageTemplateRenderer renderer = new DeliveryMessageTemplateRenderer();

    @Test
    void rendersDeliveryAndOrderVariables() {
        DeliveryContext context = DeliveryContext.builder()
                .orderId("ORDER-1")
                .xyGoodsId("GOODS-1")
                .goodsTitle("测试商品")
                .buyerUserName("买家")
                .buyerUserId("BUYER-1")
                .sellerName("店铺")
                .skuName("红色")
                .build();

        String rendered = renderer.render(
                "{buyer_name}购买{item_title}：{DELIVERY_CONTENT}，订单{order_id}，卖家{seller_name}，规格{sku_name}",
                "CARD-123", context);

        assertEquals("买家购买测试商品：CARD-123，订单ORDER-1，卖家店铺，规格红色", rendered);
    }

    @Test
    void supportsLegacyPlaceholderAndMultipleMessages() {
        String rendered = renderer.render("说明######卡密：{kmKey}", "CARD-123", DeliveryContext.builder().build());

        assertEquals(List.of("说明", "卡密：CARD-123"), renderer.splitMessages(rendered));
        assertEquals("说明\n卡密：CARD-123", renderer.joinForSingleMessageChannel(rendered));
    }
}
