package com.example.hello_demo.client;

import com.example.hello_demo.dto.PythonAgentChatRequestDTO;
import com.example.hello_demo.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 服务客户端。
 * 负责 Java 后端调用 Python FastAPI AI 服务。
 */
@Component
public class AiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.service.base-url}")
    private String aiServiceBaseUrl;

    public AiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Map<String, Object> chat(PythonAgentChatRequestDTO requestDTO) {
        return postForMap("/agent/chat", requestDTO);
    }

    public Map<String, Object> generateReplySuggestion(Long ticketId, String authToken) {
        Map<String, String> body = new HashMap<>();
        body.put("auth_token", authToken);
        return postForMap("/ai/tickets/" + ticketId + "/reply-suggestion", body);
    }

    private Map<String, Object> postForMap(String path, Object requestBody) {
        String url = aiServiceBaseUrl.replaceAll("/+$", "") + path;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Object> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    new ParameterizedTypeReference<>() {
                    }
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BusinessException(500, "AI服务调用失败");
            }

            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new BusinessException(500, "AI服务返回为空");
            }
            return body;
        } catch (HttpStatusCodeException e) {
            throw new BusinessException(
                    mapPythonStatusCode(e.getStatusCode().value()),
                    extractPythonErrorMessage(e)
            );
        } catch (ResourceAccessException e) {
            throw new BusinessException(500, "AI服务连接超时或不可用");
        } catch (RestClientException e) {
            throw new BusinessException(500, "AI服务调用异常");
        }
    }

    private int mapPythonStatusCode(int statusCode) {
        if (statusCode == 401 || statusCode == 403 || statusCode == 404) {
            return statusCode;
        }
        if (statusCode == 400 || statusCode == 422) {
            return 400;
        }
        return statusCode >= 500 ? 500 : statusCode;
    }

    private String extractPythonErrorMessage(HttpStatusCodeException e) {
        String responseBody = e.getResponseBodyAsString();
        String parsedMessage = extractMessageFromJson(responseBody);
        if (parsedMessage != null && !parsedMessage.isBlank()) {
            return parsedMessage;
        }

        int statusCode = e.getStatusCode().value();
        if (statusCode == 401) {
            return "登录状态已失效，请重新登录。";
        }
        if (statusCode == 403) {
            return "你没有权限执行该操作。";
        }
        if (statusCode == 404) {
            return "目标工单不存在，或你无权访问该工单。";
        }
        if (statusCode == 400 || statusCode == 422) {
            return "请求参数不正确。";
        }
        return "服务暂时异常，请稍后重试。";
    }

    private String extractMessageFromJson(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    responseBody,
                    new TypeReference<>() {
                    }
            );
            return firstStringValue(payload, "message", "msg", "error", "detail");
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String firstStringValue(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }
}
