package com.lottery.wx.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lottery.common.result.Result;
import com.lottery.common.result.ResultCode;
import com.lottery.wx.service.WxTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 默认鉴权拦截：除标注 {@link PublicApi} 的接口外，均需携带有效登录 token。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String HEADER_TOKEN = "Authorization";
    public static final String HEADER_TOKEN_ALT = "X-Token";

    private final WxTokenService wxTokenService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        boolean isPublic = handlerMethod.hasMethodAnnotation(PublicApi.class)
                || handlerMethod.getBeanType().isAnnotationPresent(PublicApi.class);
        if (isPublic) {
            return true;
        }

        String token = resolveToken(request);
        LoginUser loginUser = wxTokenService.parseToken(token);
        if (loginUser == null) {
            writeUnauthorized(response);
            return false;
        }
        UserContext.set(loginUser);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader(HEADER_TOKEN);
        if (auth != null && !auth.isBlank()) {
            auth = auth.trim();
            if (auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return auth.substring(7).trim();
            }
            return auth;
        }
        String alt = request.getHeader(HEADER_TOKEN_ALT);
        return alt == null ? null : alt.trim();
    }

    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Result<Void> body = Result.fail(ResultCode.UNAUTHORIZED);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
