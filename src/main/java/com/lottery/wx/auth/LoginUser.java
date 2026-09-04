package com.lottery.wx.auth;

import lombok.Builder;
import lombok.Data;

/**
 * 登录态中缓存的用户摘要。
 */
@Data
@Builder
public class LoginUser {

    private Long userId;
    private String openid;
}
