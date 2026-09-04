package com.lottery.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WxLoginVO {

    private String token;
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private Integer gender;
    /** true=新注册用户 */
    private Boolean newUser;
    private LocalDateTime lastLoginAt;
}
