package com.lottery.controller;

import com.lottery.common.result.Result;
import com.lottery.dto.request.WxLoginRequest;
import com.lottery.dto.request.WxProfileUpdateRequest;
import com.lottery.dto.response.WxLoginVO;
import com.lottery.dto.response.WxUserVO;
import com.lottery.wx.auth.PublicApi;
import com.lottery.wx.auth.UserContext;
import com.lottery.wx.service.WxMiniUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信小程序用户登录相关接口。
 * <p>
 * 仅 {@code /wx/login} 为公开接口，其余均需登录 token。
 */
@RestController
@RequestMapping("/wx")
@RequiredArgsConstructor
public class WxMiniUserController {

    private final WxMiniUserService wxMiniUserService;

    /**
     * 小程序登录：前端 wx.login() 拿到 code 后调用本接口（唯一公开接口）。
     */
    @PublicApi
    @PostMapping("/login")
    public Result<WxLoginVO> login(@Valid @RequestBody WxLoginRequest request) {
        return Result.ok(wxMiniUserService.loginByCode(request.getCode()));
    }

    /**
     * 获取当前登录用户信息。
     * Header: Authorization: Bearer {token} 或 X-Token: {token}
     */
    @GetMapping("/user/me")
    public Result<WxUserVO> me() {
        return Result.ok(wxMiniUserService.currentUser(UserContext.requireUserId()));
    }

    /**
     * 更新昵称/头像等资料（由小程序端授权后提交）。
     */
    @PutMapping("/user/profile")
    public Result<WxUserVO> updateProfile(@Valid @RequestBody WxProfileUpdateRequest request) {
        return Result.ok(wxMiniUserService.updateProfile(UserContext.requireUserId(), request));
    }

    /**
     * 退出登录，清除服务端 token。
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        wxMiniUserService.logout(resolveToken(request));
        return Result.ok();
    }

    private String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && !auth.isBlank()) {
            auth = auth.trim();
            if (auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return auth.substring(7).trim();
            }
            return auth;
        }
        String alt = request.getHeader("X-Token");
        return alt == null ? null : alt.trim();
    }
}
