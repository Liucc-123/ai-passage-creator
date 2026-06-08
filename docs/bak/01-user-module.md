# 用户模块设计文档

## 📌 模块概述

用户模块是系统的基础模块，负责用户认证、授权和信息管理。本模块采用 JWT + Redis 实现无状态的认证机制，支持普通用户和管理员两种角色。

## 🎯 学习目标

完成本模块后，你将能够：
1. 实现用户注册、登录功能
2. 掌握 JWT 认证机制
3. 理解权限控制的设计
4. 实现用户信息的增删改查

## 🗄️ 数据库设计

### user 表

```sql
CREATE TABLE user (
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256) not null comment '账号',
    userPassword varchar(512) not null comment '密码',
    userName     varchar(256) null comment '用户昵称',
    userAvatar   varchar(1024) null comment '用户头像',
    userProfile  varchar(512) null comment '用户简介',
    userRole     varchar(256) default 'user' not null comment '用户角色：user/admin',
    vipTime      datetime null comment '成为会员时间',
    quota        int default 0 comment '剩余配额',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint default 0 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户表';
```

**字段说明：**
- `userAccount`: 用户账号，唯一索引
- `userPassword`: 密码（建议使用 BCrypt 加密）
- `userRole`: 用户角色，枚举值：user（普通用户）、admin（管理员）
- `vipTime`: 会员时间，用于判断用户是否为 VIP
- `quota`: 用户剩余配额，用于限制使用次数

## 🔧 后端实现

### 1. 实体类设计

**路径：** `src/main/java/com/yupi/template/model/entity/User.java`

**设计要点：**
- 使用 Lombok 注解简化代码
- 实现 Serializable 接口
- 添加字段注释

**参考字段：**
```java
@Data
public class User implements Serializable {
    private Long id;
    private String userAccount;
    private String userPassword;
    private String userName;
    private String userAvatar;
    private String userProfile;
    private String userRole;
    private Date vipTime;
    private Integer quota;
    private Date editTime;
    private Date createTime;
    private Date updateTime;
    private Integer isDelete;
}
```

### 2. DTO 设计

**路径：** `src/main/java/com/yupi/template/model/dto/user/`

需要创建以下 DTO：

1. **UserRegisterRequest.java** - 用户注册请求
   - userAccount: 账号
   - userPassword: 密码
   - checkPassword: 确认密码
   - 添加参数校验注解

2. **UserLoginRequest.java** - 用户登录请求
   - userAccount: 账号
   - userPassword: 密码

3. **UserUpdateRequest.java** - 用户信息更新请求
   - userName: 昵称
   - userAvatar: 头像
   - userProfile: 简介

4. **UserQueryRequest.java** - 用户查询请求
   - id: 用户ID
   - userAccount: 账号
   - userName: 昵称
   - userRole: 角色
   - 支持分页查询

### 3. VO 设计

**路径：** `src/main/java/com/yupi/template/model/vo/`

需要创建以下 VO：

1. **LoginUserVO.java** - 登录用户信息
   - id: 用户ID
   - userAccount: 账号
   - userName: 昵称
   - userAvatar: 头像
   - userRole: 角色
   - vipTime: 会员时间
   - quota: 剩余配额

2. **UserVO.java** - 用户信息展示
   - 包含用户基本信息
   - 不包含敏感信息（如密码）

### 4. 枚举类设计

**路径：** `src/main/java/com/yupi/template/model/enums/UserRoleEnum.java`

```java
public enum UserRoleEnum {
    USER("user", "普通用户"),
    ADMIN("admin", "管理员");

    private final String value;
    private final String text;

    // 构造函数、getter 方法
}
```

### 5. Mapper 接口设计

**路径：** `src/main/java/com/yupi/template/mapper/UserMapper.java`

