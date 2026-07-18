package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * GitHub 更新检查结果。
 */
@Data
public class SystemUpdateStatusRespDTO {

    /** 是否可用当前镜像中的提交标识与远端比较。 */
    private boolean versionTracked;

    /** 是否发现可以通过 update.sh 获取的新提交。 */
    private boolean updateAvailable;

    /** 面向界面的简短状态说明。 */
    private String message;

    private String currentCommit;
    private String latestCommit;
    private String latestMessage;
    private String updateUrl;
    private String checkedAt;
}
