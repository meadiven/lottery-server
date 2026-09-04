package com.lottery.common.result;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ResultCode {

    SUCCESS(0, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已失效"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    BUSINESS_ERROR(1000, "业务处理失败"),
    COLLECT_ERROR(2000, "数据采集失败"),
    WX_LOGIN_ERROR(3000, "微信登录失败"),
    SYSTEM_ERROR(5000, "系统异常");

    private final int code;
    private final String message;
}