使用 MyBatis-Flex 注解：

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 基础 CRUD 由 BaseMapper 提供
    // 如需自定义查询，添加方法
}
```

### 6. Service 层设计

**路径：** `src/main/java/com/yupi/template/service/UserService.java`

**核心方法：**

1. **用户注册**
   ```java
   Long userRegister(UserRegisterRequest request);
   ```
   - 校验账号是否已存在
   - 校验两次密码是否一致
   - 密码加密存储
   - 返回用户ID

2. **用户登录**
   ```java
   LoginUserVO userLogin(UserLoginRequest request, HttpServletRequest request);
   ```
   - 校验账号密码
   - 生成 JWT Token
   - Token 存入 Redis
   - 返回登录用户信息

3. **获取当前登录用户**
   ```java
   User getLoginUser(HttpServletRequest request);
   ```
   - 从请求头获取 Token
   - 解析 Token 获取用户信息
   - 返回用户对象

4. **用户注销**
   ```java
   boolean userLogout(HttpServletRequest request);
   ```
   - 从 Redis 删除 Token
   - 返回是否成功

5. **更新用户信息**
   ```java
   boolean updateUser(UserUpdateRequest request, HttpServletRequest request);
   ```
   - 获取当前登录用户
   - 更新用户信息
   - 返回是否成功

6. **查询用户列表**
   ```java
   Page<UserVO> listUserByPage(UserQueryRequest request);
   ```
   - 支持分页查询
   - 支持条件过滤
   - 返回用户 VO 列表

### 7. Controller 层设计

**路径：** `src/main/java/com/yupi/template/controller/UserController.java`

**接口列表：**

1. POST `/api/user/register` - 用户注册
2. POST `/api/user/login` - 用户登录
3. POST `/api/user/logout` - 用户注销
4. GET `/api/user/get/login` - 获取当前登录用户
5. POST `/api/user/update` - 更新用户信息
6. POST `/api/user/delete` - 删除用户（管理员）
7. POST `/api/user/list/page` - 分页查询用户（管理员）

**注意：**
- 使用 `@AuthCheck` 注解进行权限控制
- 使用 `BaseResponse` 统一返回格式
- 添加接口文档注解

### 8. 工具类设计

**路径：** `src/main/java/com/yupi/template/utils/`

需要实现以下工具类：

1. **JwtUtil.java** - JWT 工具类
   - 生成 Token
   - 解析 Token
   - 验证 Token

2. **PasswordUtil.java** - 密码工具类
   - 密码加密
   - 密码校验

3. **ResultUtils.java** - 统一返回结果工具类
   - 成功返回
   - 失败返回

## 🎨 前端实现

### 1. API 封装

**路径：** `frontend/src/api/userController.ts`

```typescript
import request from '../request';

// 用户注册
export const userRegister = (data: UserRegisterRequest) => {
  return request.post('/api/user/register', data);
};

// 用户登录
export const userLogin = (data: UserLoginRequest) => {
  return request.post('/api/user/login', data);
};

// 用户注销
export const userLogout = () => {
  return request.post('/api/user/logout');
};

// 获取当前登录用户
export const getLoginUser = () => {
  return request.get('/api/user/get/login');
};

// 更新用户信息
export const updateUser = (data: UserUpdateRequest) => {
  return request.post('/api/user/update', data);
};
```

### 2. 状态管理

**路径：** `frontend/src/stores/loginUser.ts`

使用 Pinia 管理登录用户状态：

```typescript
import { defineStore } from 'pinia';
import { ref } from 'vue';
import { getLoginUser } from '../api/userController';

