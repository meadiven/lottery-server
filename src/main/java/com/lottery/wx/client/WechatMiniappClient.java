package com.lottery.wx.client;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lottery.common.exception.BusinessException;
import com.lottery.common.result.ResultCode;
import com.lottery.config.properties.WechatMiniappProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 调用微信小程序登录凭证校验接口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WechatMiniappClient {

    private final RestTemplate restTemplate;
    private final WechatMiniappProperties properties;

    public Code2SessionResult code2Session(String jsCode) {
        URI uri = UriComponentsBuilder
                .fromUriString(properties.getCode2sessionUrl())
                .queryParam("appid", properties.getAppId())
                .queryParam("secret", properties.getAppSecret())
                .queryParam("js_code", jsCode)
                .queryParam("grant_type", "authorization_code")
                .encode()
                .build()
                .toUri();

        String body;
        try {
            body = restTemplate.getForObject(uri, String.class);
        } catch (Exception ex) {
            log.error("调用微信 code2session 失败", ex);
            throw new BusinessException(ResultCode.WX_LOGIN_ERROR, "微信服务暂时不可用");
        }

        if (StrUtil.isBlank(body) || !JSONUtil.isTypeJSON(body)) {
            throw new BusinessException(ResultCode.WX_LOGIN_ERROR, "微信登录返回异常");
        }

        JSONObject json = JSONUtil.parseObj(body);
        Integer errcode = json.getInt("errcode");
        if (errcode != null && errcode != 0) {
            String errmsg = json.getStr("errmsg", "unknown");
            log.warn("code2session 失败 errcode={} errmsg={}", errcode, errmsg);
            throw new BusinessException(ResultCode.WX_LOGIN_ERROR, "微信登录失败: " + errmsg);
        }

        String openid = json.getStr("openid");
        String sessionKey = json.getStr("session_key");
        if (StrUtil.hasBlank(openid, sessionKey)) {
            throw new BusinessException(ResultCode.WX_LOGIN_ERROR, "微信登录凭证无效");
        }

        return new Code2SessionResult(openid, sessionKey, json.getStr("unionid"));
    }

    public record Code2SessionResult(String openid, String sessionKey, String unionid) {
    }
}
