package com.xianyusmart.controller.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 商品管理中的批量配置请求。null 表示对应项目保持不变。 */
@Data
public class BatchUpdateGoodsConfigReqDTO {

    @NotNull(message = "账号不能为空")
    private Long xianyuAccountId;

    @NotEmpty(message = "请至少选择一个商品")
    private List<String> xyGoodsIds = new ArrayList<>();

    /** 0=关闭，1=开启，null=不修改。 */
    private Integer xianyuAutoDeliveryOn;

    /** 0=关闭，1=开启，null=不修改。 */
    private Integer xianyuAutoReplyOn;

    /**
     * 指定后将所选商品的默认发货来源改为该卡券；同时自动开启自动发货。
     * null 表示不修改卡券关联。
     */
    private Long kamiConfigId;
}
