package com.example.hello_demo.security;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.hello_demo.config.JwtProperties;
import com.example.hello_demo.dto.LoginRequestDTO;
import com.example.hello_demo.entity.User;
import com.example.hello_demo.exception.BusinessException;
import com.example.hello_demo.mapper.UserMapper;
import com.example.hello_demo.service.AuthService;
import com.example.hello_demo.service.OperationLogService;
import com.example.hello_demo.vo.LoginResponseVO;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthJwtSecurityTest {

    private static final String LOGIN_EXPIRED_MESSAGE = "登录状态已失效，请重新登录。";

    private final JwtUtil jwtUtil = new JwtUtil(jwtProperties());

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void loginReturnsToken() {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        UserMapper userMapper = mock(UserMapper.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        User user = user(1L, "tom", passwordEncoder.encode("123456"), "USER");
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        AuthService authService = new AuthService(userMapper, passwordEncoder, operationLogService, jwtUtil);
        LoginRequestDTO dto = loginRequest("tom", "123456");

        LoginResponseVO response = authService.login(dto);

        assertNotNull(response.getToken());
        assertEquals(1L, response.getUserId());
        assertEquals("tom", response.getUsername());
        assertEquals("USER", response.getRole());
        assertEquals("tom", jwtUtil.getUsername(response.getToken()));
    }

    @Test
    void loginWithWrongPasswordFails() {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        UserMapper userMapper = mock(UserMapper.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        User user = user(1L, "tom", passwordEncoder.encode("123456"), "USER");
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        AuthService authService = new AuthService(userMapper, passwordEncoder, operationLogService, jwtUtil);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.login(loginRequest("tom", "bad-password"))
        );
        assertEquals(400, exception.getCode());
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        JwtInterceptor interceptor = new JwtInterceptor(jwtUtil);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString(StandardCharsets.UTF_8).contains("\"code\":401"));
        assertTrue(response.getContentAsString(StandardCharsets.UTF_8).contains(LOGIN_EXPIRED_MESSAGE));
        assertEquals(null, CurrentUserContext.getUserId());
    }

    @Test
    void protectedEndpointWithMalformedTokenReturnsLoginExpiredMessage() throws Exception {
        JwtInterceptor interceptor = new JwtInterceptor(jwtUtil);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "bad-token-format");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString(StandardCharsets.UTF_8).contains("\"code\":401"));
        assertTrue(response.getContentAsString(StandardCharsets.UTF_8).contains(LOGIN_EXPIRED_MESSAGE));
        assertEquals(null, CurrentUserContext.getUserId());
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        JwtInterceptor interceptor = new JwtInterceptor(jwtUtil);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString(StandardCharsets.UTF_8).contains("\"code\":401"));
        assertTrue(response.getContentAsString(StandardCharsets.UTF_8).contains(LOGIN_EXPIRED_MESSAGE));
        assertEquals(null, CurrentUserContext.getUserId());
    }

    @Test
    void tamperedTokenReturnsLoginExpiredMessage() throws Exception {
        String token = jwtUtil.generateToken(9L, "staff", "STAFF");
        String tamperedToken = token.substring(0, token.length() - 2) + "xx";
        JwtInterceptor interceptor = new JwtInterceptor(jwtUtil);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + tamperedToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString(StandardCharsets.UTF_8).contains("\"code\":401"));
        assertTrue(response.getContentAsString(StandardCharsets.UTF_8).contains(LOGIN_EXPIRED_MESSAGE));
        assertEquals(null, CurrentUserContext.getUserId());
    }

    @Test
    void expiredTokenReturnsLoginExpiredMessage() throws Exception {
        JwtProperties properties = jwtProperties();
        properties.setExpiration(1L);
        JwtUtil shortLivedJwtUtil = new JwtUtil(properties);
        String token = shortLivedJwtUtil.generateToken(9L, "staff", "STAFF");
        Thread.sleep(50L);
        JwtInterceptor interceptor = new JwtInterceptor(shortLivedJwtUtil);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString(StandardCharsets.UTF_8).contains("\"code\":401"));
        assertTrue(response.getContentAsString(StandardCharsets.UTF_8).contains(LOGIN_EXPIRED_MESSAGE));
        assertEquals(null, CurrentUserContext.getUserId());
    }

    @Test
    void validTokenSetsCurrentUserContext() throws Exception {
        String token = jwtUtil.generateToken(9L, "staff", "STAFF");
        JwtInterceptor interceptor = new JwtInterceptor(jwtUtil);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        assertEquals(9L, CurrentUserContext.getUserId());
        assertEquals("staff", CurrentUserContext.getUsername());
        assertEquals("STAFF", CurrentUserContext.getRole());
    }

    private JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-change-me-test-secret-change-me");
        properties.setExpiration(86400000L);
        return properties;
    }

    private LoginRequestDTO loginRequest(String username, String password) {
        LoginRequestDTO dto = new LoginRequestDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    private User user(Long id, String username, String password, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(password);
        user.setName(username);
        user.setEmail(username + "@example.com");
        user.setRole(role);
        return user;
    }
}
