package com.lottery.wx.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.lottery.config.properties.WechatMiniappProperties;
import com.lottery.wx.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class WxTokenService {

    private static final String TOKEN_KEY_PREFIX = "wx:token:";
    private static final String USER_TOKEN_KEY_PREFIX = "wx:user:token:";

    private final StringRedisTemplate stringRedisTemplate;
    private final WechatMiniappProperties properties;

    public String issueToken(Long userId, String openid) {
        // 单端登录：挤掉旧 token
        String oldToken = stringRedisTemplate.opsForValue().get(USER_TOKEN_KEY_PREFIX + userId);
        if (oldToken != null) {
            stringRedisTemplate.delete(TOKEN_KEY_PREFIX + oldToken);
        }

        String token = IdUtil.fastSimpleUUID();
        LoginUser loginUser = LoginUser.builder().userId(userId).openid(openid).build();
        Duration ttl = Duration.ofDays(Math.max(1, properties.getTokenTtlDays()));

        stringRedisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + token, JSONUtil.toJsonStr(loginUser), ttl);
        stringRedisTemplate.opsForValue().set(USER_TOKEN_KEY_PREFIX + userId, token, ttl);
        return token;
    }

    public LoginUser parseToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String json = stringRedisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);
        if (json == null || json.isBlank()) {
            return null;
        }
        // 滑动续期
        stringRedisTemplate.expire(TOKEN_KEY_PREFIX + token, properties.getTokenTtlDays(), TimeUnit.DAYS);
        LoginUser user = JSONUtil.toBean(json, LoginUser.class);
        if (user != null && user.getUserId() != null) {
            stringRedisTemplate.expire(USER_TOKEN_KEY_PREFIX + user.getUserId(),
                    properties.getTokenTtlDays(), TimeUnit.DAYS);
        }
        return user;
    }

    public void revokeToken(String token) {
        LoginUser user = parseTokenWithoutRenew(token);
        if (user != null) {
            stringRedisTemplate.delete(TOKEN_KEY_PREFIX + token);
            stringRedisTemplate.delete(USER_TOKEN_KEY_PREFIX + user.getUserId());
        }
    }

    private LoginUser parseTokenWithoutRenew(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String json = stringRedisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSONUtil.toBean(json, LoginUser.class);
    }
}