export const useLoginUserStore = defineStore('loginUser', () => {
  const loginUser = ref<API.LoginUserVO>();

  // 获取登录用户信息
  async function fetchLoginUser() {
    const res = await getLoginUser();
    if (res.data) {
      loginUser.value = res.data;
    }
  }

  return { loginUser, fetchLoginUser };
});
```

### 3. 页面实现

#### 3.1 登录页面

**路径：** `frontend/src/pages/user/UserLoginPage.vue`

**功能要点：**
- 表单验证（账号、密码必填）
- 登录成功后存储 Token
- 跳转到首页
- 错误提示

#### 3.2 注册页面

**路径：** `frontend/src/pages/user/UserRegisterPage.vue`

**功能要点：**
- 表单验证（账号、密码、确认密码）
- 两次密码一致性校验
- 注册成功后跳转到登录页
- 错误提示

### 4. 路由配置

**路径：** `frontend/src/router/index.ts`

配置登录和注册路由：

```typescript
const routes = [
  {
    path: '/user/login',
    name: 'UserLogin',
    component: () => import('../pages/user/UserLoginPage.vue'),
  },
  {
    path: '/user/register',
    name: 'UserRegister',
    component: () => import('../pages/user/UserRegisterPage.vue'),
  },
  // ...其他路由
];
```

### 5. 请求拦截器

**路径：** `frontend/src/request.ts`

配置 Axios 请求拦截器，自动添加 Token：

```typescript
// 请求拦截器
request.interceptors.request.use(
  (config) => {
    // 从 localStorage 获取 Token
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);
```

### 6. 响应拦截器

**路径：** `frontend/src/request.ts`

配置 Axios 响应拦截器，处理 401 错误：

```typescript
// 响应拦截器
request.interceptors.response.use(
  (response) => {
    return response.data;
  },
  (error) => {
    if (error.response?.status === 401) {
      // Token 过期，跳转到登录页
      router.push('/user/login');
    }
    return Promise.reject(error);
  }
);
```

## 🔐 权限控制

### 后端权限控制

**路径：** `src/main/java/com/yupi/template/annotation/AuthCheck.java`

创建自定义注解：

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {
    String mustRole() default "";
}
```

**路径：** `src/main/java/com/yupi/template/aop/AuthInterceptor.java`

实现 AOP 拦截器：

```java
@Aspect
@Component
public class AuthInterceptor {
    @Resource
    private UserService userService;

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        // 获取当前登录用户
        // 校验用户角色
        // 不满足权限则抛出异常
        return joinPoint.proceed();
    }
}
```

### 前端权限控制

**路径：** `frontend/src/access.ts`

实现权限控制函数：

```typescript
export const hasAccess = (userRole: string, requiredRole: string) => {
  // 管理员可以访问所有页面
  if (userRole === 'admin') {
    return true;
  }
  // 普通用户只能访问普通用户页面
  return userRole === requiredRole;
};
```

## 📝 学习任务清单

### 后端任务
- [ ] 创建 user 表
- [ ] 实现 User 实体类
- [ ] 实现用户相关 DTO
- [ ] 实现用户相关 VO
- [ ] 实现 UserRoleEnum 枚举
- [ ] 实现 UserMapper 接口
- [ ] 实现 UserService 接口及实现类
- [ ] 实现 UserController 控制器
- [ ] 实现 JwtUtil 工具类
- [ ] 实现 PasswordUtil 工具类
- [ ] 实现 ResultUtils 工具类
- [ ] 实现 @AuthCheck 注解
- [ ] 实现 AuthInterceptor 拦截器

### 前端任务
- [ ] 封装用户相关 API
- [ ] 实现登录用户状态管理
- [ ] 实现登录页面
- [ ] 实现注册页面
- [ ] 配置路由
- [ ] 实现请求拦截器
- [ ] 实现响应拦截器
- [ ] 实现权限控制函数

## 💡 实现提示

1. **密码安全**：使用 BCrypt 加密，不要明文存储密码
2. **Token 管理**：Token 存储在 Redis，支持过期时间
3. **权限控制**：使用 AOP 实现统一的权限控制
4. **参数校验**：使用 Jakarta Validation 注解进行参数校验
5. **错误处理**：使用全局异常处理器统一处理错误
6. **前端安全**：Token 存储在 localStorage，每次请求自动携带

## 🔍 测试建议

1. 测试正常注册流程
2. 测试账号已存在的场景
3. 测试密码不一致的场景
4. 测试正常登录流程
5. 测试账号密码错误的场景
6. 测试 Token 过期场景
7. 测试权限控制是否生效

## 📚 参考资源

- Spring Security 官方文档
- JWT 官方文档
- MyBatis-Flex 官方文档
- Pinia 官方文档
- Axios 官方文档
