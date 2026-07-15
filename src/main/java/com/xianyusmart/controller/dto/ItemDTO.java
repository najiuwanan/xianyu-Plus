package com.xianyusmart.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 闲鱼商品信息DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemDTO {
    
    /**
     * 商品ID
     */
    private String id;
    
    /**
     * 商品标题
     */
    private String title;
    
    /**
     * 拍卖类型（b=一口价）
     */
    private String auctionType;
    
    /**
     * 类目ID
     */
    private Long categoryId;
    
    /**
     * 商品状态（0=在售）
     */
    private Integer itemStatus;
    
    /**
     * 详情页URL
     */
    private String detailUrl;
    
    /**
     * 价格信息
     */
    private PriceInfo priceInfo;
    
    /**
     * 图片信息
     */
    private PicInfo picInfo;
    
    /**
     * 详情参数
     */
    private DetailParams detailParams;
    
    /**
     * 商品标签数据
     */
    private ItemLabelDataVO itemLabelDataVO;
    
    /**
     * 追踪参数
     */
    private TrackParams trackParams;
    
    /**
     * 价格信息
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PriceInfo {
        /**
         * 价格前缀（如：¥）
         */
        private String preText;
        
        /**
         * 价格
         */
        private String price;
    }
    
    /**
     * 图片信息
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PicInfo {
        /**
         * 图片URL
         */
        private String picUrl;
        
        /**
         * 图片宽度
         */
        private Integer width;
        
        /**
         * 图片高度
         */
        private Integer height;
        
        /**
         * 是否有视频
         */
        private Boolean hasVideo;
    }
    
    /**
     * 详情参数
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DetailParams {
        /**
         * 商品ID
         */
        private String itemId;
        
        /**
         * 标题
         */
        private String title;
        
        /**
         * 图片URL
         */
        private String picUrl;
        
        /**
         * 图片宽度
         */
        private String picWidth;
        
        /**
         * 图片高度
         */
        private String picHeight;
        
        /**
         * 售价
         */
        private String soldPrice;
        
        /**
         * 是否为视频
         */
        private String isVideo;
        
        /**
         * 图片信息JSON字符串
         */
        private String imageInfos;
    }
    
    /**
     * 商品标签数据
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ItemLabelDataVO {
        /**
         * 标签数据
         */
        private LabelData labelData;
        
        /**
         * 服务UT参数
         */
        private String serviceUtParams;
        
        /**
         * 追踪参数
         */
        private TrackParams trackParams;
        
        /**
         * 不显示的标签UT参数
         */
        private String unShowLabelUtParams;
    }
    
    /**
     * 标签数据
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LabelData {
        /**
         * R2区域标签（如：48小时内发布）
         */
        private TagGroup r2;
        
        /**
         * R3区域标签（如：粉丝更优惠、X人想要）
         */
        private TagGroup r3;
        
        /**
         * R4区域标签
         */
        private TagGroup r4;
    }
    
    /**
     * 标签组
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TagGroup {
        /**
         * 标签列表
         */
        private java.util.List<Tag> tagList;
        
        /**
         * 配置
         */
        private TagConfig config;
    }
    
    /**
     * 标签
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tag {
        /**
         * 标签数据
         */
        private TagData data;
        
        /**
         * UT参数
         */
        private UtParams utParams;
    }
    
    /**
     * 标签数据
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TagData {
        /**
         * 标签ID
         */
        private String labelId;
        
        /**
         * 标签类型（text=文本, gradientImageText=渐变图文）
         */
        private String type;
        
        /**
         * 标签内容
         */
        private String content;
        
        /**
         * 文字颜色
         */
        private String color;
        
        /**
         * 字体大小
         */
        private String size;
        
        /**
         * 行高
         */
        private String lineHeight;
        
        /**
         * 左边距
         */
        private String marginLeft;
        
        /**
         * 右边距
         */
        private String marginRight;
        
        /**
         * 是否加粗
         */
        private String bold;
        
        /**
         * 是否删除线
         */
        private String isStrikeThrough;
        
        /**
         * 边框颜色
         */
        private String borderColor;
        
        /**
         * 边框圆角
         */
        private String borderRadius;
        
        /**
         * 渐变方向
         */
        private String gradientDirection;
        
        /**
         * 渐变类型
         */
        private String gradientType;
        
        /**
         * 渐变颜色数组
         */
        private java.util.List<String> gradientColors;
        
        /**
         * 左侧图片
         */
        private TagImage leftImage;
        
        /**
         * 右侧图片
         */
        private TagImage rightImage;
        
        /**
         * 标签高度
         */
        private String height;
    }
    
    /**
     * 标签图片
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TagImage {
        /**
         * 图片URL
         */
        private String url;
        
        /**
         * 图片宽度
         */
        private String width;
        
        /**
         * 图片高度
         */
        private String height;
        
        /**
         * 左边距
         */
        private String marginLeft;
        
        /**
         * 右边距
         */
        private String marginRight;
    }
    
    /**
     * 标签配置
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TagConfig {
        /**
         * 是否互斥标签业务组
         */
        private String mutualLabelBizGroup;
    }
    
    /**
     * UT参数
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UtParams {
        /**
         * 参数1
         */
        private String arg1;
        
        /**
         * 参数映射
         */
        private java.util.Map<String, String> args;
    }
    
    /**
     * 追踪参数
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrackParams {
        /**
         * 是否店铺用户
         */
        private String isShopUser;
        
        /**
         * XYH桶信息
         */
        private String XyhBucketInfo;
        
        /**
         * XYH桶ID
         */
        private String XyhBucketId;
        
        /**
         * 标签桶ID
         */
        private String labelBucketId;
        
        /**
         * 服务UT参数
         */
        private String serviceUtParams;
        
        /**
         * 优惠券使用类型
         */
        private String couponUsageType;
    }
}
