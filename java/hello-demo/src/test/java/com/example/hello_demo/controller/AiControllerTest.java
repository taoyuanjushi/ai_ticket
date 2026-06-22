package com.example.hello_demo.controller;

import com.example.hello_demo.dto.AiChatRequestDTO;
import com.example.hello_demo.dto.PythonAgentChatRequestDTO;
import com.example.hello_demo.exception.BusinessException;
import com.example.hello_demo.security.CurrentUserContext;
import com.example.hello_demo.service.AiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiControllerTest {

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void chatBuildsPythonRequestFromCurrentJavaUser() {
        AiService aiService = mock(AiService.class);
        when(aiService.chat(any(PythonAgentChatRequestDTO.class))).thenReturn(Map.of("answer", "ok"));
        AiController controller = new AiController(aiService);
        CurrentUserContext.set(7L, "staff", "STAFF");

        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("查询我的工单");
        request.setConversationId(" chat-1 ");

        controller.chat(request, "Bearer token");

        ArgumentCaptor<PythonAgentChatRequestDTO> captor = ArgumentCaptor.forClass(PythonAgentChatRequestDTO.class);
        verify(aiService).chat(captor.capture());
        PythonAgentChatRequestDTO pythonRequest = captor.getValue();
        assertEquals("查询我的工单", pythonRequest.getMessage());
        assertEquals("chat-1", pythonRequest.getConversation_id());
        assertEquals(7L, pythonRequest.getUser_id());
        assertEquals("Bearer token", pythonRequest.getAuth_token());
    }

    @Test
    void missingConversationIdUsesCurrentUserScopedGeneratedId() {
        AiService aiService = mock(AiService.class);
        when(aiService.chat(any(PythonAgentChatRequestDTO.class))).thenReturn(Map.of("answer", "ok"));
        AiController controller = new AiController(aiService);
        CurrentUserContext.set(7L, "staff", "STAFF");

        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("查询我的工单");

        controller.chat(request, "Bearer token");

        ArgumentCaptor<PythonAgentChatRequestDTO> captor = ArgumentCaptor.forClass(PythonAgentChatRequestDTO.class);
        verify(aiService).chat(captor.capture());
        assertTrue(captor.getValue().getConversation_id().startsWith("java-7-"));
    }

    @Test
    void missingAuthorizationReturns401() {
        AiController controller = new AiController(mock(AiService.class));
        CurrentUserContext.set(7L, "staff", "STAFF");
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("查询我的工单");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.chat(request, "")
        );

        assertEquals(401, exception.getCode());
    }

    @Test
    void missingCurrentUserReturns401() {
        AiController controller = new AiController(mock(AiService.class));
        AiChatRequestDTO request = new AiChatRequestDTO();
        request.setMessage("查询我的工单");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.chat(request, "Bearer token")
        );

        assertEquals(401, exception.getCode());
    }
}
