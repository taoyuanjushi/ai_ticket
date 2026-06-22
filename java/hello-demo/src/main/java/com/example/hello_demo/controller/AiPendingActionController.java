package com.example.hello_demo.controller;

import com.example.hello_demo.common.Result;
import com.example.hello_demo.dto.AiPendingActionConfirmResponse;
import com.example.hello_demo.dto.AiPendingActionConversationRequest;
import com.example.hello_demo.dto.AiPendingActionCreateRequest;
import com.example.hello_demo.dto.AiPendingActionResponse;
import com.example.hello_demo.service.AiPendingActionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 待确认动作接口。
 */
@RestController
@RequestMapping("/ai/pending-actions")
public class AiPendingActionController {

    private final AiPendingActionService aiPendingActionService;

    public AiPendingActionController(AiPendingActionService aiPendingActionService) {
        this.aiPendingActionService = aiPendingActionService;
    }

    @PostMapping
    public Result<AiPendingActionResponse> createPendingAction(
            @Valid @RequestBody AiPendingActionCreateRequest request) {

        return Result.success(aiPendingActionService.createPendingAction(request));
    }

    @GetMapping("/current")
    public Result<AiPendingActionResponse> getCurrentPendingAction(
            @RequestParam String conversationId) {

        return Result.success(aiPendingActionService.getCurrentPendingAction(conversationId));
    }

    @PostMapping("/confirm")
    public Result<AiPendingActionConfirmResponse> confirmPendingAction(
            @Valid @RequestBody AiPendingActionConversationRequest request) {

        return Result.success(aiPendingActionService.confirmPendingAction(request.getConversationId()));
    }

    @PostMapping("/cancel")
    public Result<AiPendingActionResponse> cancelPendingAction(
            @Valid @RequestBody AiPendingActionConversationRequest request) {

        return Result.success(aiPendingActionService.cancelPendingAction(request.getConversationId()));
    }
}
