package com.xianyusmart.controller.dto;

import lombok.Data;
import java.util.List;

/**
 * 获取自动发货记录响应DTO
 */
@Data
public class AutoDeliveryRecordRespDTO {
    
    /**
     * 记录列表
     */
    private List<AutoDeliveryRecordDTO> records;
    
    /**
     * 总记录数
     */
    private Long total;
    
    /**
     * 当前页码
     */
    private Integer pageNum;
    
    /**
     * 每页数量
     */
    private Integer pageSize;
}
