package com.lottery.wx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lottery.common.exception.BusinessException;
import com.lottery.common.result.ResultCode;
import com.lottery.dto.request.WxProfileUpdateRequest;
import com.lottery.dto.response.WxLoginVO;
import com.lottery.dto.response.WxUserVO;
import com.lottery.entity.WxMiniUser;
import com.lottery.mapper.WxMiniUserMapper;
import com.lottery.wx.client.WechatMiniappClient;
import com.lottery.wx.service.WxMiniUserService;
import com.lottery.wx.service.WxTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class WxMiniUserServiceImpl implements WxMiniUserService {

    private final WechatMiniappClient wechatMiniappClient;
    private final WxMiniUserMapper wxMiniUserMapper;
    private final WxTokenService wxTokenService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WxLoginVO loginByCode(String code) {
        WechatMiniappClient.Code2SessionResult session = wechatMiniappClient.code2Session(code);

        WxMiniUser user = wxMiniUserMapper.selectOne(new LambdaQueryWrapper<WxMiniUser>()
                .eq(WxMiniUser::getOpenid, session.openid())
                .last("LIMIT 1"));

        boolean newUser = false;
        LocalDateTime now = LocalDateTime.now();
        if (user == null) {
            newUser = true;
            user = new WxMiniUser();
            user.setOpenid(session.openid());
            user.setUnionid(session.unionid());
            user.setSessionKey(session.sessionKey());
            user.setNickname("微信用户");
            user.setGender(0);
            user.setStatus(1);
            user.setLastLoginAt(now);
            wxMiniUserMapper.insert(user);
            log.info("小程序新用户注册 openid={}", maskOpenid(session.openid()));
        } else {
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new BusinessException(ResultCode.FORBIDDEN, "账号已禁用");
            }
            user.setSessionKey(session.sessionKey());
            if (session.unionid() != null && !session.unionid().isBlank()) {
                user.setUnionid(session.unionid());
            }
            user.setLastLoginAt(now);
            wxMiniUserMapper.updateById(user);
        }

        String token = wxTokenService.issueToken(user.getId(), user.getOpenid());
        return WxLoginVO.builder()
                .token(token)
                .userId(user.getId())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .gender(user.getGender())
                .newUser(newUser)
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    @Override
    public WxUserVO currentUser(Long userId) {
        WxMiniUser user = requireUser(userId);
        return toVo(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WxUserVO updateProfile(Long userId, WxProfileUpdateRequest request) {
        WxMiniUser user = requireUser(userId);
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.setNickname(request.getNickname().trim());
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isBlank()) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        wxMiniUserMapper.updateById(user);
        return toVo(user);
    }

    @Override
    public void logout(String token) {
        wxTokenService.revokeToken(token);
    }

    private WxMiniUser requireUser(Long userId) {
        WxMiniUser user = wxMiniUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已禁用");
        }
        return user;
    }

    private WxUserVO toVo(WxMiniUser user) {
        return WxUserVO.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .gender(user.getGender())
                .phone(user.getPhone())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private String maskOpenid(String openid) {
        if (openid == null || openid.length() < 8) {
            return "****";
        }
        return openid.substring(0, 4) + "****" + openid.substring(openid.length() - 4);
    }
}
