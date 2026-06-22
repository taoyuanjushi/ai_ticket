# Redis 缓存说明

本文用初学者能理解的方式，解释当前 Spring Boot 工单项目中的 Redis 缓存。

当前项目已经接入 Redis 缓存第一版，主要缓存两类数据：

- 用户详情：`user:detail:{id}`
- 工单详情：`ticket:detail:{id}`

涉及的核心代码：

- `config/RedisConfig.java`：配置 `RedisTemplate` 的序列化方式。
- `constant/RedisKeyConstants.java`：统一管理 Redis key。
- `service/RedisCacheService.java`：封装 Redis 的 `get`、`set`、`delete`。
- `service/UserService.java`：查询用户详情时使用 Redis。
- `service/TicketService.java`：查询工单详情时使用 Redis。
- `service/TicketReplyService.java`：新增回复后删除工单详情缓存。

## 1. Redis 和 MySQL 有什么区别？

MySQL 是关系型数据库，主要负责长期保存业务数据。

例如当前项目里：

- `user` 表保存用户信息；
- `ticket` 表保存工单信息；
- `ticket_reply` 表保存工单回复；
- `operation_log` 表保存操作日志。

这些数据需要长期保存，即使项目重启、电脑重启，也不能丢。

Redis 更常用于缓存，它把数据放在内存里，读取速度很快。

可以简单理解为：

```text
MySQL：长期保存数据，适合做最终数据来源
Redis：临时加速读取，适合缓存热点数据
```

举例：

用户详情保存在 MySQL 中，Redis 只是保存一份临时副本。Redis 里的缓存过期或被删除后，系统还能继续从 MySQL 查到真实数据。

所以 Redis 不能替代 MySQL。Redis 是加速层，MySQL 是最终数据来源。

## 2. 什么是缓存？

缓存就是把经常使用的数据临时保存到读取更快的地方。

生活中的例子：

第一次查一个单词，你可能要翻词典。  
第二次再遇到这个单词，你已经记住了，就不用再翻词典。

在后端项目中：

```text
第一次查询用户详情：
Redis 没有
    ↓
查 MySQL
    ↓
把结果写入 Redis
    ↓
返回给前端

第二次查询用户详情：
Redis 有
    ↓
直接返回给前端
```

Redis 里的这份数据就是缓存。

## 3. 为什么用户详情适合缓存？

用户详情适合缓存，是因为它通常有这些特点：

- 查询频率高；
- 数据变化不频繁；
- 数据量不大；
- 多个接口可能都会用到用户信息。

例如当前项目中，用户详情可能被这些功能使用：

- `GET /users/{id}` 查询用户信息；
- 工单详情里展示提交人信息；
- 权限控制中需要知道当前用户身份。

用户姓名、邮箱、角色等信息不会每秒都变，所以适合缓存。

不过用户详情里不能缓存和返回 `password`。当前项目用 `UserInfoVO` 作为用户详情返回对象，字段包括：

```text
id
username
name
age
email
role
```

不包含：

```text
password
```

## 4. 为什么工单详情适合缓存？

工单详情也适合缓存，尤其是当前项目的 `GET /tickets/{id}/detail`。

因为工单详情不是只查一张表，它会组合多部分数据：

- 查询 `ticket` 表，拿工单本身；
- 查询 `user` 表，拿提交人信息；
- 查询 `ticket_reply` 表，拿回复列表；
- 组装成 `TicketDetailVO` 返回给前端。

这比普通的单表查询成本更高。

如果同一张工单详情短时间内被多次查看，使用 Redis 可以减少重复查询 MySQL。

当前项目缓存的是：

```text
ticket:detail:{id}
```

对应的数据是某张工单的详情对象。

## 5. RedisTemplate 是用来做什么的？

`RedisTemplate` 是 Spring 提供的 Redis 操作工具。

可以把它理解成 Java 代码和 Redis 之间的“操作入口”。

通过 `RedisTemplate` 可以做：

```java
redisTemplate.opsForValue().get(key);
redisTemplate.opsForValue().set(key, value, ttl);
redisTemplate.delete(key);
```

当前项目没有在业务代码里直接到处写 `RedisTemplate`，而是封装成了 `RedisCacheService`：

```text
RedisCacheService.get(key)
RedisCacheService.set(key, value, ttl)
RedisCacheService.delete(key)
```

