package com.xianyusmart.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部卡券接口的订单级领取缓存。
 *
 * <p>一旦外部接口已成功返回卡密，后续消息重试必须复用该内容，不能再次向供应商取卡。</p>
 */
@Data
@TableName("xianyu_api_kami_delivery")
public class XianyuApiKamiDelivery {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kamiConfigId;

    private Long xianyuAccountId;

    private String orderId;

    private String deliveryContent;

    /** 0请求中，1已获取，2请求失败。 */
    private Integer state;

    private String errorMessage;

    private LocalDateTime requestTime;

    private LocalDateTime responseTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
