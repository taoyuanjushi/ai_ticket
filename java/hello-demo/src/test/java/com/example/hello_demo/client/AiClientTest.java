package com.example.hello_demo.client;

import com.example.hello_demo.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiClientTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void python403MessagePassesThrough() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AiClient aiClient = aiClient(restTemplate);
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                HttpHeaders.EMPTY,
                "{\"message\":\"你没有权限执行该操作。\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenThrow(exception);

        BusinessException businessException = assertThrows(
                BusinessException.class,
                () -> aiClient.generateReplySuggestion(3L, "Bearer token")
        );

        assertEquals(403, businessException.getCode());
        assertEquals("你没有权限执行该操作。", businessException.getMessage());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void python404FallbackMessageIsUserFriendly() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AiClient aiClient = aiClient(restTemplate);
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "Not Found",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenThrow(exception);

        BusinessException businessException = assertThrows(
                BusinessException.class,
                () -> aiClient.generateReplySuggestion(404L, "Bearer token")
        );

        assertEquals(404, businessException.getCode());
        assertEquals("目标工单不存在，或你无权访问该工单。", businessException.getMessage());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void aiConnectionFailureReturnsStableBusinessError() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AiClient aiClient = aiClient(restTemplate);
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new ResourceAccessException("timeout"));

        BusinessException businessException = assertThrows(
                BusinessException.class,
                () -> aiClient.generateReplySuggestion(3L, "Bearer token")
        );

        assertEquals(500, businessException.getCode());
        assertEquals("AI服务连接超时或不可用", businessException.getMessage());
    }

    private AiClient aiClient(RestTemplate restTemplate) {
        AiClient aiClient = new AiClient(restTemplate);
        ReflectionTestUtils.setField(aiClient, "aiServiceBaseUrl", "http://127.0.0.1:8001/");
        return aiClient;
    }
}
