package com.lottery.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "wechat.miniapp")
public class WechatMiniappProperties {

    private String appId;

    private String appSecret;

    private String code2sessionUrl = "https://api.weixin.qq.com/sns/jscode2session";

    /** 登录态 token 有效天数 */
    private int tokenTtlDays = 7;
}
