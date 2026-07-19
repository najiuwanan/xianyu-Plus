package com.xianyusmart.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 单账号商品发布请求；服务端会重新校验类目和属性。 */
@Data
public class ProductPublishReqDTO {
    private Long accountId;
    private String requestId;
    private String title;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer quantity = 1;
    /** FREE、FLAT、NONE、SELF_PICKUP。 */
    private String deliveryMode;
    private BigDecimal postFee;
    private boolean acknowledged;
    private String confirmation;
    private Address address;
    private List<Image> images = new ArrayList<>();
    private List<PropertySelection> properties = new ArrayList<>();

    @Data
    public static class Image {
        private String url;
        private Integer width;
        private Integer height;
    }

    @Data
    public static class PropertySelection {
        private String propertyId;
        /** 对应 valueId；接口没有 valueId 时使用选项名称。 */
        private String valueKey;
    }

    @Data
    public static class Address {
        /** 服务端返回的地点 key，防止直接伪造行政区和坐标。 */
        private String locationKey;
        /** 使用浏览器当前位置查询候选地点时携带。 */
        private Double lookupLongitude;
        private Double lookupLatitude;
        /** 可自定义页面显示的地点名称，行政区和坐标仍使用平台候选地点。 */
        private String customPoiName;
    }
}
