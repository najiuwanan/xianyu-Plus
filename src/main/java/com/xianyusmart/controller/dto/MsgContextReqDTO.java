package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 查询上下文消息请求DTO
 */
@Data
public class MsgContextReqDTO {

    /** 账号 ID；新客服界面传入，用于隔离多账号的同名会话。 */
    private Long xianyuAccountId;
    
    /**
     * 会话ID
     */
    private String sid;
    
    /**
     * 限制条数（默认20）
     */
    private Integer limit;
    
    /**
     * 偏移量（默认0）
     */
    private Integer offset;
}
