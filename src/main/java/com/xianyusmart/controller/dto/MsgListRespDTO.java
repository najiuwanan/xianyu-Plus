package com.xianyusmart.controller.dto;

import lombok.Data;

import java.util.List;

/**
 * 消息列表查询响应DTO
 */
@Data
public class MsgListRespDTO {
    
    /**
     * 消息列表
     */
    private List<MsgDTO> list;
    
    /**
     * 消息总数
     */
    private Integer totalCount;
    
    /**
     * 当前页码
     */
    private Integer pageNum;
    
    /**
     * 每页数量
     */
    private Integer pageSize;
    
    /**
     * 总页数
     */
    private Integer totalPage;
}

