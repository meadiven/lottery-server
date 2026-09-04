package com.lottery.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WxUserVO {

    private Long userId;
    private String nickname;
    private String avatarUrl;
    private Integer gender;
    private String phone;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
