package com.example.hello_demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hello_demo.constant.RedisKeyConstants;
import com.example.hello_demo.entity.Ticket;
import com.example.hello_demo.entity.TicketReply;
import com.example.hello_demo.entity.User;
import com.example.hello_demo.enums.UserRole;
import com.example.hello_demo.exception.BusinessException;
import com.example.hello_demo.mapper.TicketMapper;
import com.example.hello_demo.mapper.TicketReplyMapper;
import com.example.hello_demo.mapper.UserMapper;
import com.example.hello_demo.security.PermissionUtil;
import com.example.hello_demo.vo.UserInfoVO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 用户业务逻辑层。
 * Controller 不直接访问数据库，而是通过 Service 调用 Mapper 完成操作。
 */
@Service
public class UserService {

    private static final String DEFAULT_ROLE = "USER";

    private final UserMapper userMapper;
    private final TicketMapper ticketMapper;
    private final TicketReplyMapper ticketReplyMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RedisCacheService redisCacheService;

    public UserService(UserMapper userMapper, TicketMapper ticketMapper, TicketReplyMapper ticketReplyMapper, BCryptPasswordEncoder passwordEncoder, RedisCacheService redisCacheService) {
        this.userMapper = userMapper;
        this.ticketMapper = ticketMapper;
        this.ticketReplyMapper = ticketReplyMapper;
        this.passwordEncoder = passwordEncoder;
        this.redisCacheService = redisCacheService;
    }

    public List<User> getUsers() {
        PermissionUtil.requireAdmin();
        return userMapper.selectList(null);
    }

    public UserInfoVO getUserById(Long id) {
        checkUserProfileReadable(id);

        String key = RedisKeyConstants.userDetailKey(id);
        Object cached = redisCacheService.get(key);
        if (cached instanceof UserInfoVO userInfoVO) {
            return userInfoVO;
        }

        User user = getExistingUser(id);
        UserInfoVO userInfoVO = toUserInfoVO(user);
        redisCacheService.set(key, userInfoVO, Duration.ofMinutes(30));

        return userInfoVO;
    }

    public User createUser(User user) {
        PermissionUtil.requireAdmin();
        // 新增用户时由 MySQL 自增主键生成 id，避免使用前端传入的 id。
        user.setId(null);
        prepareUserForCreate(user);
        userMapper.insert(user);
        return user;
    }

    public User updateUser(Long id, User user) {
        PermissionUtil.requireAdmin();
        User existingUser = userMapper.selectById(id);
        if (existingUser == null) {
            throw new BusinessException(404, "用户不存在");
        }

        user.setId(id);
        prepareUserForUpdate(id, user);
        userMapper.updateById(user);
        redisCacheService.delete(RedisKeyConstants.userDetailKey(id));
        return userMapper.selectById(id);
    }

    public boolean deleteUser(Long id) {
        PermissionUtil.requireAdmin();
        int rows = userMapper.deleteById(id);
        if (rows == 0) {
            throw new BusinessException(404, "用户不存在");
        }
        redisCacheService.delete(RedisKeyConstants.userDetailKey(id));
        return true;
    }

    public List<Ticket> getTicketsByUserId(Long userId) {
        checkUserRelatedDataReadable(userId);
        getExistingUser(userId);

        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Ticket::getUserId, userId);
        wrapper.orderByDesc(Ticket::getCreatedAt);

        return ticketMapper.selectList(wrapper);
    }

    public List<TicketReply> getTicketRepliesByUserId(Long userId) {
        checkUserRelatedDataReadable(userId);
        getExistingUser(userId);

        LambdaQueryWrapper<TicketReply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketReply::getUserId, userId);
        wrapper.orderByDesc(TicketReply::getCreatedAt);

        return ticketReplyMapper.selectList(wrapper);
    }

    private void checkUserProfileReadable(Long userId) {
        validateUserId(userId);

        Long currentUserId = PermissionUtil.requireLoginUserId();
        String currentRole = PermissionUtil.requireLoginRole();

        if (!UserRole.isAdmin(currentRole) && !currentUserId.equals(userId)) {
            throw new BusinessException(403, "你没有权限执行该操作。");
        }
    }

    private void checkUserRelatedDataReadable(Long userId) {
        validateUserId(userId);

        Long currentUserId = PermissionUtil.requireLoginUserId();
        String currentRole = PermissionUtil.requireLoginRole();

        if (!UserRole.isStaffOrAdmin(currentRole) && !currentUserId.equals(userId)) {
            throw new BusinessException(403, "你没有权限执行该操作。");
        }
    }

    private User getExistingUser(Long userId) {
        validateUserId(userId);

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        return user;
    }

    private UserInfoVO toUserInfoVO(User user) {
        return new UserInfoVO(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getAge(),
                user.getEmail(),
                user.getRole()
        );
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(400, "userId不能为空");
        }
    }

    private void prepareUserForCreate(User user) {
        String username = normalizeRequired(user.getUsername(), "username不能为空");
        ensureUsernameAvailable(username, null);

        String password = normalizeRequired(user.getPassword(), "password不能为空");

        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(normalizeRoleOrDefault(user.getRole()));
    }

    private void prepareUserForUpdate(Long id, User user) {
        String username = trimToNull(user.getUsername());
        if (username != null) {
            ensureUsernameAvailable(username, id);
            user.setUsername(username);
        }

        String password = trimToNull(user.getPassword());
        if (password != null) {
            user.setPassword(passwordEncoder.encode(password));
        } else {
            user.setPassword(null);
        }

        String role = trimToNull(user.getRole());
        if (role != null) {
            validateRole(role);
            user.setRole(role.toUpperCase());
        } else {
            user.setRole(null);
        }
    }

    private void ensureUsernameAvailable(String username, Long currentUserId) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        User existingUser = userMapper.selectOne(wrapper);

        if (existingUser != null && !existingUser.getId().equals(currentUserId)) {
            throw new BusinessException(400, "用户名已存在");
        }
    }

    private String normalizeRoleOrDefault(String role) {
        String normalizedRole = trimToNull(role);
        if (normalizedRole == null) {
            return DEFAULT_ROLE;
        }
        validateRole(normalizedRole);
        return normalizedRole.toUpperCase();
    }

    private void validateRole(String role) {
        if (!UserRole.isValid(role)) {
            throw new BusinessException(400, "用户角色不合法");
        }
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
