# Ticket 工单接口说明

本文档说明当前 Spring Boot 项目中的 Ticket 工单模块接口、请求示例、响应示例和 Postman 测试步骤。

## 一、模块结构

当前 Ticket 模块位于基础包 `com.example.hello_demo` 下：

- `entity/Ticket.java`：工单实体，对应数据库 `ticket` 表。
- `mapper/TicketMapper.java`：工单数据库访问层，继承 MyBatis-Plus `BaseMapper<Ticket>`。
- `service/TicketService.java`：工单业务逻辑层，负责查询、新增、修改、删除和业务校验。
- `controller/TicketController.java`：工单 HTTP 接口层，统一返回 `Result`。
- `config/MyBatisPlusConfig.java`：MyBatis-Plus 配置类，开启分页插件。
- `dto/TicketQueryRequest.java`：工单分页条件查询参数对象。
- `dto/TicketUpdateRequest.java`：修改工单请求对象，只接收允许修改的字段。

当前项目使用 MyBatis-Plus `Page` 和 `LambdaQueryWrapper` 完成分页与条件查询，没有使用 `TicketMapper.xml`。

## 二、数据表字段

`ticket` 表字段如下：

| 字段 | 说明 |
| --- | --- |
| `id` | 工单主键，自增 |
| `title` | 工单标题 |
| `content` | 工单内容 |
| `status` | 工单状态 |
| `priority` | 工单优先级 |
| `category` | 工单分类 |
| `user_id` | 提交工单的用户 ID |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

当前接口字段使用 `content` 表示工单描述。修改接口也兼容请求中的 `description` 字段，会映射到 `content`。

## 三、统一响应格式

接口统一返回 `Result`：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

业务异常和参数校验失败也会返回统一格式：

```json
{
  "code": 400,
  "message": "错误信息",
  "data": null
}
```

## 四、接口列表

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/tickets` | 新增工单 |
| `GET` | `/tickets` | 查询工单列表 |
| `GET` | `/tickets/{id}` | 查询工单详情 |
| `PUT` | `/tickets/{id}` | 修改工单 |
| `DELETE` | `/tickets/{id}` | 删除工单 |

## 五、新增工单

### 请求

```http
POST /tickets
Content-Type: application/json
```

```json
{
  "title": "登录失败",
  "content": "用户反馈登录系统时提示账号不存在",
  "priority": "HIGH",
  "status": "OPEN",
  "category": "ACCOUNT",
  "userId": 1
}
```

`status`、`priority`、`category` 不传时，Service 会分别设置默认值 `OPEN`、`MEDIUM`、`OTHER`。

### 响应

```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "id": 1,
    "title": "登录失败",
    "content": "用户反馈登录系统时提示账号不存在",
    "status": "OPEN",
    "priority": "HIGH",
    "category": "ACCOUNT",
    "userId": 1,
    "createdAt": "2026-06-09T17:00:00",
    "updatedAt": "2026-06-09T17:00:00"
  }
}
```

## 六、查询工单列表

### 请求

```http
GET /tickets
```

支持分页和条件查询：

```http
GET /tickets?page=1&size=10&status=OPEN&priority=HIGH&category=ACCOUNT&keyword=登录
```

请求参数：

| 参数 | 说明 |
| --- | --- |
| `page` | 页码，默认 `1`，不能小于 `1` |
| `size` | 每页条数，默认 `10`，不能小于 `1`，最大 `100` |
| `status` | 工单状态，可选值：`OPEN`、`PROCESSING`、`DONE`、`CLOSED` |
| `priority` | 工单优先级，可选值：`LOW`、`MEDIUM`、`HIGH`、`URGENT` |
| `category` | 工单分类，例如 `ACCOUNT`、`SYSTEM` |
| `keyword` | 关键字，模糊匹配 `title` 或 `content` |

### 响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "title": "登录失败",
        "content": "用户反馈登录系统时提示账号不存在",
        "status": "OPEN",
        "priority": "HIGH",
        "category": "ACCOUNT",
        "userId": 1,
        "createdAt": "2026-06-09T17:00:00",
        "updatedAt": "2026-06-09T17:00:00"
      }
    ],
    "total": 1,
    "page": 1,
    "size": 10
  }
}
```

## 七、查询工单详情

### 请求

```http
GET /tickets/1
```

