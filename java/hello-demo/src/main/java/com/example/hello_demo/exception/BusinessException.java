package com.example.hello_demo.exception;

/**
 * 业务异常类。
 * 用于表示用户不存在、参数不合法、业务操作失败等可预期异常。
 */
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
