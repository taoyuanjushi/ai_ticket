# 操作日志模块说明

本文用初学者能理解的方式，解释当前 Spring Boot 工单项目中的操作日志模块。

当前项目已经有这些核心文件：

- `entity/OperationLog.java`：操作日志实体类，对应数据库 `operation_log` 表。
- `mapper/OperationLogMapper.java`：操作日志数据库访问层。
- `service/OperationLogService.java`：操作日志业务层，负责记录日志和分页查询日志。
- `controller/OperationLogController.java`：操作日志查询接口。
- `enums/OperationType.java`：操作类型枚举。
- `enums/BusinessType.java`：业务类型枚举。

## 1. 操作日志模块解决什么问题？

操作日志模块用来记录系统里发生过的重要操作。

比如：

- 谁创建了一张工单；
- 谁回复了一张工单；
- 谁把工单状态从 `OPEN` 改成 `PROCESSING`；
- 谁删除了一张工单；
- 谁登录成功；
- 谁登录失败。

如果没有操作日志，系统只知道“现在是什么状态”，但不知道“这个状态是怎么变成这样的”。

例如某张工单突然被关闭了：

- 没有操作日志：只能看到工单现在是 `CLOSED`。
- 有操作日志：可以看到是谁在什么时间把它改成了 `CLOSED`。

所以操作日志解决的是“系统行为可追踪”的问题。

## 2. operation_log 表为什么需要 user_id？

`user_id` 表示这条操作日志是谁产生的。

例如：

```text
user_id = 1
operation_type = CREATE_TICKET
content = 用户创建了工单 #10
```

这表示用户 `1` 创建了工单 `10`。

如果没有 `user_id`，日志只能说明系统发生了某件事，但不知道是谁做的。

`user_id` 的作用包括：

- 追踪某个用户做过哪些操作；
- 排查问题时找到具体操作人；
- 做权限审计；
- 发现异常行为，例如某个账号短时间内频繁登录失败。

登录失败时有一种特殊情况：用户名不存在。这时系统查不到用户，所以 `user_id` 可以是 `null`。

## 3. operation_type 和 business_type 有什么区别？

`operation_type` 表示“做了什么动作”。

例如：

- `CREATE_TICKET`：创建工单；
- `REPLY_TICKET`：回复工单；
- `UPDATE_TICKET_STATUS`：修改工单状态；
- `DELETE_TICKET`：删除工单；
- `LOGIN_SUCCESS`：登录成功；
- `LOGIN_FAILED`：登录失败；
- `REGISTER_USER`：用户注册。

`business_type` 表示“这个动作属于哪个业务模块”。

例如：

- `AUTH`：认证模块，比如登录、注册；
- `USER`：用户模块；
- `TICKET`：工单模块；
- `TICKET_REPLY`：工单回复模块。

可以这样理解：

```text
operation_type 关注动作：做了什么
business_type 关注对象：操作的是哪个业务模块
```

举例：

```text
operation_type = CREATE_TICKET
business_type = TICKET
```

含义是：在工单模块里，发生了一次创建工单操作。

再比如：

```text
operation_type = LOGIN_FAILED
business_type = AUTH
```

含义是：在认证模块里，发生了一次登录失败操作。

## 4. business_id 有什么用？

`business_id` 表示这条日志关联到哪一条具体业务数据。

例如创建工单成功后，数据库生成了工单 id：

```text
ticket.id = 10
```

日志可以记录：

```text
business_type = TICKET
business_id = 10
```

这样以后查询工单 `10` 的操作历史时，就可以查：

```text
business_type = TICKET
business_id = 10
```

它的作用是把日志和具体业务数据关联起来。

如果没有 `business_id`，只能知道“有人创建过工单”，但不知道创建的是哪一张工单。

## 5. 为什么真实企业系统需要操作日志？

真实企业系统一般需要操作日志，原因主要有四个。

第一，方便排查问题。

例如用户反馈“我的工单被关闭了”，后台可以查看日志，确认是谁关闭的、什么时候关闭的。

第二，方便追责。

如果某个管理员误删了工单，操作日志可以记录删除人和删除时间。

第三，方便安全审计。

例如某个账号连续登录失败很多次，可能说明密码被猜测或账号被攻击。

第四，方便分析业务流程。

例如系统可以统计：

