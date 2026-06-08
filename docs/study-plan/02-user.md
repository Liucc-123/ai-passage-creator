# 模块2：用户模块

> 目标：注册/登录/登出跑通，Session 写入 Redis，管理员接口有权限拦截

## 核心流程

```
注册 → 密码加盐MD5 → 写入user表
登录 → 校验账号密码 → 写入Session(Redis) → 返回Cookie
鉴权 → 拦截器读Session → 校验角色 → 放行/拒绝
```

## 数据模型

User 实体对应模块1建好的 `user` 表，关键字段：

| 字段 | 说明 | 设计意图 |
|------|------|---------|
| `userAccount` | 登录账号，唯一索引 | 与 userName 分离，userName 可改，account 不可改 |
| `userPassword` | MD5 + 盐加密 | 盐值拼接在密码前，如 `salt + password` 再 MD5 |
| `userRole` | `user` / `admin` | 后续 `@AuthCheck` 注解基于此字段拦截 |
| `quota` | 剩余创作配额，默认 5 | 每次创建文章扣 1，VIP 不限量 |
| `vipTime` | VIP 过期时间 | null 表示非 VIP |

## API 设计

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/user/register` | 注册 | 无 |
| POST | `/user/login` | 登录 | 无 |
| POST | `/user/logout` | 登出 | 登录 |
| GET | `/user/get/login` | 获取当前登录用户 | 登录 |
| POST | `/user/add` | 添加用户 | 管理员 |
| GET | `/user/get` | 查询单个用户 | 管理员 |
| POST | `/user/delete` | 删除用户 | 管理员 |
| POST | `/user/update` | 更新用户 | 管理员 |
| POST | `/user/list/page/vo` | 分页查询用户 | 管理员 |

## 密码加密方案

项目使用 **MD5 + 盐**，流程：

```
1. 生成随机盐值（或使用固定盐，如 "yupi"）
2. 加密：MD5(salt + password)
3. 存储：直接存加密后的字符串
```

> 这不是最安全的方案（BCrypt 更好），但项目选择了简单实现。你可以自行决定是否升级。

## Session 管理

利用 Spring Session + Redis 自动管理：

```java
// 登录成功后
request.getSession().setAttribute(USER_LOGIN_STATE, user);

// 获取当前用户
User user = (User) request.getSession().getAttribute(USER_LOGIN_STATE);

// 登出
request.getSession().removeAttribute(USER_LOGIN_STATE);
```

Spring Session 自动将 Session 数据序列化到 Redis，Cookie 中只存 Session ID。配置 30 天过期即可。

## 权限拦截设计

### 自定义注解 `@AuthCheck`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {
    String mustRole() default "";  // "admin" 表示需要管理员权限
}
```

### AOP 拦截器

```java
@Around("@annotation(authCheck)")
public Object doIntercept(ProceedingJoinPoint joinPoint, AuthCheck authCheck) {
    // 1. 从 HttpServletRequest 获取 Session 中的用户
    // 2. 如果 mustRole 不为空，校验 userRole 是否匹配
    // 3. 不匹配则抛出 BusinessException(ErrorCode.NO_AUTH_ERROR)
    // 4. 匹配则放行
}
```

### 使用方式

```java
@AuthCheck(mustRole = "admin")
@PostMapping("/add")
public BaseResponse<Long> addUser(...) { ... }
```

## 关键设计决策

1. **为什么用 Session 而不是 JWT？** 项目需要主动让用户下线（如管理员踢人），Session 存 Redis 可以直接删除，JWT 做不到。选择 Session 是有意为之。

2. **为什么返回 UserVO 而不是 User？** User 包含密码等敏感字段，列表查询应脱敏为 UserVO（去掉密码，补充 VIP 状态等计算字段）。

3. **逻辑删除**：`isDelete` 字段标记删除，查询时加 `WHERE isDelete = 0`。MyBatis-Flex 支持全局逻辑删除配置。

## 本模块验证标准

- [ ] 注册接口能创建用户，密码加密存储
- [ ] 登录接口返回成功，Redis 中能查到 Session 数据
- [ ] 登出后 Session 清除
- [ ] 未登录访问受保护接口返回 40100 错误码
- [ ] 普通用户访问管理员接口被拦截
- [ ] 分页查询返回 UserVO（不含密码）