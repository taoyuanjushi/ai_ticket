package com.example.hello_demo.controller;

import com.example.hello_demo.common.Result;
import com.example.hello_demo.entity.Ticket;
import com.example.hello_demo.entity.TicketReply;
import com.example.hello_demo.entity.User;
import com.example.hello_demo.service.UserService;
import com.example.hello_demo.vo.UserInfoVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户接口控制器。
 * 负责接收 HTTP 请求，调用 UserService，并返回统一 Result 格式。
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Result<List<User>> getUsers() {
        return Result.success(userService.getUsers());
    }

    @GetMapping("/{id}")
    public Result<UserInfoVO> getUserById(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }

    @GetMapping("/{userId}/tickets")
    public Result<List<Ticket>> getTicketsByUserId(@PathVariable Long userId) {
        return Result.success(userService.getTicketsByUserId(userId));
    }

    @GetMapping("/{userId}/ticket-replies")
    public Result<List<TicketReply>> getTicketRepliesByUserId(@PathVariable Long userId) {
        return Result.success(userService.getTicketRepliesByUserId(userId));
    }

    @PostMapping
    public Result<User> createUser(@Valid @RequestBody User user) {
        User createdUser = userService.createUser(user);
        return Result.success("创建成功", createdUser);
    }

    @PutMapping("/{id}")
    public Result<User> updateUser(@PathVariable Long id, @Valid @RequestBody User user) {
        User updatedUser = userService.updateUser(id, user);
        return Result.success("修改成功", updatedUser);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success("删除成功", null);
    }
}
