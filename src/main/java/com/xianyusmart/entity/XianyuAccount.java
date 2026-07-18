package com.xianyusmart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;



/**
 * 闲鱼账号实体类
 */
@Data
@TableName("xianyu_account")
public class XianyuAccount {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 闲鱼账号备注
     */
    private String accountNote;
    
    /**
     * UNB标识
     */
    private String unb;

    /** 闲鱼账号头像地址；无法获取时前端使用文字头像兜底 */
    private String avatarUrl;

    /** 账号级自动擦亮状态，仅用于账号列表展示，不写入 xianyu_account 表。 */
    @TableField(exist = false)
    private Integer itemPolishEnabled;

    /** 自动擦亮每日计划时间，仅用于账号列表展示。 */
    @TableField(exist = false)
    private String itemPolishScheduleTime;
    
    /**
     * 设备ID（UUID格式-用户ID，用于WebSocket连接）
     * 格式: XXXXXXXX-XXXX-4XXX-XXXX-XXXXXXXXXXXX-用户ID
     * 例如: ED4CBA2C-5DA0-4154-A902-BF5CB52409E2-3888777108
     */
    private String deviceId;
    
    /**
     * 账号状态：1-正常，0-手动禁用，-1/-2-需要处理连接或验证。
     */
    private Integer status;

    /**
     * 是否开启自动评价买家：0-关闭，1-开启
     */
    private Integer autoRateEnabled;

    /**
     * 自动评价默认文案
     */
    private String autoRateText;
    
    /**
     * 发货后是否求小红花 1:是 0:否
     */
    private Integer autoAskFlower;

    /**
     * 求小红花文案
     */
    private String autoAskFlowerText;

    /** 服务器或容器启动后是否自动恢复该账号的实时连接。 */
    private Integer autoConnectOnStartup;

    /** 连续自动化失败后由系统暂停；不影响账号的实时连接状态。 */
    private Integer automationRiskPaused;

    /** 本次自动化保护暂停的原因，供账号管理页面展示。 */
    private String automationRiskPauseReason;

    private java.time.LocalDateTime automationRiskPausedAt;

    
    /**
     * 创建时间（SQLite存储为TEXT）
     */
    private String createdTime;
    
    /**
     * 更新时间（SQLite存储为TEXT）
     */
    private String updatedTime;
}
