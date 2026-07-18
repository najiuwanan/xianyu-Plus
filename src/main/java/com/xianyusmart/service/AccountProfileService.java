package com.xianyusmart.service;

/**
 * 闲鱼账号公开资料服务。
 */
public interface AccountProfileService {

    /**
     * 使用当前账号的已登录状态获取并缓存头像。
     * 获取失败时不改变现有头像，调用方可继续使用文字头像兜底。
     *
     * @param accountId 本地账号 ID
     * @return 最新可用头像地址；没有可用头像时返回 {@code null}
     */
    String refreshAvatar(Long accountId);
}
