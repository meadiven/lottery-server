package com.lottery.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WxLoginRequest {

    /** wx.login() 获取的临时登录凭证 */
    @NotBlank(message = "code不能为空")
    private String code;
}
