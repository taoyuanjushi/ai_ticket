package com.example.hello_demo.controller;

import com.example.hello_demo.config.JwtProperties;
import com.example.hello_demo.exception.GlobalExceptionHandler;
import com.example.hello_demo.security.CurrentUserContext;
import com.example.hello_demo.security.JwtInterceptor;
import com.example.hello_demo.security.JwtUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

abstract class MockMvcIntegrationTestSupport {

    protected final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final JwtUtil jwtUtil = new JwtUtil(testJwtProperties());

    @AfterEach
    void clearCurrentUserContext() {
        CurrentUserContext.clear();
    }

    protected MockMvc mockMvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .addInterceptors(new JwtInterceptor(jwtUtil))
                .build();
    }

    protected String bearerToken(Long userId, String username, String role) {
        return "Bearer " + jwtUtil.generateToken(userId, username, role);
    }

    protected String tomToken() {
        return bearerToken(1L, "tom", "USER");
    }

    protected String staffToken() {
        return bearerToken(7L, "staff", "STAFF");
    }

    protected String adminToken() {
        return bearerToken(9L, "admin", "ADMIN");
    }

    private JwtProperties testJwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-change-me-test-secret-change-me");
        properties.setExpiration(86400000L);
        return properties;
    }
}
