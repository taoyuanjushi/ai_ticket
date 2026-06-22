package com.example.hello_demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hello_demo.dto.LoginRequestDTO;
import com.example.hello_demo.dto.RegisterRequestDTO;
import com.example.hello_demo.entity.User;
import com.example.hello_demo.enums.BusinessType;
import com.example.hello_demo.enums.OperationType;
import com.example.hello_demo.exception.BusinessException;
import com.example.hello_demo.mapper.UserMapper;
import com.example.hello_demo.security.CurrentUserContext;
import com.example.hello_demo.security.JwtUtil;
import com.example.hello_demo.vo.CurrentUserVO;
import com.example.hello_demo.vo.LoginResponseVO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证业务逻辑层。
 * 负责注册、登录和查询当前登录用户。
 */
@Service
public class AuthService {

    private static final String DEFAULT_ROLE = "USER";

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;
    private final JwtUtil jwtUtil;

    public AuthService(UserMapper userMapper, BCryptPasswordEncoder passwordEncoder, OperationLogService operationLogService, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.operationLogService = operationLogService;
        this.jwtUtil = jwtUtil;
    }

    public CurrentUserVO register(RegisterRequestDTO dto) {
        String username = normalizeRequired(dto.getUsername(), "username不能为空");
        ensureUsernameNotExists(username);

        User user = new User();
        user.setUsername(username);
        // 注册时只保存 BCrypt 加密后的密码，数据库中不能保存明文密码。
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setName(dto.getName().trim());
        user.setAge(dto.getAge());
        user.setEmail(trimToNull(dto.getEmail()));
        user.setRole(DEFAULT_ROLE);

        userMapper.insert(user);
        operationLogService.record(
                user.getId(),
                OperationType.REGISTER_USER.name(),
                BusinessType.AUTH.name(),
                user.getId(),
                "用户注册成功：" + user.getUsername()
        );

        return toCurrentUserVO(user);
    }

    public LoginResponseVO login(LoginRequestDTO dto) {
        String username = normalizeRequired(dto.getUsername(), "username不能为空");
        User user = getUserByUsername(username);

        if (user == null) {
            operationLogService.record(
                    null,
                    OperationType.LOGIN_FAILED.name(),
                    BusinessType.AUTH.name(),
                    null,
                    "用户登录失败，用户名不存在：" + username
            );
            throw new BusinessException(400, "用户名或密码错误");
        }

        if (user.getPassword() == null || !isPasswordMatched(dto.getPassword(), user.getPassword())) {
            operationLogService.record(
                    user.getId(),
                    OperationType.LOGIN_FAILED.name(),
                    BusinessType.AUTH.name(),
                    user.getId(),
                    "用户登录失败，密码错误：" + user.getUsername()
            );
            throw new BusinessException(400, "用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        operationLogService.record(
                user.getId(),
                OperationType.LOGIN_SUCCESS.name(),
                BusinessType.AUTH.name(),
                user.getId(),
                "用户登录成功：" + user.getUsername()
        );

        return new LoginResponseVO(token, user.getId(), user.getUsername(), user.getRole());
    }

    public CurrentUserVO getCurrentUser() {
        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "登录状态已失效，请重新登录。");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        return toCurrentUserVO(user);
    }

    private User getUserByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return userMapper.selectOne(wrapper);
    }

    private void ensureUsernameNotExists(String username) {
        if (getUserByUsername(username) != null) {
            throw new BusinessException(400, "用户名已存在");
        }
    }

    private boolean isPasswordMatched(String rawPassword, String encodedPassword) {
        try {
            return passwordEncoder.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private CurrentUserVO toCurrentUserVO(User user) {
        return new CurrentUserVO(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    private String normalizeRequired(String value, String message) {
        String normalizedValue = trimToNull(value);
        if (normalizedValue == null) {
            throw new BusinessException(400, message);
        }
        return normalizedValue;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
