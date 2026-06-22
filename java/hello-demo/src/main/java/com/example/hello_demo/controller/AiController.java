package com.example.hello_demo.controller;

import com.example.hello_demo.common.Result;
import com.example.hello_demo.dto.AiChatRequestDTO;
import com.example.hello_demo.dto.PythonAgentChatRequestDTO;
import com.example.hello_demo.exception.BusinessException;
import com.example.hello_demo.security.CurrentUserContext;
import com.example.hello_demo.service.AiService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * AI 控制器。
 * 对外提供 Java 后端统一 AI 入口。
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    private static final String LOGIN_EXPIRED_MESSAGE = "登录状态已失效，请重新登录。";

    private static final Logger logger = LoggerFactory.getLogger(AiController.class);

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(
            @Valid @RequestBody AiChatRequestDTO requestDTO,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        Long userId = requireCurrentUserId();
        String authToken = requireAuthorization(authorization);
        String conversationId = resolveConversationId(requestDTO.getConversationId(), userId);

        logger.info("Forward AI chat request to Python. userId={}, conversationId={}", userId, conversationId);
        PythonAgentChatRequestDTO pythonRequest = new PythonAgentChatRequestDTO(
                requestDTO.getMessage(),
                conversationId,
                userId,
                authToken
        );
        return Result.success(aiService.chat(pythonRequest));
    }

    @PostMapping("/tickets/{ticketId}/reply-suggestion")
    public Result<Map<String, Object>> generateReplySuggestion(
            @PathVariable Long ticketId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        Long userId = requireCurrentUserId();
        String authToken = requireAuthorization(authorization);

        logger.info("Forward AI reply suggestion request to Python. userId={}, ticketId={}", userId, ticketId);
        return Result.success(aiService.generateReplySuggestion(ticketId, authToken));
    }

    private Long requireCurrentUserId() {
        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, LOGIN_EXPIRED_MESSAGE);
        }
        return userId;
    }

    private String requireAuthorization(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new BusinessException(401, LOGIN_EXPIRED_MESSAGE);
        }
        return authorization;
    }

    private String resolveConversationId(String conversationId, Long userId) {
        if (conversationId != null && !conversationId.isBlank()) {
            return conversationId.trim();
        }
        return "java-" + userId + "-" + UUID.randomUUID();
    }
}
