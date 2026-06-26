package com.example.hello_demo.controller;

import com.example.hello_demo.common.PageResult;
import com.example.hello_demo.common.Result;
import com.example.hello_demo.dto.AiCategoryPendingRequest;
import com.example.hello_demo.dto.AiPendingConfirmationResponse;
import com.example.hello_demo.dto.AiReplyCreateDTO;
import com.example.hello_demo.dto.AiReplyPendingRequest;
import com.example.hello_demo.dto.TicketAssigneeUpdateRequest;
import com.example.hello_demo.dto.TicketCategoryUpdateRequest;
import com.example.hello_demo.dto.TicketCreateDTO;
import com.example.hello_demo.dto.TicketQueryRequest;
import com.example.hello_demo.dto.TicketStatusUpdateDTO;
import com.example.hello_demo.dto.TicketUpdateRequest;
import com.example.hello_demo.entity.Ticket;
import com.example.hello_demo.entity.TicketReply;
import com.example.hello_demo.exception.BusinessException;
import com.example.hello_demo.service.AiPendingActionService;
import com.example.hello_demo.service.OperationLogService;
import com.example.hello_demo.service.TicketService;
import com.example.hello_demo.vo.OperationLogVO;
import com.example.hello_demo.vo.TicketDetailVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工单接口控制器。
 * 负责接收前端或 Postman 的工单请求，调用 TicketService，并返回统一 Result 响应。
 */
@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final AiPendingActionService aiPendingActionService;
    private final OperationLogService operationLogService;

    public TicketController(
            TicketService ticketService,
            AiPendingActionService aiPendingActionService,
            OperationLogService operationLogService) {
        this.ticketService = ticketService;
        this.aiPendingActionService = aiPendingActionService;
        this.operationLogService = operationLogService;
    }

    /**
     * 分页查询工单列表。
     */
    @GetMapping
    public Result<PageResult<Ticket>> getTickets(
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) String keyword) {

        TicketQueryRequest request = new TicketQueryRequest();
        request.setPage(page);
        request.setSize(size);
        request.setStatus(status);
        request.setPriority(priority);
        request.setCategory(category);
        request.setAssignedTo(assignedTo);
        request.setKeyword(keyword);

        return Result.success(ticketService.getTickets(request));
    }

    /**
     * 根据 ID 查询工单详情。
     */
    @GetMapping("/{id}")
    public Result<Ticket> getTicketById(@PathVariable Long id) {
        return Result.success(ticketService.getTicketById(id));
    }

    /**
     * 查询工单详情，包括工单、提交人和回复列表。
     */
    @GetMapping("/{id}/detail")
    public Result<TicketDetailVO> getTicketDetail(@PathVariable Long id) {
        return Result.success(ticketService.getTicketDetail(id));
    }

    @GetMapping("/{id}/logs")
    public Result<PageResult<OperationLogVO>> getTicketLogs(
            @PathVariable Long id,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long size) {

        return Result.success(operationLogService.getTicketLogs(id, page, size));
    }

    /**
     * 新增工单。
     */
    @PostMapping
    public Result<Ticket> createTicket(@Valid @RequestBody TicketCreateDTO dto) {
        Ticket createdTicket = ticketService.createTicket(dto);
        return Result.success("创建成功", createdTicket);
    }

    /**
     * 修改工单。
     */
    @PutMapping("/{id}")
    public Result<Boolean> updateTicket(@PathVariable Long id, @Valid @RequestBody TicketUpdateRequest request) {
        return Result.success(ticketService.updateTicket(id, request));
    }

    /**
     * 修改工单状态。
     */
    @PutMapping("/{id}/status")
    public Result<Ticket> updateTicketStatus(
            @PathVariable Long id,
            @Valid @RequestBody TicketStatusUpdateDTO dto) {

        Ticket updatedTicket = ticketService.updateTicketStatus(id, dto.getStatus());
        return Result.success("状态修改成功", updatedTicket);
    }

    /**
     * 修改工单分类。
     */
    @PatchMapping("/{id}/category")
    public Result<Ticket> updateTicketCategory(
            @PathVariable Long id,
            @Valid @RequestBody TicketCategoryUpdateRequest request) {

        Ticket updatedTicket = ticketService.updateTicketCategory(id, request);
        return Result.success("分类修改成功", updatedTicket);
    }

    /**
     * 分配或取消工单处理人。
     */
    @PatchMapping("/{id}/assignee")
    public Result<Ticket> updateTicketAssignee(
            @PathVariable Long id,
            @Valid @RequestBody TicketAssigneeUpdateRequest request) {

        Ticket updatedTicket = ticketService.updateTicketAssignee(id, request);
        return Result.success("处理人修改成功", updatedTicket);
    }

    /**
     * 保存 AI 回复建议。
     */
    @PostMapping("/{id}/ai-replies")
    public Result<TicketReply> createAiReply(
            @PathVariable Long id,
            @Valid @RequestBody AiReplyCreateDTO dto) {

        throw new BusinessException(400, "保存 AI 回复需要先创建 pending_action 并确认。");
    }

    /**
     * 创建保存 AI 回复建议的待确认动作。
     */
    @PostMapping("/{id}/ai-replies/pending")
    public Result<AiPendingConfirmationResponse> createAiReplyPending(
            @PathVariable Long id,
            @Valid @RequestBody AiReplyPendingRequest request) {

        return Result.success(aiPendingActionService.createSaveAiReplyPending(id, request));
    }

    /**
     * 创建采纳 AI 分类建议的待确认动作。
     */
    @PostMapping("/{id}/category/pending")
    public Result<AiPendingConfirmationResponse> createCategoryPending(
            @PathVariable Long id,
            @Valid @RequestBody AiCategoryPendingRequest request) {

        return Result.success(aiPendingActionService.createApplyCategoryPending(id, request));
    }

    /**
     * 删除工单。
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteTicket(@PathVariable Long id) {
        return Result.success(ticketService.deleteTicket(id));
    }
}