- 每天创建了多少工单；
- 哪些工单经常被回复；
- 哪些用户登录失败比较多；
- 工单平均多久从 `OPEN` 变成 `CLOSED`。

操作日志不是为了替代业务表，而是补充“发生过什么事”的历史记录。

## 6. 为什么第一版先不使用 AOP？

`AOP` 可以把日志记录从业务代码中抽出来，统一拦截方法并自动记录日志。

但是第一版先不使用 AOP，原因是：

- 初学阶段更容易看懂直接调用；
- 能清楚看到每个业务动作在哪里写入日志；
- 不需要学习切面、注解、反射、方法参数解析等额外概念；
- 出问题时更容易定位。

当前项目第一版采用的是直接调用：

```java
operationLogService.record(...);
```

这样可以明确看到：

- 创建工单成功后记录日志；
- 回复工单成功后记录日志；
- 修改状态成功后记录日志；
- 删除工单成功后记录日志；
- 登录成功或失败时记录日志。

等业务流程稳定后，再用 AOP 优化会更合适。

## 7. operationLogService.record(...) 应该放在哪里调用？

`operationLogService.record(...)` 应该放在 Service 层调用。

当前项目中，日志记录放在这些业务 Service 中：

- `TicketService`
- `TicketReplyService`
- `AuthService`

不建议放在 Controller 中，因为 Controller 主要负责：

- 接收 HTTP 请求；
- 接收参数；
- 调用 Service；
- 返回统一 `Result`。

真正判断“业务是否成功”的地方在 Service。

例如创建工单：

```text
Controller 接收请求
    ↓
TicketService 校验用户、设置工单字段、插入数据库
    ↓
插入成功后调用 operationLogService.record(...)
```

也就是说，日志应该记录在业务真正完成之后。

如果工单创建失败，就不应该记录“创建工单成功”的日志。

## 8. 创建工单时应该记录什么日志？

创建工单成功后，应该记录一条 `CREATE_TICKET` 日志。

当前项目记录的信息可以理解为：

```text
user_id：当前登录用户 id
operation_type：CREATE_TICKET
business_type：TICKET
business_id：新创建的工单 id
content：用户创建了工单 #工单id
```

为什么要在插入数据库之后记录？

因为只有执行：

```java
ticketMapper.insert(ticket);
```

之后，MyBatis-Plus 才能拿到数据库自动生成的 `ticket.id`。

所以正确顺序是：

```text
创建 Ticket 对象
插入 ticket 表
拿到 ticket.id
记录 CREATE_TICKET 日志
```

## 9. 回复工单时应该记录什么日志？

回复工单成功后，应该记录一条 `REPLY_TICKET` 日志。

当前项目记录的信息可以理解为：

```text
user_id：当前登录用户 id
operation_type：REPLY_TICKET
business_type：TICKET_REPLY
business_id：新创建的回复 id
content：用户回复了工单 #工单id
```

注意，必须在回复插入成功后再记录日志。

如果出现这些情况，不应该记录成功日志：

- 工单不存在；
- 工单已经 `CLOSED`；
- 普通用户想回复别人的工单；
- 回复内容为空；
- 数据库插入失败。

因为这些情况都没有真正产生一条成功回复。

## 10. 修改工单状态时应该记录什么日志？

修改工单状态成功后，应该记录一条 `UPDATE_TICKET_STATUS` 日志。

当前项目记录的信息可以理解为：

```text
user_id：当前登录用户 id
operation_type：UPDATE_TICKET_STATUS
business_type：TICKET
business_id：工单 id
content：用户将工单 #工单id 状态修改为 新状态
```

例如：

```text
用户将工单 #10 状态修改为 PROCESSING
```

状态修改日志很重要，因为工单状态代表处理进度。

如果一张工单从 `OPEN` 变成 `PROCESSING`，再变成 `CLOSED`，操作日志可以还原这个处理过程。

如果状态修改失败，例如：

- 状态值非法；
- 工单不存在；
- 已关闭工单再次修改；
- 状态流转不合法；

则不记录成功日志。

## 11. 登录成功和登录失败为什么也要记录？

登录属于安全相关操作，所以成功和失败都应该记录。

登录成功日志可以说明：

- 哪个用户成功进入系统；
- 什么时候登录；
- 后续操作是谁发起的。

登录失败日志可以帮助发现安全风险。

