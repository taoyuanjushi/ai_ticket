package com.example.hello_demo.controller;

import com.example.hello_demo.common.Result;
import com.example.hello_demo.dto.LoginRequestDTO;
import com.example.hello_demo.dto.RegisterRequestDTO;
import com.example.hello_demo.service.AuthService;
import com.example.hello_demo.vo.CurrentUserVO;
import com.example.hello_demo.vo.LoginResponseVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口控制器。
 * 负责注册、登录、获取当前登录用户。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<CurrentUserVO> register(@Valid @RequestBody RegisterRequestDTO dto) {
        return Result.success("注册成功", authService.register(dto));
    }

    @PostMapping("/login")
    public Result<LoginResponseVO> login(@Valid @RequestBody LoginRequestDTO dto) {
        return Result.success("登录成功", authService.login(dto));
    }

    @GetMapping("/me")
    public Result<CurrentUserVO> me() {
        return Result.success(authService.getCurrentUser());
    }
}