### 响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "title": "登录失败",
    "content": "用户反馈登录系统时提示账号不存在",
    "status": "OPEN",
    "priority": "HIGH",
    "category": "ACCOUNT",
    "userId": 1,
    "createdAt": "2026-06-09T17:00:00",
    "updatedAt": "2026-06-09T17:00:00"
  }
}
```

如果工单不存在：

```json
{
  "code": 404,
  "message": "工单不存在",
  "data": null
}
```

## 八、修改工单

### 请求

```http
PUT /tickets/1
Content-Type: application/json
```

推荐使用当前项目字段名 `content`：

```json
{
  "title": "登录问题已更新",
  "content": "用户反馈登录时偶发失败，已补充错误截图",
  "priority": "HIGH",
  "status": "PROCESSING",
  "category": "ACCOUNT"
}
```

也兼容使用 `description`：

```json
{
  "title": "登录问题已更新",
  "description": "用户反馈登录时偶发失败，已补充错误截图",
  "priority": "HIGH",
  "status": "PROCESSING"
}
```

### 校验规则

- `title` 不能为空，错误提示：`工单标题不能为空`
- `content` 或 `description` 不能为空，错误提示：`工单描述不能为空`
- `priority` 不能为空，允许值：`LOW`、`MEDIUM`、`HIGH`、`URGENT`
- `status` 不能为空，允许值：`OPEN`、`PROCESSING`、`DONE`、`CLOSED`

### 响应

```json
{
  "code": 200,
  "message": "success",
  "data": true
}
```

修改时以路径中的 `id` 为准，不允许通过请求体修改 `id`。`created_at` 不会被修改，`updated_at` 会更新。

## 九、删除工单

### 请求

```http
DELETE /tickets/1
```

当前 `ticket` 表没有 `deleted` 或 `is_deleted` 字段，因此删除接口执行物理删除。

### 响应

```json
{
  "code": 200,
  "message": "success",
  "data": true
}
```

如果工单不存在：

```json
{
  "code": 404,
  "message": "工单不存在",
  "data": null
}
```

## 十、常见错误

| 场景 | 返回信息 |
| --- | --- |
| 工单不存在 | `工单不存在` |
| 标题为空 | `工单标题不能为空` |
| 描述为空 | `工单描述不能为空` |
| 优先级为空 | `工单优先级不能为空` |
| 状态为空 | `工单状态不能为空` |
| 优先级不合法 | `工单优先级不合法` |
| 状态不合法 | `工单状态不合法` |
| page 小于 1 | `page不能小于1` |
| size 小于 1 | `size不能小于1` |
| size 大于 100 | `size不能大于100` |
| page 或 size 不是数字 | `page参数格式不正确` 或 `size参数格式不正确` |
| 修改失败 | `工单修改失败` |
| 删除失败 | `工单删除失败` |

## 十一、Postman 测试步骤

启动项目：

```powershell
cd D:\code\java\hello-demo
.\mvnw.cmd spring-boot:run
```

### 1. 新增工单

- Method: `POST`
- URL: `http://localhost:8080/tickets`
- Body: `raw` -> `JSON`

```json
{
  "title": "Postman 测试工单",
  "content": "这是用于测试修改和删除接口的工单",
  "priority": "MEDIUM",
  "status": "OPEN",
  "category": "TEST",
  "userId": 1
}
```

记录响应中的 `data.id`。

### 2. 分页和条件查询

- Method: `GET`
- URL: `http://localhost:8080/tickets?page=1&size=10`

按状态查询：

```text
http://localhost:8080/tickets?page=1&size=10&status=OPEN
```

按优先级查询：

```text
http://localhost:8080/tickets?page=1&size=10&priority=HIGH
```

按分类查询：

```text
http://localhost:8080/tickets?page=1&size=10&category=ACCOUNT
```

关键词搜索：

```text
http://localhost:8080/tickets?page=1&size=10&keyword=登录
```

多条件组合查询：

```text
http://localhost:8080/tickets?page=1&size=10&status=OPEN&priority=HIGH&keyword=登录
```

预期响应中的 `data` 包含 `records`、`total`、`page`、`size`。

### 3. 查询详情

- Method: `GET`
- URL: `http://localhost:8080/tickets/{id}`

确认能查到刚新增的工单。

### 4. 修改工单

- Method: `PUT`
- URL: `http://localhost:8080/tickets/{id}`
- Body: `raw` -> `JSON`

```json
{
  "title": "Postman 测试工单已更新",
  "content": "修改接口已更新工单内容",
  "priority": "HIGH",
  "status": "PROCESSING",
  "category": "TEST"
}
```

预期返回 `data: true`。再次查询详情，确认字段已更新。

### 5. 修改不存在工单

- Method: `PUT`
- URL: `http://localhost:8080/tickets/999999`
- Body: `raw` -> `JSON`

```json
{
  "title": "不存在工单",
  "content": "测试不存在工单",
  "priority": "HIGH",
  "status": "PROCESSING"
}
```

预期返回 `工单不存在`。

### 6. 修改参数错误

- Method: `PUT`
- URL: `http://localhost:8080/tickets/{id}`
- Body: `raw` -> `JSON`

```json
{
  "title": "",
  "content": "测试描述",
  "priority": "HIGH",
  "status": "PROCESSING"
}
```

预期返回 `工单标题不能为空`。

### 7. 删除工单

- Method: `DELETE`
- URL: `http://localhost:8080/tickets/{id}`

预期返回 `data: true`。

### 8. 删除后再次查询

- Method: `GET`
- URL: `http://localhost:8080/tickets/{id}`

预期返回 `工单不存在`。

### 9. 删除不存在工单

- Method: `DELETE`
- URL: `http://localhost:8080/tickets/999999`

预期返回 `工单不存在`。
