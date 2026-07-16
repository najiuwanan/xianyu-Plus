package com.xianyusmart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 自动擦亮的账号级配置。 */
@Data
@TableName("xianyu_item_polish_config")
public class XianyuItemPolishConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long xianyuAccountId;
    private Integer enabled;
    private String scheduleTime;
    private LocalDate lastScheduledDate;
    private LocalDateTime lastRunAt;
    private Integer lastRunTotal;
    private Integer lastRunSuccess;
    private Integer lastRunFailed;
    private String lastRunMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
