package com.xianyusmart.controller.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 商品发布能力只读检测结果；不会实际创建商品。 */
@Data
public class PublishCapabilityCheckRespDTO {
    private boolean passed;
    private String status;
    private String summary;
    private String detail;
    private String categoryId;
    private String categoryName;
    private String channelCategoryId;
    private String taobaoCategoryId;
    private boolean categoryApiReady;
    private boolean dynamicPropertiesReady;
    private boolean locationApiReady;
    private boolean realPublishTested;
    private int propertyCount;
    private List<Property> properties = new ArrayList<>();

    @Data
    public static class Property {
        private String propertyId;
        private String propertyName;
        private int optionCount;
        private List<String> optionExamples = new ArrayList<>();
    }
}
