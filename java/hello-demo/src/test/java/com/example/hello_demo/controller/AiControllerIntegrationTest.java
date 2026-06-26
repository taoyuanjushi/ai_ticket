package com.example.hello_demo.controller;

import com.example.hello_demo.dto.PythonAgentChatRequestDTO;
import com.example.hello_demo.service.AiService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiControllerIntegrationTest extends MockMvcIntegrationTestSupport {

    @Test
    void bodyUserIdCannotImpersonateAnotherUser() throws Exception {
        AiService aiService = mock(AiService.class);
        when(aiService.chat(any(PythonAgentChatRequestDTO.class))).thenReturn(Map.of("answer", "ok"));
        MockMvc mockMvc = mockMvc(new AiController(aiService));
        String token = tomToken();

        mockMvc.perform(post("/ai/chat")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "查询我的工单",
                                  "conversationId": "impersonate-test",
                                  "userId": 999,
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("ok"));

        ArgumentCaptor<PythonAgentChatRequestDTO> captor = ArgumentCaptor.forClass(PythonAgentChatRequestDTO.class);
        verify(aiService).chat(captor.capture());
        assertEquals(1L, captor.getValue().getUser_id());
        assertEquals("impersonate-test", captor.getValue().getConversation_id());
        assertEquals(token, captor.getValue().getAuth_token());
    }
}
