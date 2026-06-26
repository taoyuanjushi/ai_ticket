package com.example.hello_demo.controller;

import com.example.hello_demo.common.PageResult;
import com.example.hello_demo.common.Result;
import com.example.hello_demo.service.OperationLogService;
import com.example.hello_demo.vo.OperationLogVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志接口控制器。
 * 用于分页查询系统操作日志。
 */
@RestController
@RequestMapping("/operation-logs")
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public Result<PageResult<OperationLogVO>> getOperationLogs(
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long size,
            @RequestParam(required = false) Long ticketId,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String businessType) {

        Long normalizedOperatorId = operatorId == null ? userId : operatorId;
        String normalizedAction = action == null ? operationType : action;
        return Result.success(operationLogService.getOperationLogs(
                page,
                size,
                ticketId,
                normalizedOperatorId,
                normalizedAction,
                businessType
        ));
    }
}
