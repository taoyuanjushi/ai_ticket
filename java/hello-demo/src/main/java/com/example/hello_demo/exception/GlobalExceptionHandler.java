package com.example.hello_demo.exception;

import com.example.hello_demo.common.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理器。
 * 统一捕获 Controller 层抛出的异常，并返回统一 Result 格式。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        int status = normalizeHttpStatus(e.getCode());
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 处理 @Valid 参数校验异常。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = "参数校验失败";
        FieldError fieldError = e.getBindingResult().getFieldError();

        if (fieldError != null) {
            if (fieldError.isBindingFailure()) {
                message = fieldError.getField() + "参数格式不正确";
            } else {
                message = fieldError.getDefaultMessage();
            }
        }

        return ResponseEntity.badRequest().body(Result.fail(400, message));
    }

    /**
     * 处理 GET 查询参数绑定异常，例如 page=abc。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException e) {
        String message = "请求参数格式不正确";
        FieldError fieldError = e.getBindingResult().getFieldError();

        if (fieldError != null) {
            message = fieldError.getField() + "参数格式不正确";
        }

        return ResponseEntity.badRequest().body(Result.fail(400, message));
    }

    /**
     * 处理路径参数或单个请求参数类型不匹配异常。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(Result.fail(400, e.getName() + "参数格式不正确"));
    }

    /**
     * 处理其他未知系统异常。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        return ResponseEntity.internalServerError().body(Result.fail(500, "系统异常"));
    }

    private int normalizeHttpStatus(Integer code) {
        if (code == null) {
            return 500;
        }
        if (code >= 400 && code <= 599) {
            return code;
        }
        return 500;
    }
}
