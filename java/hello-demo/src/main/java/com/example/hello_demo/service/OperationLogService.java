package com.example.hello_demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hello_demo.common.PageResult;
import com.example.hello_demo.entity.OperationLog;
import com.example.hello_demo.enums.BusinessType;
import com.example.hello_demo.enums.OperationType;
import com.example.hello_demo.exception.BusinessException;
import com.example.hello_demo.mapper.OperationLogMapper;
import com.example.hello_demo.security.CurrentUserContext;
import com.example.hello_demo.security.PermissionUtil;
import org.springframework.stereotype.Service;

/**
 * 操作日志业务逻辑层。
 * 负责记录关键业务操作，并提供管理员分页查询能力。
 */
@Service
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public OperationLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    /**
     * 记录指定用户的操作日志。
     */
    public void record(Long userId, String operationType, String businessType, Long businessId, String content) {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setOperationType(requireOperationType(operationType));
        log.setBusinessType(requireBusinessType(businessType));
        log.setBusinessId(businessId);
        log.setContent(requireContent(content));

        operationLogMapper.insert(log);
    }

    /**
     * 记录当前登录用户的操作日志，当前用户由 JWT 拦截器写入 CurrentUserContext。
     */
    public void record(String operationType, String businessType, Long businessId, String content) {
        Long currentUserId = CurrentUserContext.getUserId();
        record(currentUserId, operationType, businessType, businessId, content);
    }

    /**
     * 分页查询操作日志。操作日志属于审计数据，只允许 ADMIN 查看。
     */
    public PageResult<OperationLog> getOperationLogs(Long page, Long size, Long userId, String operationType, String businessType) {
        PermissionUtil.requireAdmin();

        Long normalizedPage = normalizePage(page);
        Long normalizedSize = normalizeSize(size);
        String normalizedOperationType = normalizeOperationType(operationType);
        String normalizedBusinessType = normalizeBusinessType(businessType);

        if (userId != null && userId < 1) {
            throw new BusinessException(400, "userId不能小于1");
        }

        Page<OperationLog> pageParam = new Page<>(normalizedPage, normalizedSize);
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();

        if (userId != null) {
            wrapper.eq(OperationLog::getUserId, userId);
        }
        if (normalizedOperationType != null) {
            wrapper.eq(OperationLog::getOperationType, normalizedOperationType);
        }
        if (normalizedBusinessType != null) {
            wrapper.eq(OperationLog::getBusinessType, normalizedBusinessType);
        }

        wrapper.orderByDesc(OperationLog::getCreatedAt);

        Page<OperationLog> resultPage = operationLogMapper.selectPage(pageParam, wrapper);
        return PageResult.of(
                resultPage.getRecords(),
                resultPage.getTotal(),
                resultPage.getCurrent(),
                resultPage.getSize()
        );
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
            return 10L;
        }
        if (size < 1) {
            throw new BusinessException(400, "size不能小于1");
        }
        if (size > 100) {
            throw new BusinessException(400, "size不能大于100");
        }
        return size;
    }

    private String normalizeOperationType(String operationType) {
        if (operationType == null || operationType.isBlank()) {
            return null;
        }
        String normalizedType = OperationType.normalize(operationType);
        if (normalizedType == null) {
            throw new BusinessException(400, "operationType不合法");
        }
        return normalizedType;
    }

    private String normalizeBusinessType(String businessType) {
        if (businessType == null || businessType.isBlank()) {
            return null;
        }
        String normalizedType = BusinessType.normalize(businessType);
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
        if (content == null || content.isBlank()) {
            throw new BusinessException(500, "操作日志内容不能为空");
        }
        return content.trim();
    }
}
