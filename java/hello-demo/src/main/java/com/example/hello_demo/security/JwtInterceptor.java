package com.example.hello_demo.security;

import com.example.hello_demo.common.Result;
import com.example.hello_demo.exception.BusinessException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 登录拦截器。
 * 在请求进入 Controller 前校验 Token，并把当前用户信息放入 CurrentUserContext。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final String LOGIN_EXPIRED_MESSAGE = "登录状态已失效，请重新登录。";

    private final JwtUtil jwtUtil;

    public JwtInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authorization = request.getHeader("Authorization");

        if (authorization == null || authorization.trim().isEmpty()) {
            writeError(response, 401, LOGIN_EXPIRED_MESSAGE);
            return false;
        }

        if (!authorization.startsWith("Bearer ")) {
            writeError(response, 401, LOGIN_EXPIRED_MESSAGE);
            return false;
        }

        String token = authorization.substring(7);

        try {
            Claims claims = jwtUtil.parseToken(token);
            Long userId = Long.valueOf(claims.getSubject());
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);

            CurrentUserContext.set(userId, username, role);
            return true;
        } catch (BusinessException e) {
            writeError(response, 401, LOGIN_EXPIRED_MESSAGE);
            return false;
        } catch (Exception e) {
            writeError(response, 401, LOGIN_EXPIRED_MESSAGE);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUserContext.clear();
    }

    private void writeError(HttpServletResponse response, Integer code, String message) throws Exception {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(toJson(Result.fail(code, message)));
    }

    private String toJson(Result<Void> result) {
        return "{\"code\":" + result.getCode()
                + ",\"message\":\"" + escapeJson(result.getMessage())
                + "\",\"data\":null}";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