这样做的好处是：

- 业务代码更简单；
- Redis 操作更统一；
- 以后扩展日志、异常处理、降级逻辑更方便。

## 6. 什么是缓存 key？

缓存 key 是 Redis 中用来定位一份数据的名字。

Redis 存数据时是 key-value 结构：

```text
key   -> value
名字  -> 数据
```

例如：

```text
user:detail:1 -> 用户 1 的详情数据
ticket:detail:10 -> 工单 10 的详情数据
```

如果没有 key，Redis 就不知道你要读取哪一份缓存。

所以 key 必须清晰、稳定、能表达含义。

当前项目用 `RedisKeyConstants` 统一生成 key，避免在业务代码里到处手写字符串。

## 7. user:detail:{id} 这种 key 表示什么？

`user:detail:{id}` 表示某个用户的详情缓存。

其中 `{id}` 是用户 id。

例如：

```text
user:detail:1
```

表示：

```text
用户 id = 1 的用户详情缓存
```

当前项目中，`RedisKeyConstants.userDetailKey(1L)` 会生成：

```text
user:detail:1
```

这个 key 对应的 value 是 `UserInfoVO`，不包含 `password`。

## 8. ticket:detail:{id} 这种 key 表示什么？

`ticket:detail:{id}` 表示某个工单的详情缓存。

其中 `{id}` 是工单 id。

例如：

```text
ticket:detail:1
```

表示：

```text
工单 id = 1 的工单详情缓存
```

当前项目中，`RedisKeyConstants.ticketDetailKey(1L)` 会生成：

```text
ticket:detail:1
```

这个 key 对应的 value 是 `TicketDetailVO`，包含：

- 工单本身；
- 提交人信息；
- 回复列表。

注意，当前项目缓存命中后仍然会校验权限，避免普通用户读取别人的工单详情。

## 9. 什么是 TTL？

`TTL` 是 `Time To Live` 的缩写，意思是“还能活多久”。

在 Redis 中，TTL 表示一个 key 还有多久过期。

例如：

```text
user:detail:1 的 TTL 是 1800 秒
```

意思是：

```text
user:detail:1 这个缓存最多再保留 1800 秒，也就是 30 分钟
```

当前项目设置：

```text
用户详情 TTL：30 分钟
工单详情 TTL：10 分钟
```

可以用 Redis CLI 查看：

```bash
ttl user:detail:1
ttl ticket:detail:1
```

## 10. 为什么缓存要设置过期时间？

缓存设置过期时间主要有三个原因。

第一，避免旧数据长期存在。

如果用户信息已经改了，但缓存永远不过期，就可能长期返回旧数据。

第二，避免 Redis 内存无限增长。

Redis 主要使用内存，内存不是无限的。给缓存设置 TTL，可以让不常用的数据自动清理。

第三，降低缓存一致性风险。

就算某次更新数据后忘记删缓存，TTL 到期后缓存也会自动失效，之后会重新从 MySQL 加载新数据。

TTL 不是解决所有问题的办法，但它是缓存设计里很重要的安全网。

## 11. 查询数据时为什么要先查 Redis，再查 MySQL？

因为 Redis 读取速度快，适合作为第一层查询。

当前项目的查询流程是：

```text
Controller 接收请求
    ↓
Service 先查 Redis
    ↓
Redis 命中：直接返回
    ↓
Redis 未命中：查询 MySQL
    ↓
把 MySQL 结果写入 Redis
    ↓
返回给前端
```

这样设计的好处是：

- 缓存命中时，减少 MySQL 查询；
- 降低数据库压力；
- 提高接口响应速度；
- 热点数据可以被更快返回。

MySQL 仍然是最终数据来源。Redis 没有数据时，系统还能从 MySQL 查询。

## 12. 修改工单后为什么要删除 Redis 缓存？

修改工单后，MySQL 中的工单已经变了。

如果不删除 Redis 缓存，Redis 里可能还保存着修改前的旧工单详情。

例如：

```text
Redis 中 ticket:detail:1 的 title = 登录失败
MySQL 中 ticket.id = 1 的 title 已改成 登录异常
```

如果继续返回 Redis 缓存，前端看到的还是旧标题。

所以当前项目在 `TicketService.updateTicket(...)` 成功后会删除：

