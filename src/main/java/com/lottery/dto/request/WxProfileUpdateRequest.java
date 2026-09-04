package com.lottery.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WxProfileUpdateRequest {

    @Size(max = 64, message = "昵称最长64字符")
    private String nickname;

    @Size(max = 512, message = "头像地址过长")
    private String avatarUrl;

    /** 0未知 1男 2女 */
    private Integer gender;
}
