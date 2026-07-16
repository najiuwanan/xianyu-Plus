package com.xianyusmart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 单个商品的擦亮执行记录。 */
@Data
@TableName("xianyu_item_polish_record")
public class XianyuItemPolishRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long xianyuAccountId;
    private String xyGoodsId;
    private String goodsTitle;
    private String triggerType;
    private Integer success;
    private String message;
    private LocalDateTime createTime;
}