```text
ticket:detail:{id}
```

下一次再查询工单详情时，Redis 没有缓存，就会重新查 MySQL，并把新结果写回 Redis。

## 13. 删除工单后为什么要删除 Redis 缓存？

删除工单后，MySQL 中这张工单已经不存在了。

如果 Redis 里还保留：

```text
ticket:detail:1
```

那么用户可能还能从缓存里看到一张已经被删除的工单。

这会造成严重问题：

- 前端看到不存在的数据；
- 用户误以为删除失败；
- 权限和审计判断变复杂；
- 数据状态不可信。

所以当前项目在 `TicketService.deleteTicket(...)` 成功后会删除对应工单详情缓存。

同理，新增回复后也要删除工单详情缓存，因为工单详情里包含回复列表。如果不删除，前端可能看不到刚新增的回复。

## 14. 什么是缓存一致性？

缓存一致性指的是：

```text
Redis 中的数据和 MySQL 中的数据是否保持一致
```

举例：

```text
MySQL 中用户邮箱 = new@example.com
Redis 中用户邮箱 = old@example.com
```

这就是缓存不一致。

当前项目采用的简单策略是：

```text
查询时写缓存
修改或删除时删缓存
```

也就是：

```text
Cache Aside Pattern
```

可以理解为：

```text
读数据：先读缓存，缓存没有再读数据库
写数据：先改数据库，成功后删除缓存
```

为什么是删除缓存，而不是直接更新缓存？

因为删除更简单，也更不容易出错。下一次查询时会重新从 MySQL 加载最新数据。

## 15. 什么是缓存穿透？

缓存穿透指的是：用户查询一个根本不存在的数据，Redis 没有，MySQL 也没有。

例如有人不断请求：

```text
GET /users/999999999
```

如果用户 `999999999` 根本不存在：

```text
查 Redis：没有
查 MySQL：也没有
下次再查 Redis：还是没有
下次再查 MySQL：还是没有
```

这样大量请求会一直打到 MySQL，Redis 没有起到保护作用。

这就是缓存穿透。

常见解决办法有：

- 缓存空值；
- 参数校验；
- 布隆过滤器；
- 限流。

但当前项目是第一版缓存，暂时不做缓存空值和布隆过滤器，先保持逻辑简单。

## 16. 为什么第一版不建议直接缓存所有数据？

第一版不建议缓存所有数据，原因是缓存也有成本。

如果盲目缓存所有数据，会带来这些问题：

- Redis 内存占用变大；
- 缓存 key 很多，管理困难；
- 修改数据后要清理很多缓存；
- 容易出现缓存不一致；
- 很多低频数据缓存后也没人访问，收益很低；
- 排查问题时需要同时看 Redis 和 MySQL。

缓存应该优先用于“读多写少、查询成本较高、访问频率高”的数据。

当前项目选择缓存：

```text
用户详情
工单详情
```

原因是它们比较适合第一版学习：

- 逻辑清楚；
- key 好设计；
- 缓存失效规则简单；
- 容易用 Postman 和 Redis CLI 验证；
- 不会一下子把系统复杂度拉太高。

列表查询、分页查询、条件查询暂时不缓存，是因为它们的参数组合很多，例如：

```text
page
size
status
priority
keyword
```

如果每种组合都缓存，key 会变复杂，删除缓存也更麻烦。

## 小结

当前项目的 Redis 缓存可以总结为：

```text
Redis 用来加速读取
MySQL 仍然保存真实数据
查询时先查 Redis，再查 MySQL
修改或删除后删除 Redis 缓存
缓存要设置 TTL
第一版只缓存用户详情和工单详情
```

用户详情缓存流程：

```text
GET /users/{id}
    ↓
权限校验
    ↓
查 user:detail:{id}
    ↓
命中则返回 UserInfoVO
    ↓
未命中则查 MySQL
    ↓
写入 Redis，TTL 30 分钟
```

工单详情缓存流程：

```text
GET /tickets/{id}/detail
    ↓
查 ticket:detail:{id}
    ↓
命中后仍校验工单数据权限
    ↓
未命中则查 MySQL，组装 TicketDetailVO
    ↓
写入 Redis，TTL 10 分钟
```

缓存不是越多越好。第一版先缓存最容易理解、收益明显、失效规则简单的数据，是更适合学习和维护的做法。
