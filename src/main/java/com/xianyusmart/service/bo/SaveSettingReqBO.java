package com.xianyusmart.service.bo;

import lombok.Data;

/**
 * 保存配置请求BO
 * @date 2026/4/22
 */
@Data
public class SaveSettingReqBO {
    private String settingKey;
    private String settingValue;
    private String settingDesc;
}
