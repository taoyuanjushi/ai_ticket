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
import com.example.hello_demo.exception.BusinessException;
import com.example.hello_demo.mapper.OperationLogMapper;
import com.example.hello_demo.mapper.TicketMapper;
import com.example.hello_demo.mapper.TicketReplyMapper;
import com.example.hello_demo.mapper.UserMapper;
import com.example.hello_demo.security.CurrentUserContext;
import com.example.hello_demo.security.PermissionUtil;
import com.example.hello_demo.vo.OperationLogVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class OperationLogService {

    private static final Pattern SENSITIVE_DETAIL_PATTERN = Pattern.compile(
            "(?i)bearer\\s+[^,;\\s]+|\\b(authorization|auth_token|token|password|apikey|api_key|secret)\\b\\s*[:=]\\s*[^,;\\s]+"
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, String operationType, String businessType, Long businessId, String content) {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setOperationType(requireOperationType(operationType));
        log.setBusinessType(requireBusinessType(businessType));
        log.setBusinessId(businessId);
        log.setContent(requireContent(content));

        operationLogMapper.insert(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String operationType, String businessType, Long businessId, String content) {
        Long currentUserId = CurrentUserContext.getUserId();
        record(currentUserId, operationType, businessType, businessId, content);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAi(
            String actionType,
            String businessType,
            Long businessId,
            String conversationId,
            String intent,
            String toolName,
            String targetType,
            Long targetId,
            String resultStatus,
            String requestSummary,
            String resultSummary,
            Object riskFlags) {
        record(
                actionType,
                businessType,
                businessId,
                buildAiContent(
                        actionType,
                        conversationId,
                        intent,
                        toolName,
                        targetType,
                        targetId,
                        resultStatus,
                        requestSummary,
                        resultSummary,
                        riskFlags
                )
        );
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
        return getOperationLogs(page, size, ticketId, operatorId, action, businessType, null, null, null);
    }

    public PageResult<OperationLogVO> getOperationLogs(
            Long page,
            Long size,
            Long ticketId,
            Long operatorId,
            String action,
            String businessType,
            String operationSource,
            String resultStatus,
            String conversationId) {
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
        String normalizedOperationSource = normalizeOperationSource(operationSource);
        String normalizedResultStatus = normalizeResultStatus(resultStatus);
        String normalizedConversationId = trimToNull(conversationId);

        List<Long> replyIds = normalizedTicketId == null ? List.of() : findReplyIds(requireExistingTicketId(normalizedTicketId));

        Page<OperationLog> pageParam = new Page<>(normalizedPage, normalizedSize);
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();

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
        if ("AI".equals(normalizedOperationSource)) {
            wrapper.likeRight(OperationLog::getOperationType, "AI_");
        }
        if ("MANUAL".equals(normalizedOperationSource)) {
            wrapper.apply("operation_type NOT LIKE {0}", "AI_%");
        }
        if (normalizedResultStatus != null) {
            wrapper.like(OperationLog::getContent, "resultStatus=" + normalizedResultStatus);
        }
        if (normalizedConversationId != null) {
            wrapper.like(OperationLog::getContent, "conversationId=" + normalizedConversationId);
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
        String detail = safeDetail(log.getContent());
        OperationLogVO vo = new OperationLogVO(
                log.getId(),
                ticketId,
                log.getUserId(),
                operatorName(log.getUserId(), operator),
                log.getOperationType(),
                detail,
                log.getCreatedAt()
        );
        vo.setUsername(operator == null ? null : operator.getUsername());
        vo.setRole(operator == null ? null : operator.getRole());
        vo.setOperationSource(operationSource(log.getOperationType()));
        vo.setActionType(log.getOperationType());
        vo.setConversationId(extractContentValue(detail, "conversationId"));
        vo.setTargetType(firstNonBlank(extractContentValue(detail, "targetType"), log.getBusinessType()));
        vo.setTargetId(firstNonNull(parseLong(extractContentValue(detail, "targetId")), log.getBusinessId()));
        vo.setResultStatus(firstNonBlank(extractContentValue(detail, "resultStatus"), inferResultStatus(log.getOperationType())));
        vo.setRequestSummary(extractContentValue(detail, "requestSummary"));
        vo.setResultSummary(firstNonBlank(extractContentValue(detail, "resultSummary"), detail));
        return vo;
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

    private String buildAiContent(
            String actionType,
            String conversationId,
            String intent,
            String toolName,
            String targetType,
            Long targetId,
            String resultStatus,
            String requestSummary,
            String resultSummary,
            Object riskFlags) {
        List<String> parts = new ArrayList<>();
        addPart(parts, "operationSource", "AI");
        addPart(parts, "actionType", actionType);
        addPart(parts, "conversationId", conversationId);
        addPart(parts, "intent", intent);
        addPart(parts, "toolName", toolName);
        addPart(parts, "targetType", targetType);
        addPart(parts, "targetId", targetId);
        addPart(parts, "resultStatus", resultStatus == null ? "SUCCESS" : resultStatus);
        addPart(parts, "requestSummary", safeText(requestSummary, 200));
        addPart(parts, "resultSummary", safeText(resultSummary, 200));
        addPart(parts, "riskFlags", safeText(riskFlags == null ? null : String.valueOf(riskFlags), 200));
        return String.join("; ", parts);
    }

    private void addPart(List<String> parts, String key, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (!text.isEmpty()) {
            parts.add(key + "=" + safeText(text, 200));
        }
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
        if (value == null || "ALL".equalsIgnoreCase(value)) {
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
        if (value == null || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        String normalizedType = BusinessType.normalize(value);
        if (normalizedType == null) {
            throw new BusinessException(400, "businessType不合法");
        }
        return normalizedType;
    }

    private String normalizeOperationSource(String operationSource) {
        String value = trimToNull(operationSource);
        if (value == null || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        String normalized = value.toUpperCase();
        if (!"AI".equals(normalized) && !"MANUAL".equals(normalized)) {
            throw new BusinessException(400, "operationSource不合法");
        }
        return normalized;
    }

    private String normalizeResultStatus(String resultStatus) {
        String value = trimToNull(resultStatus);
        if (value == null || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        String normalized = value.toUpperCase();
        if (!List.of("SUCCESS", "FAILED", "CANCELLED", "FORBIDDEN").contains(normalized)) {
            throw new BusinessException(400, "resultStatus不合法");
        }
        return normalized;
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

    private String operationSource(String operationType) {
        return operationType != null && operationType.startsWith("AI_") ? "AI" : "MANUAL";
    }

    private String inferResultStatus(String operationType) {
        if (operationType == null) {
            return "SUCCESS";
        }
        if (operationType.endsWith("_CANCELLED") || OperationType.AI_WRITE_CANCELLED.name().equals(operationType)
                || OperationType.AI_ACTION_CANCELLED.name().equals(operationType)) {
            return "CANCELLED";
        }
        if (OperationType.AI_FORBIDDEN.name().equals(operationType)) {
            return "FORBIDDEN";
        }
        if (OperationType.AI_ERROR.name().equals(operationType)
                || OperationType.AI_ACTION_CONFIRM_FAILED.name().equals(operationType)) {
            return "FAILED";
        }
        return "SUCCESS";
    }

    private String extractContentValue(String content, String key) {
        if (content == null || key == null) {
            return null;
        }
        String prefix = key + "=";
        for (String part : content.split(";")) {
            String text = part.trim();
            if (text.startsWith(prefix)) {
                return trimToNull(text.substring(prefix.length()));
            }
        }
        return null;
    }

    private Long parseLong(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String firstNonBlank(String first, String second) {
        String value = trimToNull(first);
        return value == null ? trimToNull(second) : value;
    }

    private Long firstNonNull(Long first, Long second) {
        return first == null ? second : first;
    }

    private String safeText(String text, int maxLength) {
        String value = trimToNull(text);
        if (value == null) {
            return null;
        }
        value = SENSITIVE_DETAIL_PATTERN.matcher(value).replaceAll("[SENSITIVE]");
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
