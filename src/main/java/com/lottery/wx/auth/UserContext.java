package com.lottery.wx.auth;

import com.lottery.common.exception.BusinessException;
import com.lottery.common.result.ResultCode;

/**
 * 当前请求登录用户上下文。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long requireUserId() {
        LoginUser user = HOLDER.get();
        if (user == null || user.getUserId() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user.getUserId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
