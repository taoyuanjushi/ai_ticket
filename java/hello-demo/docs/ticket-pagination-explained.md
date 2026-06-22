# Ticket 分页查询核心概念说明

本文档解释 Ticket 工单分页查询中几个常见问题，帮助理解当前项目里 `GET /tickets?page=1&size=10` 这类接口为什么这样设计。

## 1. 为什么真实项目不能直接查询全部数据？

学习阶段数据很少，直接查全部数据看起来没问题。但真实项目中的数据会不断增长，可能从几十条变成几万、几十万甚至更多。

如果接口每次都查询全部数据，会带来几个问题：

- 数据库压力大：一次查询太多行，会占用数据库 CPU、内存和磁盘 IO。
- 后端压力大：后端需要把大量数据加载到内存，再转换成 JSON。
- 网络传输慢：大量 JSON 数据会让接口响应变慢。
- 前端渲染慢：浏览器一次渲染大量表格行，页面会卡顿。
- 用户体验差：用户通常只需要看第一页或某一页，不需要一次拿到全部数据。

所以真实项目通常都使用分页查询：每次只查一页数据。

## 2. page 和 size 分别是什么意思？

`page` 表示当前第几页。

例如：

```text
page=1 表示第 1 页
page=2 表示第 2 页
page=3 表示第 3 页
```

`size` 表示每页查询多少条数据。

例如：

```text
size=10 表示每页 10 条
size=20 表示每页 20 条
```

所以：

```text
GET /tickets?page=1&size=10
```

表示查询第 1 页，每页 10 条数据。

当前项目中：

- `page` 默认为 `1`
- `size` 默认为 `10`
- `size` 最大限制为 `100`

## 3. Page<Ticket> 是什么？

`Page<Ticket>` 是 MyBatis-Plus 提供的分页对象。

可以把它理解为“分页查询参数 + 分页查询结果”的容器。

查询前，它保存：

```java
new Page<>(page, size)
```

也就是当前页码和每页条数。

查询后，它会保存：

- 当前页数据：`getRecords()`
- 总记录数：`getTotal()`
- 当前页码：`getCurrent()`
- 每页条数：`getSize()`

在当前项目中，Service 使用：

```java
Page<Ticket> resultPage = ticketMapper.selectPage(pageParam, wrapper);
```

意思是：按照 `pageParam` 的分页要求和 `wrapper` 的查询条件，从 `ticket` 表查询一页工单数据。

## 4. LambdaQueryWrapper 是用来做什么的？

`LambdaQueryWrapper<Ticket>` 是 MyBatis-Plus 用来拼接查询条件的工具。

它可以把 Java 代码转换成 SQL 条件。

例如：

```java
wrapper.eq(Ticket::getStatus, "OPEN");
```

大致对应 SQL：

```sql
WHERE status = 'OPEN'
```

再例如：

```java
wrapper.orderByDesc(Ticket::getCreatedAt);
```

大致对应 SQL：

```sql
ORDER BY created_at DESC
```

使用 `LambdaQueryWrapper` 的好处是：字段通过 `Ticket::getStatus` 这类方法引用来指定，比直接手写字符串 `"status"` 更安全，字段重命名时也更容易发现问题。

## 5. eq 和 like 有什么区别？

`eq` 表示精确匹配。

例如：

```java
wrapper.eq(Ticket::getStatus, "OPEN");
```

含义是：只查询 `status` 等于 `OPEN` 的工单。

对应 SQL 类似：

```sql
WHERE status = 'OPEN'
```

`like` 表示模糊匹配。

例如：

```java
wrapper.like(Ticket::getTitle, "登录");
```

含义是：查询标题中包含“登录”的工单。

对应 SQL 类似：

```sql
WHERE title LIKE '%登录%'
```

简单理解：

- `eq`：必须完全相等。
- `like`：只要包含关键字即可。

## 6. keyword 为什么要同时查 title 和 content？

用户输入 `keyword` 时，通常是在搜索“和这个词相关的工单”，而不是只搜索标题。

例如用户搜索：

```text
登录
```

可能有两种情况：

- 工单标题是：`登录失败`
- 工单内容是：`用户反馈登录系统时提示账号不存在`

如果只查 `title`，第二种情况可能查不到。

所以当前项目用：

```java
wrapper.and(w -> w
        .like(Ticket::getTitle, keyword)
        .or()
        .like(Ticket::getContent, keyword));
```

大致含义是：

```sql
AND (title LIKE '%keyword%' OR content LIKE '%keyword%')
```

这样搜索范围更符合用户预期。

## 7. 为什么查询逻辑应该写在 Service，而不是 Controller？

Controller 的职责是处理 HTTP 请求和返回 HTTP 响应。

在当前项目中，Controller 主要负责：

- 接收 `page`
- 接收 `size`
- 接收 `status`
- 接收 `priority`
- 接收 `category`
- 接收 `keyword`
- 调用 `TicketService`
- 返回统一 `Result`

Service 的职责是处理业务逻辑。

在当前项目中，Service 负责：

- 设置分页默认值
- 校验 `page` 和 `size`
- 校验 `status` 和 `priority`
- 组装查询条件
- 调用 Mapper 查询数据库
- 封装 `PageResult`

这样分层有几个好处：

- Controller 更简单，只关注接口。
- Service 可以复用，后续别的入口也能调用同一套查询逻辑。
- 业务规则集中在 Service，后续维护更容易。
- 测试更清晰，可以单独测试 Service 的查询逻辑。

如果把查询逻辑都写在 Controller 中，Controller 会越来越复杂，后续新增条件、修改规则、排查问题都会更困难。

## 8. 为什么分页返回要包含 records、total、page、size？

分页接口不仅要返回当前页的数据，还要让前端知道分页状态。

当前项目返回：

```json
{
  "records": [],
  "total": 0,
  "page": 1,
  "size": 10
}
```

各字段含义：

| 字段 | 含义 |
| --- | --- |
| `records` | 当前页的数据列表 |
| `total` | 符合查询条件的总记录数 |
| `page` | 当前第几页 |
| `size` | 每页多少条 |

前端需要这些字段来渲染分页器。

例如：

- `records` 用来显示表格数据。
- `total` 用来计算一共有多少页。
- `page` 用来知道当前停留在哪一页。
- `size` 用来知道每页显示多少条。

如果只返回 `records`，前端只能显示当前页数据，但不知道总共有多少条，也不知道能不能继续翻页。

## 小结

Ticket 分页查询的核心流程是：

```text
前端传入 page/size/status/priority/category/keyword
        ↓
Controller 接收参数
        ↓
Service 校验参数并组装查询条件
        ↓
Mapper 通过 MyBatis-Plus 查询数据库
        ↓
Service 封装 PageResult
        ↓
Controller 返回 Result<PageResult<Ticket>>
```

这种写法既能控制数据量，也能保持 Controller、Service、Mapper 分层清晰。
