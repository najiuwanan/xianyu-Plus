package com.xianyusmart.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("xianyu_kami_config")
public class XianyuKamiConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long xianyuAccountId;

    private String aliasName;

    /** 1本地库存卡券，2外部 API 卡券，3固定内容。 */
    private Integer sourceType = 1;

    /** 固定内容来源的发货文本，例如网盘链接、教程说明等。 */
    private String fixedContent;

    /** 卡券库统一发货消息模板；{DELIVERY_CONTENT} 会替换为实际卡密或来源内容。 */
    private String deliveryTemplate;

    /** 外部 API 卡券配置，仅 sourceType=2 时使用。 */
    private String apiUrl;

    private String apiMethod;

    private String apiHeaders;

    private String apiRequestTemplate;

    private String apiResultPath;

    private Integer apiTimeoutSeconds;

    private Integer alertEnabled;

    private Integer alertThresholdType;

    private Integer alertThresholdValue;

    private String alertEmail;

    private Integer totalCount;

    private Integer usedCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime = LocalDateTime.now();

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime = LocalDateTime.now();
}
