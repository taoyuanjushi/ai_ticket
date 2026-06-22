package com.example.hello_demo.exception;

import com.example.hello_demo.common.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionUsesSameHttpStatusAndResultCode() {
        assertBusinessExceptionStatus(401);
        assertBusinessExceptionStatus(403);
        assertBusinessExceptionStatus(404);
        assertBusinessExceptionStatus(400);
    }

    @Test
    void unexpectedExceptionReturnsHttp500AndStableMessage() {
        ResponseEntity<Result<Void>> response = handler.handleException(new RuntimeException("boom"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals(500, response.getBody().getCode());
        assertEquals("系统异常", response.getBody().getMessage());
    }

    private void assertBusinessExceptionStatus(int code) {
        ResponseEntity<Result<Void>> response = handler.handleBusinessException(
                new BusinessException(code, "message-" + code)
        );

        assertEquals(code, response.getStatusCode().value());
        assertEquals(code, response.getBody().getCode());
        assertEquals("message-" + code, response.getBody().getMessage());
    }
}
