package com.xianyusmart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("xianyu_product_material")
public class XianyuProductMaterial {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String materialName;
    private String title;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer quantity;
    private String deliveryMode;
    private BigDecimal postFee;
    private String imagesJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
