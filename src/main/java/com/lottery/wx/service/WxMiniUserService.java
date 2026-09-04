package com.lottery.wx.service;

import com.lottery.dto.request.WxProfileUpdateRequest;
import com.lottery.dto.response.WxLoginVO;
import com.lottery.dto.response.WxUserVO;

public interface WxMiniUserService {

    WxLoginVO loginByCode(String code);

    WxUserVO currentUser(Long userId);

    WxUserVO updateProfile(Long userId, WxProfileUpdateRequest request);

    void logout(String token);
}
