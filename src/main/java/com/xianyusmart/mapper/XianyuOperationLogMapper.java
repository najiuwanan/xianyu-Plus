package com.xianyusmart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianyusmart.entity.XianyuOperationLog;
import com.xianyusmart.controller.dto.DashboardActivityDTO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 操作记录 Mapper
 */
@Mapper
public interface XianyuOperationLogMapper extends BaseMapper<XianyuOperationLog> {
    
    /**
     * 分页查询操作记录
     */
    @Select("<script>" +
            "SELECT * FROM xianyu_operation_log " +
            "WHERE xianyu_account_id = #{accountId} " +
            "<if test='operationType != null and operationType != \"\"'>" +
            "  AND operation_type = #{operationType} " +
            "</if>" +
            "<if test='operationModule != null and operationModule != \"\"'>" +
            "  AND operation_module = #{operationModule} " +
            "</if>" +
            "<if test='operationStatus != null'>" +
            "  AND operation_status = #{operationStatus} " +
            "</if>" +
            "ORDER BY create_time DESC " +
            "LIMIT #{pageSize} OFFSET #{offset}" +
            "</script>")
    List<XianyuOperationLog> selectByPage(
            @Param("accountId") Long accountId,
            @Param("operationType") String operationType,
            @Param("operationModule") String operationModule,
            @Param("operationStatus") Integer operationStatus,
            @Param("pageSize") Integer pageSize,
            @Param("offset") Integer offset
    );
    
    /**
     * 统计操作记录数量
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM xianyu_operation_log " +
            "WHERE xianyu_account_id = #{accountId} " +
            "<if test='operationType != null and operationType != \"\"'>" +
            "  AND operation_type = #{operationType} " +
            "</if>" +
            "<if test='operationModule != null and operationModule != \"\"'>" +
            "  AND operation_module = #{operationModule} " +
            "</if>" +
            "<if test='operationStatus != null'>" +
            "  AND operation_status = #{operationStatus} " +
            "</if>" +
            "</script>")
    Integer countByCondition(
            @Param("accountId") Long accountId,
            @Param("operationType") String operationType,
            @Param("operationModule") String operationModule,
            @Param("operationStatus") Integer operationStatus
    );

    /** 仪表盘最近动态，不暴露日志中的请求参数与敏感内容。 */
    @Select("SELECT l.operation_module AS module, " +
            "COALESCE(NULLIF(l.operation_desc, ''), '系统操作') AS content, " +
            "l.operation_status AS status, l.create_time AS created_at, " +
            "COALESCE(NULLIF(a.account_note, ''), a.unb, '系统') AS account_name " +
            "FROM xianyu_operation_log l " +
            "LEFT JOIN xianyu_account a ON a.id = l.xianyu_account_id " +
            "ORDER BY l.create_time DESC LIMIT #{limit}")
    List<DashboardActivityDTO> findRecentActivities(@Param("limit") int limit);
    
    /**
     * 根据账号ID删除操作记录
     */
    @Delete("DELETE FROM xianyu_operation_log WHERE xianyu_account_id = #{accountId}")
    int deleteByAccountId(@Param("accountId") Long accountId);
}
