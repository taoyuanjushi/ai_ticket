package com.example.hello_demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hello_demo.common.PageResult;
import com.example.hello_demo.entity.OperationLog;
import com.example.hello_demo.entity.Ticket;
import com.example.hello_demo.entity.TicketReply;
import com.example.hello_demo.entity.User;
import com.example.hello_demo.enums.BusinessType;
import com.example.hello_demo.enums.OperationType;
import com.example.hello_demo.enums.UserRole;
import com.example.hello_demo.exception.BusinessException;
import com.example.hello_demo.mapper.OperationLogMapper;
import com.example.hello_demo.mapper.TicketMapper;
import com.example.hello_demo.mapper.TicketReplyMapper;
import com.example.hello_demo.mapper.UserMapper;
import com.example.hello_demo.security.CurrentUserContext;
import com.example.hello_demo.security.PermissionUtil;
import com.example.hello_demo.vo.OperationLogVO;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class OperationLogService {

    private static final Pattern SENSITIVE_DETAIL_PATTERN = Pattern.compile(
            "(?i)\\b(authorization|auth_token|token|password)\\s*[:=]\\s*[^,;\\s]+"
    );

    private static final Set<String> STAFF_VISIBLE_BUSINESS_TYPES = Set.of(
            BusinessType.TICKET.name(),
            BusinessType.TICKET_REPLY.name()
    );

    private final OperationLogMapper operationLogMapper;
    private final TicketMapper ticketMapper;
    private final TicketReplyMapper ticketReplyMapper;
    private final UserMapper userMapper;

    public OperationLogService(
            OperationLogMapper operationLogMapper,
            TicketMapper ticketMapper,
            TicketReplyMapper ticketReplyMapper,
            UserMapper userMapper) {
        this.operationLogMapper = operationLogMapper;
        this.ticketMapper = ticketMapper;
        this.ticketReplyMapper = ticketReplyMapper;
        this.userMapper = userMapper;
    }

    public void record(Long userId, String operationType, String businessType, Long businessId, String content) {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setOperationType(requireOperationType(operationType));
        log.setBusinessType(requireBusinessType(businessType));
        log.setBusinessId(businessId);
        log.setContent(requireContent(content));

        operationLogMapper.insert(log);
    }

    public void record(String operationType, String businessType, Long businessId, String content) {
        Long currentUserId = CurrentUserContext.getUserId();
        record(currentUserId, operationType, businessType, businessId, content);
    }

    public PageResult<OperationLogVO> getTicketLogs(Long ticketId, Long page, Long size) {
        Long currentUserId = PermissionUtil.requireLoginUserId();
        String currentRole = PermissionUtil.requireLoginRole();
        Long normalizedTicketId = requirePositiveId(ticketId, "ticketId");

        Ticket ticket = ticketMapper.selectById(normalizedTicketId);
        if (ticket == null) {
            throw new BusinessException(404, "目标工单不存在。");
        }
        if (!PermissionUtil.canViewTicketLogs(currentRole, currentUserId, ticket)) {
            throw new BusinessException(403, "你没有权限执行该操作。");
        }

        Long normalizedPage = normalizePage(page);
        Long normalizedSize = normalizeSize(size);
        List<Long> replyIds = findReplyIds(normalizedTicketId);

        Page<OperationLog> pageParam = new Page<>(normalizedPage, normalizedSize);
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        applyTicketScope(wrapper, normalizedTicketId, replyIds);
        wrapper.orderByDesc(OperationLog::getCreatedAt);

        Page<OperationLog> resultPage = operationLogMapper.selectPage(pageParam, wrapper);
        return toPageResult(resultPage, normalizedTicketId);
    }

    public PageResult<OperationLogVO> getOperationLogs(
            Long page,
            Long size,
            Long operatorId,
            String action,
            String businessType) {
        return getOperationLogs(page, size, null, operatorId, action, businessType);
    }

    public PageResult<OperationLogVO> getOperationLogs(
            Long page,
            Long size,
            Long ticketId,
            Long operatorId,
            String action,
            String businessType) {
        PermissionUtil.requireLoginUserId();
        String currentRole = PermissionUtil.requireLoginRole();
        if (!PermissionUtil.canViewGlobalOperationLogs(currentRole)) {
            throw new BusinessException(403, "你没有权限执行该操作。");
        }

        Long normalizedPage = normalizePage(page);
        Long normalizedSize = normalizeSize(size);
        Long normalizedTicketId = optionalPositiveId(ticketId, "ticketId");
        Long normalizedOperatorId = optionalPositiveId(operatorId, "operatorId");
        String normalizedAction = normalizeOperationType(action);
        String normalizedBusinessType = normalizeBusinessType(businessType);

        List<Long> replyIds = normalizedTicketId == null ? List.of() : findReplyIds(requireExistingTicketId(normalizedTicketId));

        Page<OperationLog> pageParam = new Page<>(normalizedPage, normalizedSize);
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();

        if (UserRole.isStaff(currentRole)) {
            wrapper.in(OperationLog::getBusinessType, STAFF_VISIBLE_BUSINESS_TYPES);
        }
        if (normalizedTicketId != null) {
            applyTicketScope(wrapper, normalizedTicketId, replyIds);
        }
        if (normalizedOperatorId != null) {
            wrapper.eq(OperationLog::getUserId, normalizedOperatorId);
        }
        if (normalizedAction != null) {
            wrapper.eq(OperationLog::getOperationType, normalizedAction);
        }
        if (normalizedBusinessType != null) {
            wrapper.eq(OperationLog::getBusinessType, normalizedBusinessType);
        }

        wrapper.orderByDesc(OperationLog::getCreatedAt);

        Page<OperationLog> resultPage = operationLogMapper.selectPage(pageParam, wrapper);
        return toPageResult(resultPage, normalizedTicketId);
    }

    private Long requireExistingTicketId(Long ticketId) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BusinessException(404, "目标工单不存在。");
        }
        return ticketId;
    }

    private void applyTicketScope(LambdaQueryWrapper<OperationLog> wrapper, Long ticketId, List<Long> replyIds) {
        wrapper.and(ticketLog -> {
            ticketLog.eq(OperationLog::getBusinessType, BusinessType.TICKET.name())
                    .eq(OperationLog::getBusinessId, ticketId);
            if (!replyIds.isEmpty()) {
                ticketLog.or()
                        .eq(OperationLog::getBusinessType, BusinessType.TICKET_REPLY.name())
                        .in(OperationLog::getBusinessId, replyIds);
            }
        });
    }

    private List<Long> findReplyIds(Long ticketId) {
        LambdaQueryWrapper<TicketReply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketReply::getTicketId, ticketId)
                .select(TicketReply::getId);
        return ticketReplyMapper.selectList(wrapper).stream()
                .map(TicketReply::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    private PageResult<OperationLogVO> toPageResult(Page<OperationLog> resultPage, Long fixedTicketId) {
        List<OperationLog> records = resultPage.getRecords();
        Map<Long, User> users = findUsers(records);
        return PageResult.of(
                records.stream()
                        .map(log -> toVO(log, users.get(log.getUserId()), fixedTicketId))
                        .toList(),
                resultPage.getTotal(),
                resultPage.getCurrent(),
                resultPage.getSize()
        );
    }

    private Map<Long, User> findUsers(List<OperationLog> logs) {
        List<Long> userIds = logs.stream()
                .map(OperationLog::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Collection<User> users = userMapper.selectBatchIds(userIds);
        if (users == null) {
            return Collections.emptyMap();
        }
        return users.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, ignored) -> first));
    }

    private OperationLogVO toVO(OperationLog log, User operator, Long fixedTicketId) {
        Long ticketId = fixedTicketId;
        if (ticketId == null && BusinessType.TICKET.name().equals(log.getBusinessType())) {
            ticketId = log.getBusinessId();
        }
        return new OperationLogVO(
                log.getId(),
                ticketId,
                log.getUserId(),
                operatorName(log.getUserId(), operator),
                log.getOperationType(),
                safeDetail(log.getContent()),
                log.getCreatedAt()
        );
    }

    private String operatorName(Long operatorId, User operator) {
        if (operator == null) {
            return operatorId == null ? null : "user#" + operatorId;
        }
        String name = trimToNull(operator.getName());
        if (name != null) {
            return name;
        }
        String username = trimToNull(operator.getUsername());
        return username == null ? "user#" + operatorId : username;
    }

    private String safeDetail(String detail) {
        String text = detail == null ? "" : detail.trim();
        text = SENSITIVE_DETAIL_PATTERN.matcher(text).replaceAll("[SENSITIVE]");
        if (text.length() <= 500) {
            return text;
        }
        return text.substring(0, 500);
    }

    private Long normalizePage(Long page) {
        if (page == null) {
            return 1L;
        }
        if (page < 1) {
            throw new BusinessException(400, "page不能小于1");
        }
        return page;
    }

    private Long normalizeSize(Long size) {
        if (size == null) {
            return 20L;
        }
        if (size < 1) {
            throw new BusinessException(400, "size不能小于1");
        }
        if (size > 100) {
            throw new BusinessException(400, "size不能大于100");
        }
        return size;
    }

    private Long requirePositiveId(Long id, String name) {
        if (id == null) {
            throw new BusinessException(400, name + "不能为空");
        }
        if (id < 1) {
            throw new BusinessException(400, name + "不能小于1");
        }
        return id;
    }

    private Long optionalPositiveId(Long id, String name) {
        if (id == null) {
            return null;
        }
        return requirePositiveId(id, name);
    }

    private String normalizeOperationType(String operationType) {
        String value = trimToNull(operationType);
        if (value == null) {
            return null;
        }
        String normalizedType = OperationType.normalize(value);
        if (normalizedType == null) {
            throw new BusinessException(400, "action不合法");
        }
        return normalizedType;
    }

    private String normalizeBusinessType(String businessType) {
        String value = trimToNull(businessType);
        if (value == null) {
            return null;
        }
        String normalizedType = BusinessType.normalize(value);
        if (normalizedType == null) {
            throw new BusinessException(400, "businessType不合法");
        }
        return normalizedType;
    }

    private String requireOperationType(String operationType) {
        String normalizedType = OperationType.normalize(operationType);
        if (normalizedType == null) {
            throw new BusinessException(500, "操作类型不合法");
        }
        return normalizedType;
    }

    private String requireBusinessType(String businessType) {
        String normalizedType = BusinessType.normalize(businessType);
        if (normalizedType == null) {
            throw new BusinessException(500, "业务类型不合法");
        }
        return normalizedType;
    }

    private String requireContent(String content) {
        String value = trimToNull(content);
        if (value == null) {
            throw new BusinessException(500, "操作日志内容不能为空");
        }
        return value;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
