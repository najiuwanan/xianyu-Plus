package com.xianyusmart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 操作记录实体
 */
@Data
@TableName("xianyu_operation_log")
public class XianyuOperationLog {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 账号ID
     */
    private Long xianyuAccountId;
    
    /**
     * 操作类型
     */
    private String operationType;
    
    /**
     * 操作模块
     */
    private String operationModule;
    
    /**
     * 操作描述
     */
    private String operationDesc;
    
    /**
     * 操作状态：1-成功，0-失败，2-部分成功
     */
    private Integer operationStatus;
    
    /**
     * 目标类型
     */
    private String targetType;
    
    /**
     * 目标ID
     */
    private String targetId;
    
    /**
     * 请求参数（JSON格式）
     */
    private String requestParams;
    
    /**
     * 响应结果（JSON格式）
     */
    private String responseResult;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * IP地址
     */
    private String ipAddress;
    
    /**
     * 浏览器UA
     */
    private String userAgent;
    
    /**
     * 操作耗时（毫秒）
     */
    private Integer durationMs;
    
    /**
     * 创建时间（时间戳，毫秒）
     */
    private Long createTime;
}
