package com.xianyusmart.service.bo;

import lombok.Data;

/**
 * 修改密码请求BO
 * @date 2026/4/22
 */
@Data
public class ChangePasswordReqBO {
    private Long userId;
    private String oldPassword;
    private String newPassword;
}