例如：

- 某个用户名连续密码错误；
- 很多不存在的用户名被尝试登录；
- 某个账号在短时间内出现大量失败记录。

这些情况可能是：

- 用户忘记密码；
- 有人在尝试猜密码；
- 有人在批量试探账号。

当前项目中：

- 用户名不存在时，`user_id` 记录为 `null`；
- 密码错误时，如果查到了用户，`user_id` 记录为该用户 id；
- 日志内容不记录明文密码。

明文密码不能写入日志，因为日志可能被管理员、运维人员或日志平台看到，泄露密码会带来严重安全风险。

## 12. 日志查询为什么也需要分页？

操作日志会越来越多。

一个系统每天可能产生很多日志：

- 登录日志；
- 创建工单日志；
- 回复日志；
- 修改状态日志；
- 删除日志。

如果接口一次性查询全部日志，会有几个问题：

- 数据库压力大；
- 后端内存占用高；
- 接口响应慢；
- 前端页面也渲染不了太多数据；
- 网络传输数据过大。

所以操作日志查询必须分页。

当前项目的日志查询参数包括：

```text
page：第几页，默认 1
size：每页多少条，默认 10，最大 100
userId：按操作人筛选
operationType：按操作类型筛选
businessType：按业务模块筛选
```

返回结构使用统一分页对象 `PageResult`：

```text
records：当前页日志列表
total：符合条件的总条数
page：当前页码
size：每页条数
```

## 13. 为什么操作日志查询接口应该限制为 ADMIN？

操作日志属于敏感数据。

日志里可能包含：

- 用户 id；
- 用户名；
- 登录成功记录；
- 登录失败记录；
- 工单 id；
- 删除操作；
- 状态修改记录。

普通用户不应该看到全系统的操作历史。

例如普通用户如果能查询所有日志，就可能知道：

- 哪些用户登录过系统；
- 哪些工单被谁处理过；
- 哪些账号登录失败；
- 管理员做过哪些操作。

这会造成隐私和安全风险。

所以当前项目中，`GET /operation-logs` 只能由 `ADMIN` 查询。

权限判断放在 `OperationLogService` 中：

```java
PermissionUtil.requireAdmin();
```

这样即使以后新增别的 Controller 或内部调用，也会先经过 Service 层权限校验。

## 14. 这个模块后续如何用 AOP 优化？

当前第一版是在业务 Service 中手动写：

```java
operationLogService.record(...);
```

这样直观、容易学习，但随着业务越来越多，会出现重复代码。

后续可以使用 AOP 优化。

常见做法是自定义一个注解，例如：

```java
@OperationLogRecord(
        operationType = "CREATE_TICKET",
        businessType = "TICKET"
)
```

然后在需要记录日志的方法上使用：

```java
@OperationLogRecord(operationType = "CREATE_TICKET", businessType = "TICKET")
public Ticket createTicket(TicketCreateDTO dto) {
    ...
}
```

AOP 切面负责统一处理：

```text
方法执行前或执行后拦截
    ↓
判断方法是否成功
    ↓
获取当前登录用户
    ↓
获取业务 id
    ↓
调用 OperationLogService.record(...)
```

使用 AOP 后的好处是：

- 业务代码更干净；
- 记录日志的规则更统一；
- 新增日志时只需要加注解；
- 可以集中处理异常、耗时、请求参数等信息。

但 AOP 也有学习成本：

- 要理解切面什么时候执行；
- 要处理方法返回值；
- 要处理异常时是否记录日志；
- 要从参数或返回结果中提取 `business_id`；
- 调试时不如直接调用直观。

所以当前学习阶段先用 Service 直接调用，等模块稳定后再升级成 AOP，是更容易理解也更稳妥的路线。

## 小结

操作日志模块可以总结为一句话：

```text
它记录系统里关键动作的历史，让后端知道是谁、在什么时候、对哪个业务对象、做了什么操作。
```

当前项目的实现方式是：

```text
业务 Service 完成关键操作
    ↓
调用 OperationLogService.record(...)
    ↓
写入 operation_log 表
    ↓
ADMIN 通过 GET /operation-logs 分页查询
```

第一版不使用 AOP，是为了让调用链更清楚，适合初学者理解。后续业务增多后，可以再用 AOP 把重复的日志记录代码统一抽取。
