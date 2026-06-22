# User 模块请求流程笔记

## 1. User 模块已实现接口

| 功能 | 请求方式 | 路径 | 说明 |
|---|---|---|---|
| 查询用户列表 | GET | `/users` | 查询所有用户 |
| 查询用户详情 | GET | `/users/{id}` | 根据 ID 查询用户 |
| 新增用户 | POST | `/users` | 新增用户 |
| 修改用户 | PUT | `/users/{id}` | 根据 ID 修改用户 |
| 删除用户 | DELETE | `/users/{id}` | 根据 ID 删除用户 |

## 2. 查询用户列表请求流程

以前端或 Postman 请求 `GET /users` 为例：

1. 浏览器或 Postman 发送 `GET /users` 请求。
2. Tomcat 接收 HTTP 请求。
3. Spring MVC 的 DispatcherServlet 根据路径找到 `UserController#getUsers()`。
4. `UserController` 调用 `UserService#getUsers()`。
5. `UserService` 调用 `UserMapper#selectList(null)`。
6. `UserMapper` 查询 MySQL 中的 `user` 表。
7. MySQL 返回用户数据。
8. 数据沿着 Mapper -> Service -> Controller 返回。
9. Controller 使用 `Result.success(data)` 包装响应。
10. Spring Boot 将 Java 对象转换成 JSON 返回给前端。

## 3. 新增用户请求流程

以前端或 Postman 请求 `POST /users` 为例：

1. 前端发送 JSON 请求体。
2. Controller 使用 `@RequestBody` 将 JSON 转换成 `User` 对象。
3. `@Valid` 根据 User 类上的校验注解进行参数校验。
4. 如果参数不合法，抛出参数校验异常。
5. `GlobalExceptionHandler` 捕获异常，并返回友好提示。
6. 如果参数合法，Controller 调用 `UserService#createUser()`。
7. Service 将 `id` 设置为 `null`，让数据库自动生成主键。
8. Service 调用 `UserMapper#insert(user)`。
9. MySQL 插入用户数据。
10. Controller 返回 `Result.success("创建成功", createdUser)`。

## 4. 修改用户请求流程

以前端或 Postman 请求 `PUT /users/{id}` 为例：

1. 前端发送 `PUT /users/1` 请求，并携带 JSON 请求体。
2. Controller 使用 `@PathVariable` 获取路径中的用户 ID。
3. Controller 使用 `@RequestBody` 接收请求体。
4. `@Valid` 对请求体进行参数校验。
5. Controller 调用 `UserService#updateUser(id, user)`。
6. Service 根据 id 查询用户是否存在。
7. 如果用户不存在，Service 抛出 `BusinessException(404, "用户不存在")`。
8. `GlobalExceptionHandler` 捕获业务异常，返回统一错误响应。
9. 如果用户存在，Service 将路径参数 id 设置到 user 对象中。
10. Service 调用 `UserMapper#updateById(user)` 修改数据库。
11. Service 查询并返回修改后的用户。
12. Controller 返回 `Result.success("修改成功", updatedUser)`。

## 5. 删除用户请求流程

以前端或 Postman 请求 `DELETE /users/{id}` 为例：

1. 前端发送 `DELETE /users/1` 请求。
2. Controller 使用 `@PathVariable` 获取路径中的用户 ID。
3. Controller 调用 `UserService#deleteUser(id)`。
4. Service 调用 Mapper 删除用户。
5. 如果影响行数为 0，说明用户不存在，抛出 `BusinessException`。
6. 如果删除成功，Controller 返回 `Result.success("删除成功", null)`。

## 6. User 模块涉及的核心注解

- `@RestController`：声明当前类是接口控制器，返回 JSON 数据。
- `@RequestMapping("/users")`：声明 User 模块统一路径前缀。
- `@GetMapping`：处理 GET 请求，常用于查询。
- `@PostMapping`：处理 POST 请求，常用于新增。
- `@PutMapping`：处理 PUT 请求，常用于修改。
- `@DeleteMapping`：处理 DELETE 请求，常用于删除。
- `@PathVariable`：接收路径参数，例如 `/users/1` 中的 `1`。
- `@RequestBody`：接收 JSON 请求体，并转换成 Java 对象。
- `@Valid`：触发参数校验。
- `@Service`：声明业务逻辑层组件。
- `@Mapper`：声明数据库访问层组件。

## 7. 当前 User 模块分层职责

- `UserController`：接收 HTTP 请求，调用 Service，返回统一 Result。
- `UserService`：处理业务逻辑，例如判断用户是否存在、调用 Mapper。
- `UserMapper`：负责操作 MySQL 数据库。
- `User`：用户实体类，对应数据库中的 user 表。
- `Result`：统一接口返回格式。
- `BusinessException`：表示业务异常，例如用户不存在。
- `GlobalExceptionHandler`：统一捕获异常，并返回统一错误响应。

## 8. 复盘总结

User 模块的完整请求流程是：前端或 Postman 发送请求，Tomcat 接收请求，Spring MVC 找到对应 Controller 方法，Controller 调用 Service，Service 调用 Mapper 操作 MySQL，最后通过 Result 统一包装响应返回给前端。如果过程中出现参数错误或业务错误，会由 GlobalExceptionHandler 统一捕获并返回友好提示。
