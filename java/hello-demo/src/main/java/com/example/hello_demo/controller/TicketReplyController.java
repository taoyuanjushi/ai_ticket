package com.example.hello_demo.controller;

import com.example.hello_demo.common.Result;
import com.example.hello_demo.dto.TicketReplyCreateDTO;
import com.example.hello_demo.entity.TicketReply;
import com.example.hello_demo.service.TicketReplyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工单回复接口控制器。
 * 负责接收工单回复相关请求，并返回统一 Result 响应。
 */
@RestController
@RequestMapping("/tickets/{ticketId}/replies")
public class TicketReplyController {

    private final TicketReplyService ticketReplyService;

    public TicketReplyController(TicketReplyService ticketReplyService) {
        this.ticketReplyService = ticketReplyService;
    }

    @PostMapping
    public Result<TicketReply> createReply(
            @PathVariable Long ticketId,
            @Valid @RequestBody TicketReplyCreateDTO dto) {

        TicketReply reply = ticketReplyService.createReply(ticketId, dto);
        return Result.success("回复成功", reply);
    }

    @GetMapping
    public Result<List<TicketReply>> getRepliesByTicketId(@PathVariable Long ticketId) {
        return Result.success(ticketReplyService.getRepliesByTicketId(ticketId));
    }
}
