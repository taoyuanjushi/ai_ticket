package com.example.hello_demo.controller;

import com.example.hello_demo.common.PageResult;
import com.example.hello_demo.common.Result;
import com.example.hello_demo.dto.AiReplyCreateDTO;
import com.example.hello_demo.dto.TicketCreateDTO;
import com.example.hello_demo.dto.TicketQueryRequest;
import com.example.hello_demo.dto.TicketStatusUpdateDTO;
import com.example.hello_demo.dto.TicketUpdateRequest;
import com.example.hello_demo.entity.Ticket;
import com.example.hello_demo.entity.TicketReply;
import com.example.hello_demo.service.TicketReplyService;
import com.example.hello_demo.service.TicketService;
import com.example.hello_demo.vo.TicketDetailVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final TicketReplyService ticketReplyService;

    public TicketController(TicketService ticketService, TicketReplyService ticketReplyService) {
        this.ticketService = ticketService;
        this.ticketReplyService = ticketReplyService;
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
            @RequestParam(required = false) String keyword) {

        TicketQueryRequest request = new TicketQueryRequest();
        request.setPage(page);
        request.setSize(size);
        request.setStatus(status);
        request.setPriority(priority);
        request.setCategory(category);
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
     * 保存 AI 回复建议。
     */
    @PostMapping("/{id}/ai-replies")
    public Result<TicketReply> createAiReply(
            @PathVariable Long id,
            @Valid @RequestBody AiReplyCreateDTO dto) {

        TicketReply reply = ticketReplyService.createAiReply(id, dto);
        return Result.success("AI 回复建议已保存", reply);
    }

    /**
     * 删除工单。
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteTicket(@PathVariable Long id) {
        return Result.success(ticketService.deleteTicket(id));
    }
}
