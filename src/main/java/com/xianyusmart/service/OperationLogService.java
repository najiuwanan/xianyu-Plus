package com.xianyusmart.service;

import com.xianyusmart.entity.XianyuOperationLog;

import java.util.List;
import java.util.Map;

/**
 * 操作记录服务
 */
public interface OperationLogService {
    
    /**
     * 记录操作日志
     */
    void log(XianyuOperationLog operationLog);
    
    /**
     * 记录操作日志（简化版）
     */
    void log(Long accountId, String operationType, String operationDesc, Integer status);
    
    /**
     * 记录操作日志（完整版）
     */
    void log(Long accountId, String operationType, String operationModule, 
             String operationDesc, Integer status, String targetType, String targetId,
             String requestParams, String responseResult, String errorMessage, Integer durationMs);
    
    /**
     * 分页查询操作记录
     */
    Map<String, Object> queryLogs(Long accountId, String operationType, String operationModule,
                                   Integer operationStatus, Integer page, Integer pageSize);
    
    /**
     * 删除指定天数之前的日志
     */
    int deleteOldLogs(int days);

    /**
     * 清空指定账号下的全部操作记录。
     */
    int clearLogs(Long accountId);
}
