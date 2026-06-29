package com.example.hello_demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hello_demo.common.PageResult;
import com.example.hello_demo.dto.TicketAssigneeUpdateRequest;
import com.example.hello_demo.dto.TicketCategoryUpdateRequest;
import com.example.hello_demo.dto.TicketCreateDTO;
import com.example.hello_demo.dto.TicketQueryRequest;
import com.example.hello_demo.dto.TicketUpdateRequest;
import com.example.hello_demo.entity.Ticket;
import com.example.hello_demo.entity.TicketReply;
import com.example.hello_demo.entity.User;
import com.example.hello_demo.enums.BusinessType;
import com.example.hello_demo.enums.OperationType;
import com.example.hello_demo.enums.TicketStatus;
import com.example.hello_demo.enums.UserRole;
import com.example.hello_demo.exception.BusinessException;
import com.example.hello_demo.mapper.TicketMapper;
import com.example.hello_demo.mapper.TicketReplyMapper;
import com.example.hello_demo.mapper.UserMapper;
import com.example.hello_demo.security.PermissionUtil;
import com.example.hello_demo.vo.TicketDetailVO;
import com.example.hello_demo.vo.TicketReplyVO;
import com.example.hello_demo.vo.UserInfoVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工单业务逻辑层。
 * 负责处理工单相关业务，例如新增工单、查询工单列表、查询工单详情。
 */
@Service
public class TicketService {

    private static final Set<String> ALLOWED_PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");

    private final TicketMapper ticketMapper;
    private final UserMapper userMapper;
    private final TicketReplyMapper ticketReplyMapper;
    private final OperationLogService operationLogService;
    private final TicketCacheService ticketCacheService;
    private final TicketStatusTransitionPolicy statusTransitionPolicy;

    public TicketService(TicketMapper ticketMapper, UserMapper userMapper, TicketReplyMapper ticketReplyMapper, OperationLogService operationLogService, TicketCacheService ticketCacheService, TicketStatusTransitionPolicy statusTransitionPolicy) {
        this.ticketMapper = ticketMapper;
        this.userMapper = userMapper;
        this.ticketReplyMapper = ticketReplyMapper;
        this.operationLogService = operationLogService;
        this.ticketCacheService = ticketCacheService;
        this.statusTransitionPolicy = statusTransitionPolicy;
    }

    /**
     * 分页查询工单。
     */
    public PageResult<Ticket> getTickets(TicketQueryRequest request) {
        if (request == null) {
            request = new TicketQueryRequest();
        }

        Long currentUserId = PermissionUtil.requireLoginUserId();
        String currentRole = PermissionUtil.requireLoginRole();

        normalizePage(request);
        normalizeQueryFilters(request);

        Page<Ticket> pageParam = new Page<>(request.getPage(), request.getSize());
        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<>();

        if (UserRole.isUser(currentRole)) {
            wrapper.eq(Ticket::getUserId, currentUserId);
        }
        if (request.getStatus() != null) {
            wrapper.eq(Ticket::getStatus, request.getStatus());
        }
        if (request.getPriority() != null) {
            wrapper.eq(Ticket::getPriority, request.getPriority());
        }
        if (request.getCategory() != null) {
            wrapper.eq(Ticket::getCategory, request.getCategory());
        }
        Long assignedTo = normalizeAssignedToFilter(request.getAssignedTo(), currentUserId);
        if (assignedTo != null) {
            wrapper.eq(Ticket::getAssignedTo, assignedTo);
        }
        String keyword = request.getKeyword();
        if (keyword != null) {
            wrapper.and(keywordWrapper -> keywordWrapper
                    .like(Ticket::getTitle, keyword)
                    .or()
                    .like(Ticket::getContent, keyword));
        }

        wrapper.orderByDesc(Ticket::getCreatedAt);

        Page<Ticket> resultPage = ticketMapper.selectPage(pageParam, wrapper);
        return PageResult.of(
                resultPage.getRecords(),
                resultPage.getTotal(),
                resultPage.getCurrent(),
                resultPage.getSize()
        );
    }

