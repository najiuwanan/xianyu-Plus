package com.xianyusmart.service.delivery;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderDetailFetcherTest {

    @Test
    void parsesGoodsIdentifierFromMerchantItemDetails() throws Exception {
        OrderDetailFetcher fetcher = new OrderDetailFetcher();
        OrderDetailFetcher.OrderDetailInfo detail = new OrderDetailFetcher.OrderDetailInfo();
        Map<String, Object> module = Map.of(
                "merchantItemVO", Map.of(
                        "itemId", 123456789L,
                        "title", "测试商品"
                )
        );

        Method parser = OrderDetailFetcher.class.getDeclaredMethod(
                "parseItemInfo", Map.class, OrderDetailFetcher.OrderDetailInfo.class);
        parser.setAccessible(true);
        parser.invoke(fetcher, module, detail);

        assertEquals("123456789", detail.xyGoodsId);
        assertEquals("测试商品", detail.goodsTitle);
    }
}
