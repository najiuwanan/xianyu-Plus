package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 自动回复配置响应DTO
 * @date 2026/4/22
 */
@Data
public class RagAutoReplyConfigRespDTO {
    /** 回复延时秒数 */
    private Integer ragDelaySeconds;
}