    /**
     * 根据 ID 查询工单详情。
     */
    public Ticket getTicketById(Long id) {
        validateId(id);

        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(404, "目标工单不存在。");
        }
        checkTicketReadable(ticket);
        return ticket;
    }

    /**
     * 新增工单。
     */
    public Ticket createTicket(TicketCreateDTO dto) {
        Long currentUserId = PermissionUtil.requireLoginUserId();

        Ticket ticket = new Ticket();
        // 工单提交人必须来自 Token，忽略前端请求体里可能伪造的 userId。
        ticket.setTitle(dto.getTitle());
        ticket.setContent(dto.getContent());
        ticket.setPriority(dto.getPriority());
        ticket.setCategory(normalizeCategoryForStorage(dto.getCategory()));
        ticket.setUserId(currentUserId);
        validateTicketUser(currentUserId);

        ticket.setStatus(TicketStatus.OPEN.name());
        if (ticket.getPriority() == null || ticket.getPriority().isBlank()) {
            ticket.setPriority("MEDIUM");
        } else {
            String priority = normalize(ticket.getPriority());
            validatePriority(priority);
            ticket.setPriority(priority);
        }
        ticketMapper.insert(ticket);
        operationLogService.record(
                OperationType.CREATE_TICKET.name(),
                BusinessType.TICKET.name(),
                ticket.getId(),
                "用户创建了工单 #" + ticket.getId()
        );

        return ticketMapper.selectById(ticket.getId());
    }

    /**
     * 查询工单详情，包括工单本身、提交人信息和回复列表。
     */
    public TicketDetailVO getTicketDetail(Long id) {
        validateId(id);

        TicketDetailVO cached = ticketCacheService.getTicketDetail(id);
        if (cached != null && cached.getTicket() != null) {
            // 缓存命中后仍要校验数据权限，避免 USER 读取到别人的工单详情。
            checkTicketReadable(cached.getTicket());
            return cached;
        }

        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(404, "目标工单不存在。");
        }
        checkTicketReadable(ticket);

        User user = userMapper.selectById(ticket.getUserId());
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        LambdaQueryWrapper<TicketReply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketReply::getTicketId, id);
        wrapper.orderByAsc(TicketReply::getCreatedAt);

        List<TicketReplyVO> replies = toTicketReplyVOs(ticketReplyMapper.selectList(wrapper));
        TicketDetailVO ticketDetailVO = new TicketDetailVO(ticket, toUserInfoVO(user), replies);
        ticketCacheService.setTicketDetail(id, ticketDetailVO);

        return ticketDetailVO;
    }

    /**
     * 修改工单。
     */
    public Boolean updateTicket(Long id, TicketUpdateRequest request) {
        PermissionUtil.requireStaffOrAdmin();
        validateId(id);
        Ticket existingTicket = ticketMapper.selectById(id);
        if (existingTicket == null) {
            throw new BusinessException(404, "目标工单不存在。");
        }

        String priority = normalize(request.getPriority());
        String status = statusTransitionPolicy.normalize(request.getStatus()).name();
        validatePriority(priority);
        if (!existingTicket.getStatus().equals(status)) {
            statusTransitionPolicy.validateTransition(existingTicket.getStatus(), status);
        }

        Ticket ticket = new Ticket();
        // 路径参数 id 是唯一可信来源，不使用请求体中的 id。
        ticket.setId(id);
        ticket.setTitle(request.getTitle());
        ticket.setContent(request.getContent());
        ticket.setPriority(priority);
        ticket.setStatus(status);
        ticket.setUpdatedAt(LocalDateTime.now());

        if (request.getCategory() != null) {
            ticket.setCategory(normalizeCategoryForStorage(request.getCategory()));
        }

        int rows = ticketMapper.updateById(ticket);
        if (rows == 0) {
            throw new BusinessException(500, "工单修改失败");
        }

        ticketCacheService.evictTicketRelated(id);
        return true;
    }

    /**
     * 修改工单分类。
     */
    public Ticket updateTicketCategory(Long id, TicketCategoryUpdateRequest request) {
        PermissionUtil.requireStaffOrAdmin();
        validateId(id);
        Ticket ticket = getExistingTicket(id);

        String oldCategory = ticket.getCategory();
        String newCategory = normalizeCategoryForStorage(request == null ? null : request.getCategory());

        LambdaUpdateWrapper<Ticket> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Ticket::getId, id)
                .set(Ticket::getCategory, newCategory)
                .set(Ticket::getUpdatedAt, LocalDateTime.now());

        int rows = ticketMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BusinessException(500, "工单分类修改失败");
        }

        ticketCacheService.evictTicketRelated(id);
        operationLogService.record(
                OperationType.TICKET_CATEGORY_UPDATED.name(),
                BusinessType.TICKET.name(),
                id,
                "工单分类从 [" + displayValue(oldCategory, "未分类") + "] 修改为 [" + displayValue(newCategory, "未分类") + "]"
        );

        return ticketMapper.selectById(id);
    }

    /**
     * 分配或取消工单处理人。
     */
    public Ticket updateTicketAssignee(Long id, TicketAssigneeUpdateRequest request) {
        Long currentUserId = PermissionUtil.requireLoginUserId();
        String currentRole = PermissionUtil.requireLoginRole();
        if (!UserRole.isStaffOrAdmin(currentRole)) {
            throw new BusinessException(403, "你没有权限执行该操作。");
        }

        validateId(id);
        Ticket ticket = getExistingTicket(id);
        Long newAssignedTo = request == null ? null : request.getAssignedTo();

        User newAssignee = validateAssigneeForCurrentUser(newAssignedTo, currentUserId, currentRole);
        String oldAssigneeName = assigneeName(ticket.getAssignedTo());
        String newAssigneeName = assigneeName(newAssignedTo, newAssignee);

        LambdaUpdateWrapper<Ticket> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Ticket::getId, id)
                .set(Ticket::getAssignedTo, newAssignedTo)
                .set(Ticket::getUpdatedAt, LocalDateTime.now());

        int rows = ticketMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BusinessException(500, "工单处理人修改失败");
        }

        ticketCacheService.evictTicketRelated(id);
        operationLogService.record(
                OperationType.TICKET_ASSIGNEE_UPDATED.name(),
                BusinessType.TICKET.name(),
                id,
                "工单处理人从 [" + oldAssigneeName + "] 修改为 [" + newAssigneeName + "]"
        );

        return ticketMapper.selectById(id);
    }

    /**
     * 修改工单状态。
     */
    public Ticket updateTicketStatus(Long id, String newStatus) {
        PermissionUtil.requireStaffOrAdmin();
        validateId(id);

        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(404, "目标工单不存在。");
        }

        String normalizedStatus = statusTransitionPolicy.normalize(newStatus).name();

        String currentStatus = ticket.getStatus();
        if (currentStatus.equals(normalizedStatus)) {
            return ticket;
        }
        statusTransitionPolicy.validateTransition(currentStatus, normalizedStatus);

        ticket.setStatus(normalizedStatus);
        ticket.setUpdatedAt(LocalDateTime.now());

        int rows = ticketMapper.updateById(ticket);
        if (rows == 0) {
            throw new BusinessException(500, "工单状态修改失败");
        }

        ticketCacheService.evictTicketRelated(id);
        operationLogService.record(
                OperationType.UPDATE_TICKET_STATUS.name(),
                BusinessType.TICKET.name(),
                ticket.getId(),
                "用户将工单 #" + ticket.getId() + " 状态修改为 " + normalizedStatus
        );

        return ticketMapper.selectById(id);
    }

    /**
     * 删除工单。
     */
    public Boolean deleteTicket(Long id) {
        PermissionUtil.requireAdmin();
        validateId(id);

        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(404, "目标工单不存在。");
        }

        int rows = ticketMapper.deleteById(id);
        if (rows == 0) {
            throw new BusinessException(500, "工单删除失败");
        }

        ticketCacheService.evictTicketRelated(id);
        operationLogService.record(
                OperationType.DELETE_TICKET.name(),
                BusinessType.TICKET.name(),
                id,
                "管理员删除了工单 #" + id
        );

        return true;
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

    private List<TicketReplyVO> toTicketReplyVOs(List<TicketReply> replies) {
        if (replies == null || replies.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = findReplyAuthors(replies);
        return replies.stream()
                .map(reply -> toTicketReplyVO(reply, users.get(reply.getUserId())))
                .toList();
    }

    private Map<Long, User> findReplyAuthors(List<TicketReply> replies) {
        List<Long> userIds = replies.stream()
                .map(TicketReply::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }

        List<User> users = userMapper.selectBatchIds(userIds);
        if (users == null || users.isEmpty()) {
            return Map.of();
        }
        return users.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, ignored) -> first));
    }

    private TicketReplyVO toTicketReplyVO(TicketReply reply, User author) {
        return new TicketReplyVO(
                reply.getId(),
                reply.getTicketId(),
                reply.getUserId(),
                authorName(reply.getUserId(), author),
                author == null ? null : author.getRole(),
                reply.getContent(),
                reply.getReplyType(),
                reply.getCreatedAt(),
                reply.getUpdatedAt()
        );
    }

    private String authorName(Long userId, User author) {
        if (author == null) {
            return userId == null ? null : "用户#" + userId;
        }
        return displayValue(author.getName(), displayValue(author.getUsername(), "用户#" + userId));
    }

    private void checkTicketReadable(Ticket ticket) {
        Long currentUserId = PermissionUtil.requireLoginUserId();
        String currentRole = PermissionUtil.requireLoginRole();

        if (UserRole.isUser(currentRole) && !currentUserId.equals(ticket.getUserId())) {
            throw new BusinessException(403, "你没有权限执行该操作。");
        }
    }

    private Ticket getExistingTicket(Long id) {
        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(404, "目标工单不存在。");
        }
        return ticket;
    }

    private void validateId(Long id) {
        if (id == null) {
            throw new BusinessException(400, "工单id不能为空");
        }
    }

    private void validatePriority(String priority) {
        if (!ALLOWED_PRIORITIES.contains(priority)) {
            throw new BusinessException(400, "工单优先级不合法");
        }
    }

    private void validateTicketUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(400, "userId不能为空");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
    }

    private void validateStatus(String status) {
        statusTransitionPolicy.normalize(status);
    }

    private void normalizePage(TicketQueryRequest request) {
        if (request.getPage() == null) {
            request.setPage(1L);
        }
        if (request.getSize() == null) {
            request.setSize(10L);
        }
        if (request.getPage() < 1) {
            throw new BusinessException(400, "page不能小于1");
        }
        if (request.getSize() < 1) {
            throw new BusinessException(400, "size不能小于1");
        }
        if (request.getSize() > 100) {
            throw new BusinessException(400, "size不能大于100");
        }
    }

    private void normalizeQueryFilters(TicketQueryRequest request) {
        request.setStatus(normalizeOptional(request.getStatus()));
        request.setPriority(normalizeOptional(request.getPriority()));
        request.setCategory(normalizeCategoryForStorage(request.getCategory()));
        request.setAssignedTo(trimToNull(request.getAssignedTo()));
        request.setKeyword(trimToNull(request.getKeyword()));

        if (request.getStatus() != null) {
            validateStatus(request.getStatus());
        }
        if (request.getPriority() != null) {
            validatePriority(request.getPriority());
        }
    }

    private Long normalizeAssignedToFilter(String assignedTo, Long currentUserId) {
        if (assignedTo == null) {
            return null;
        }
        if ("me".equalsIgnoreCase(assignedTo)) {
            return currentUserId;
        }
        try {
            long value = Long.parseLong(assignedTo);
            if (value < 1) {
                throw new BusinessException(400, "assignedTo不能小于1");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "assignedTo参数格式不正确");
        }
    }

    private String normalizeOptional(String value) {
        String trimmedValue = trimToNull(value);
        if (trimmedValue == null) {
            return null;
        }
        return trimmedValue.toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeCategoryForStorage(String value) {
        String category = trimToNull(value);
        if (category == null) {
            return null;
        }
        if (category.length() > 64) {
            throw new BusinessException(400, "工单分类长度不能超过64");
        }
        return category;
    }

    private String normalize(String value) {
        return value.trim().toUpperCase();
    }

    private User validateAssigneeForCurrentUser(Long assignedTo, Long currentUserId, String currentRole) {
        if (assignedTo == null) {
            if (UserRole.isAdmin(currentRole)) {
                return null;
            }
            throw new BusinessException(403, "你没有权限执行该操作。");
        }
        if (assignedTo < 1) {
            throw new BusinessException(400, "assignedTo不能小于1");
        }
        if (UserRole.isStaff(currentRole) && !currentUserId.equals(assignedTo)) {
            throw new BusinessException(403, "你没有权限执行该操作。");
        }

        User assignee = userMapper.selectById(assignedTo);
        if (assignee == null) {
            throw new BusinessException(400, "处理人不存在");
        }
        if (!UserRole.isStaffOrAdmin(assignee.getRole())) {
            throw new BusinessException(400, "处理人必须是 STAFF 或 ADMIN");
        }
        return assignee;
    }

    private String assigneeName(Long userId) {
        if (userId == null) {
            return "未分配";
        }
        return assigneeName(userId, userMapper.selectById(userId));
    }

    private String assigneeName(Long userId, User user) {
        if (userId == null) {
            return "未分配";
        }
        if (user == null) {
            return "用户#" + userId;
        }
        return displayValue(user.getName(), displayValue(user.getUsername(), "用户#" + userId));
    }

    private String displayValue(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }
}
