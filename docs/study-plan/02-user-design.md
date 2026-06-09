# 用户模块设计文档

> **版本**: 1.0  
> **日期**: 2026-06-09  
> **状态**: 已确认，待实施

---

## 概述

本文档描述 AI Passage Creator 项目的用户模块设计，基于 `docs/study-plan/02-user.md` 的规划。

### 目标

- [x] 注册/登录/登出跑通
- [x] Session 写入 Redis
- [x] 管理员接口有权限拦截

---

## 技术选型

| 组件 | 选择 | 理由 |
|------|------|------|
| ORM | MyBatis-Flex | 轻量级，API 简洁，已在本项目使用 |
| Session 存储 | Redis + Spring Session | 分布式会话支持，支持主动删除 |
| 密码加密 | MD5 + 随机盐值 | 实现简单，符合学习路径 |
| 登录失败限制 | Redis 计数 | 无需额外表，实现轻量 |
| 鉴权方式 | 拦截器 + @AuthCheck 注解 | 符合项目现有模式 |

---

## 数据模型

### User 实体（已存在，无需修改）

对应数据库表 `user`：

```java
@Table(name = "user")
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String userAccount;       // 登录账号，唯一索引
    private String userPassword;      // MD5 + 盐加密，格式："salt$md5(salt+password)"
    private String userName;          // 展示用户名，可选（默认同账号）
    private String userAvatar;        // 头像 URL
    private String userProfile;       // 简介
    private String userRole;          // 角色："user" / "admin"
    private Date vipTime;             // VIP 过期时间，null 表示非 VIP
    private Integer quota;            // 剩余创作配额，默认 5
    private Date editTime;            // 更新时间
    private Date createTime;          // 创建时间
    private Date updateTime;          // 更新时间
    private Integer isDelete;         // 逻辑删除，0=未删除，1=已删除
}
```

### UserVO（新增响应对象）

前端展示的脱敏版本，不包含敏感字段：

```java
@Data
public class UserVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String userAccount;   // 登录账号（可显示）
    private String userName;      // 展示用户名
    private String userAvatar;    // 头像 URL
    private String userProfile;   // 简介
    private String userRole;      // 角色
    private Integer quota;        // 剩余配额
    private Integer vipType;      // VIP 类型：0=普通，1=VIP
}
```

转换工具方法：

```java
public static UserVO userToUserVO(User user) {
    if (user == null) {
        return null;
    }
    UserVO userVO = new UserVO();
    BeanUtils.copyProperties(user, userVO);
    // 计算 VIP 状态
    if (user.getVipTime() != null && user.getVipTime().after(new Date())) {
        userVO.setVipType(1);
    } else {
        userVO.setVipType(0);
    }
    return userVO;
}
```

---

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

### 请求/响应对象

#### UserRegisterRequest - 注册请求

```java
@Data
public class UserRegisterRequest {
    /**
     * 登录账号
     */
    private String userAccount;

    /**
     * 密码（6-20 位，需包含字母和数字）
     */
    private String userPassword;

    /**
     * 确认密码
     */
    private String confirmPassword;
}
```

#### UserLoginRequest - 登录请求

```java
@Data
public class UserLoginRequest {
    /**
     * 登录账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;
}
```

#### UserUpdateRequest - 更新用户请求

```java
@Data
public class UserUpdateRequest {
    /**
     * 用户 ID
     */
    private Long id;

    /**
     * 用户名（可选）
     */
    private String userName;

    /**
     * 头像 URL（可选）
     */
    private String userAvatar;

    /**
     * 简介（可选）
     */
    private String userProfile;
}
```

#### PageRequest - 分页请求（复用通用类）

```java
@Data
public class PageRequest {
    /**
     * 当前页码
     */
    private int current = 1;

    /**
     * 每页大小
     */
    private int pageSize = 10;
}
```

#### PageInfo<T> - 分页响应

```java
@Data
public class PageInfo<T> implements Serializable {
    private List<T> records;      // 记录列表
    private long total;           // 总记录数
    private int current;          // 当前页
    private int pageSize;         // 每页大小
    private long totalPage;       // 总页数
}
```

---

## 错误码设计

```java
@Getter
public enum ErrorCode {
    // 成功
    SUCCESS(20000, "请求成功"),

    // 用户模块错误
    USER_NOT_LOGIN(20100, "未登录"),
    USER_ROLE_ERROR(20101, "权限不足"),
    USER_ACCOUNT_EXIST(20102, "账号已存在"),
    USER_PASSWORD_FORMAT_ERROR(20103, "密码格式错误"),
    USER_LOCKED(20104, "账户已被暂时锁定"),
    USER_PASSWORD_ERROR(20105, "密码错误"),
    USER_CONFIRM_PASSWORD_ERROR(20106, "两次输入的密码不一致");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
```

---

## 核心实现逻辑

### 1. 密码加密工具类

```java
package com.liucc.passage.utils;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * 密码工具类
 * 使用 MD5 + 随机盐值加密
 */
public class PasswordUtils {

    private static final String SPLIT = "$";

    /**
     * 加密密码
     * 格式：salt$md5(salt+password)
     *
     * @param password 明文密码
     * @return 加密后的字符串
     */
    public static String encrypt(String password) {
        if (!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("密码不能为空");
        }
        // 生成 8 位随机盐值
        String salt = UUID.randomUUID().toString().substring(0, 8).toLowerCase();
        // MD5 加密
        String encrypted = md5(salt + password);
        // 返回盐值 + 密文
        return salt + SPLIT + encrypted;
    }

    /**
     * 验证密码
     *
     * @param encryptedPassword 加密后的密码（格式：salt$md5...）
     * @param inputPassword     输入的明文密码
     * @return 是否匹配
     */
    public static boolean validate(String encryptedPassword, String inputPassword) {
        if (!StringUtils.hasText(encryptedPassword) || !StringUtils.hasText(inputPassword)) {
            return false;
        }
        // 分割盐值和密文
        int splitIndex = encryptedPassword.indexOf(SPLIT);
        if (splitIndex == -1) {
            return false;
        }
        String salt = encryptedPassword.substring(0, splitIndex);
        String expectedHash = encryptedPassword.substring(splitIndex + 1);
        // 重新计算 MD5
        String actualHash = md5(salt + inputPassword);
        // 比较
        return expectedHash.equals(actualHash);
    }

    /**
     * MD5 加密
     */
    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 加密失败", e);
        }
    }
}
```

### 2. 登录失败限制服务

```java
package com.liucc.passage.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 登录失败次数限制服务
 * 连续失败 5 次 → 锁定 15 分钟
 */
@Component
public class LoginFailService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final int MAX_FAIL_COUNT = 5;
    private static final long LOCK_TIME_MINUTES = 15;

    /**
     * 记录登录失败次数
     *
     * @param userAccount 用户账号
     */
    public void recordFail(String userAccount) {
        if (!StringUtils.hasText(userAccount)) {
            return;
        }

        String countKey = "login_fail_count:" + userAccount;
        String lockKey = "login_fail_lock:" + userAccount;

        // 检查是否已经被锁定
        if (redisTemplate.hasKey(lockKey)) {
            throw new BusinessException(ErrorCode.USER_LOCKED);
        }

        // 获取当前失败次数
        Long count = (Long) redisTemplate.opsForValue().get(countKey);
        if (count == null) {
            count = 0L;
        }

        // 判断是否需要锁定
        if (count >= MAX_FAIL_COUNT) {
            // 设置锁定
            redisTemplate.opsForValue().set(lockKey, System.currentTimeMillis(), LOCK_TIME_MINUTES, TimeUnit.MINUTES);
            throw new BusinessException(ErrorCode.USER_LOCKED);
        }

        // 递增失败次数，设置 TTL
        redisTemplate.opsForValue().set(countKey, count + 1, LOCK_TIME_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 重置登录失败次数
     *
     * @param userAccount 用户账号
     */
    public void resetFail(String userAccount) {
        if (!StringUtils.hasText(userAccount)) {
            return;
        }
        redisTemplate.delete("login_fail_count:" + userAccount);
        redisTemplate.delete("login_fail_lock:" + userAccount);
    }

    /**
     * 检查是否被锁定
     *
     * @param userAccount 用户账号
     * @return 是否被锁定
     */
    public boolean isLocked(String userAccount) {
        if (!StringUtils.hasText(userAccount)) {
            return false;
        }
        String lockKey = "login_fail_lock:" + userAccount;
        return redisTemplate.hasKey(lockKey);
    }
}
```

### 3. 全局异常处理器

```java
package com.liucc.passage.exception;

import com.liucc.passage.common.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常处理
     */
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> handleException(BusinessException e) {
        log.warn("业务异常：{}", e.getErrorCode().getMessage());
        return new BaseResponse<>(e.getErrorCode());
    }

    /**
     * 其他异常处理
     */
    @ExceptionHandler(Exception.class)
    public BaseResponse<?> handleException(Exception e) {
        log.error("系统异常", e);
        return new BaseResponse<>(ErrorCode.SYSTEM_ERROR);
    }
}
```

补充错误码（在 ErrorCode 枚举中添加）：

```java
SYSTEM_ERROR(50000, "系统异常");
```

### 4. BusinessException 异常类

```java
package com.liucc.passage.exception;

import lombok.Getter;

/**
 * 业务异常类
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode code) {
        super(code.getMessage());
        this.errorCode = code;
    }
}
```

### 5. 鉴权注解

```java
package com.liucc.passage.annotation;

import java.lang.annotation.*;

/**
 * 权限校验注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthCheck {

    /**
     * 必需的角色
     * "admin" - 必须是管理员
     * "" - 任意角色（用于装饰性注解）
     */
    String mustRole() default "";
}
```

### 6. 鉴权拦截器

```java
package com.liucc.passage.config;

import com.liucc.passage.annotation.AuthCheck;
import com.liucc.passage.constant.UserConstant;
import com.liucc.passage.exception.BusinessException;
import com.liucc.passage.exception.ErrorCode;
import com.liucc.passage.model.entity.User;
import lombok.extern.slf4j.Slf4j;
org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 鉴权拦截器
 * 1. 校验用户是否登录
 * 2. 校验是否有指定权限
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /**
     * Session 中用户常量名
     */
    public static final String USER_LOGIN_STATE = "user_login";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 只处理 Controller 方法
        if (!(handler instanceof org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMethod)) {
            return true;
        }

        HandlerMethod method = (HandlerMethod) handler;
        AuthCheck authCheck = method.getMethodAnnotation(AuthCheck.class);

        // 没有配置权限注解，直接放行
        if (authCheck == null) {
            return true;
        }

        // 1. 校验用户是否登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }

        User loginUser = (User) userObj;

        // 2. 如果指定了必须角色，进行权限校验
        String mustRole = authCheck.mustRole();
        if (StringUtils.hasText(mustRole)) {
            if (!mustRole.equals(loginUser.getUserRole())) {
                throw new BusinessException(ErrorCode.USER_ROLE_ERROR);
            }
        }

        return true;
    }
}
```

### 7. 拦截器注册配置

```java
package com.liucc.passage.config;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置类
 */
@Configuration
@AllArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/user/**")
                .excludePathPatterns(
                        "/user/register",
                        "/user/login"
                );
    }
}
```

### 8. 用户常量

```java
package com.liucc.passage.constant;

/**
 * 用户相关常量
 */
public class UserConstant {

    /**
     * Session 中用户 Key
     */
    public static final String USER_LOGIN_STATE = "user_login";

    /**
     * 普通用户角色
     */
    public static final String USER_ROLE = "user";

    /**
     * 管理员角色
     */
    public static final String ADMIN_ROLE = "admin";
}
```

---

## Service 层接口设计

### UserService 接口

```java
package com.liucc.passage.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liucc.passage.exception.BusinessException;
import com.liucc.passage.model.dto.UserLoginRequest;
import com.liucc.passage.model.dto.UserRegisterRequest;
import com.liucc.passage.model.dto.UserUpdateRequest;
import com.liucc.passage.model.entity.User;
import com.liucc.passage.model.vo.UserVO;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户注册
     *
     * @param userRegisterRequest 注册信息
     * @return 注册成功的用户 VO
     * @throws BusinessException 账号已存在、密码错误等
     */
    UserVO userRegister(UserRegisterRequest userRegisterRequest);

    /**
     * 用户登录
     *
     * @param userLoginRequest 登录信息
     * @param request HTTP 请求
     * @return 登录成功的用户 VO
     * @throws BusinessException 密码错误、账户锁定等
     */
    UserVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request);

    /**
     * 用户登出
     *
     * @param request HTTP 请求
     */
    void userLogout(HttpServletRequest request);

    /**
     * 获取当前登录用户
     *
     * @param request HTTP 请求
     * @return 当前登录用户 VO
     */
    UserVO getLoginUser(HttpServletRequest request);

    /**
     * 是否为管理员
     *
     * @param user 用户
     * @return 是否管理员
     */
    boolean isAdmin(User user);

    /**
     * 是否为普通用户
     *
     * @param user 用户
     * @return 是否普通用户
     */
    boolean isUser(User user);

    /**
     * 添加用户（管理员）
     */
    Long addUser(User user);

    /**
     * 根据 ID 查询用户（管理员）
     */
    UserVO getUserById(Long id);

    /**
     * 更新用户信息（管理员）
     */
    Boolean updateUser(User user);

    /**
     * 删除用户（管理员，逻辑删除）
     */
    Boolean deleteUser(Long id);

    /**
     * 分页查询用户（管理员）
     */
    Page<UserVO> listUserByPage(com.liucc.passage.model.dto.UserQueryRequest queryRequest);
}
```

### UserServiceImpl 实现（关键代码）

```java
package com.liucc.passage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liucc.passage.constant.UserConstant;
import com.liucc.passage.exception.BusinessException;
import com.liucc.passage.exception.ErrorCode;
import com.liucc.passage.mapper.UserMapper;
import com.liucc.passage.model.dto.UserLoginRequest;
import com.liucc.passage.model.dto.UserQueryRequest;
import com.liucc.passage.model.dto.UserRegisterRequest;
import com.liucc.passage.model.dto.UserUpdateRequest;
import com.liucc.passage.model.entity.User;
import com.liucc.passage.model.vo.UserVO;
import com.liucc.passage.service.LoginFailService;
import com.liucc.passage.service.UserService;
import com.liucc.passage.utils.PasswordUtils;
import com.liucc.passage.utils.UserConvertUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;

/**
 * 用户服务实现
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private LoginFailService loginFailService;

    @Override
    public UserVO userRegister(UserRegisterRequest userRegisterRequest) {
        // 1. 参数校验
        if (!StringUtils.hasText(userRegisterRequest.getUserAccount()) ||
            !StringUtils.hasText(userRegisterRequest.getUserPassword()) ||
            !StringUtils.hasText(userRegisterRequest.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        String account = userRegisterRequest.getUserAccount().trim();
        String password = userRegisterRequest.getUserPassword();
        String confirmPassword = userRegisterRequest.getConfirmPassword();

        // 校验账号格式
        if (account.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号不能少于 4 位");
        }

        // 校验密码格式
        if (password.length() < 6 || password.length() > 20) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_FORMAT_ERROR);
        }
        if (!Pattern.matches(".*[A-Za-z].*[0-9].*|.*[0-9].*[A-Za-z].*", password)) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_FORMAT_ERROR, "密码需包含字母和数字");
        }

        // 校验两次密码是否一致
        if (!password.equals(confirmPassword)) {
            throw new BusinessException(ErrorCode.USER_CONFIRM_PASSWORD_ERROR);
        }

        // 2. 检查账号是否已存在
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserAccount, account);
        long count = userMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.USER_ACCOUNT_EXIST);
        }

        // 3. 设置默认用户名
        String userName = userRegisterRequest.getUserName();
        if (!StringUtils.hasText(userName)) {
            userName = account;
        }

        // 4. 加密密码并保存
        String encryptedPassword = PasswordUtils.encrypt(password);
        User user = new User();
        user.setUserAccount(account);
        user.setUserPassword(encryptedPassword);
        user.setUserName(userName);
        user.setUserRole(UserConstant.USER_ROLE);
        user.setQuota(5); // 默认配额 5

        boolean result = userMapper.insert(user);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        // 5. 自动登录
        return userLoginByEntity(user);
    }

    @Override
    public UserVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request) {
        // 1. 参数校验
        if (!StringUtils.hasText(userLoginRequest.getUserAccount()) ||
            !StringUtils.hasText(userLoginRequest.getUserPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        String account = userLoginRequest.getUserAccount().trim();
        String password = userLoginRequest.getUserPassword();

        // 2. 检查是否被锁定
        if (loginFailService.isLocked(account)) {
            throw new BusinessException(ErrorCode.USER_LOCKED);
        }

        // 3. 查询用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserAccount, account);
        User user = userMapper.selectOne(queryWrapper);

        // 4. 验证密码
        if (user == null || !PasswordUtils.validate(user.getUserPassword(), password)) {
            // 记录失败次数
            loginFailService.recordFail(account);
            throw new BusinessException(ErrorCode.USER_PASSWORD_ERROR);
        }

        // 5. 登录成功，清除失败计数
        loginFailService.resetFail(account);

        // 6. 写入 Session
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);

        // 7. 返回用户 VO
        return userToUserVO(user);
    }

    @Override
    public void userLogout(HttpServletRequest request) {
        if (request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE) != null) {
            request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        }
    }

    @Override
    public UserVO getLoginUser(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        User user = (User) userObj;
        return userToUserVO(user);
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserConstant.ADMIN_ROLE.equals(user.getUserRole());
    }

    @Override
    public boolean isUser(User user) {
        return user != null && UserConstant.USER_ROLE.equals(user.getUserRole());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addUser(User user) {
        // 管理员创建用户时，需要设置初始密码
        if (!StringUtils.hasText(user.getUserPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码不能为空");
        }
        user.setUserPassword(PasswordUtils.encrypt(user.getUserPassword()));
        user.setUserRole(UserConstant.USER_ROLE);
        user.setQuota(5);
        boolean result = userMapper.insert(user);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        return user.getId();
    }

    @Override
    public UserVO getUserById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return userToUserVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateUser(User user) {
        if (user == null || user.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return userMapper.updateById(user) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setIsDelete(1);
        return userMapper.updateById(user) > 0;
    }

    @Override
    public Page<UserVO> listUserByPage(UserQueryRequest queryRequest) {
        Page<User> page = new Page<>(queryRequest.getCurrent(), queryRequest.getPageSize());
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getIsDelete, 0)
                    .orderByDesc(User::getCreateTime);

        Page<User> userPage = userMapper.selectPage(page, queryWrapper);

        // 转换为 VO
        Page<UserVO> voPage = new Page<>();
        voPage.setRecords(userPage.getRecords().stream()
                .map(this::userToUserVO)
                .toList());
        voPage.setTotal(userPage.getTotal());
        voPage.setCurrent(userPage.getCurrent());
        voPage.setPageSize(userPage.getPageSize());
        voPage.setTotalPages(userPage.getPages());

        return voPage;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 实体转 VO
     */
    private UserVO userToUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        // 计算 VIP 类型
        if (user.getVipTime() != null && user.getVipTime().after(new Date())) {
            userVO.setVipType(1);
        } else {
            userVO.setVipType(0);
        }
        return userVO;
    }

    /**
     * 用户登录并转 VO（注册后自动登录用）
     */
    private UserVO userLoginByEntity(User user) {
        return userToUserVO(user);
    }
}
```

---

## UserController 实现

```java
package com.liucc.passage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liucc.passage.annotation.AuthCheck;
import com.liucc.passage.common.BaseResponse;
import com.liucc.passage.exception.BusinessException;
import com.liucc.passage.exception.ErrorCode;
import com.liucc.passage.model.dto.UserLoginRequest;
import com.liucc.passage.model.dto.UserQueryRequest;
import com.liucc.passage.model.dto.UserRegisterRequest;
import com.liucc.passage.model.dto.UserUpdateRequest;
import com.liucc.passage.model.entity.User;
import com.liucc.passage.model.vo.UserVO;
import com.liucc.passage.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public BaseResponse<UserVO> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserVO userVO = userService.userRegister(userRegisterRequest);
        return BaseResponse.success(userVO);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public BaseResponse<UserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        if (userLoginRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserVO userVO = userService.userLogin(userLoginRequest, request);
        return BaseResponse.success(userVO);
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        userService.userLogout(request);
        return BaseResponse.success(true);
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/get/login")
    public BaseResponse<UserVO> getLoginUser(HttpServletRequest request) {
        UserVO userVO = userService.getLoginUser(request);
        return BaseResponse.success(userVO);
    }

    /**
     * 添加用户（管理员）
     */
    @AuthCheck(mustRole = "admin")
    @PostMapping("/add")
    public BaseResponse<Long> addUser(@RequestBody User user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long id = userService.addUser(user);
        return BaseResponse.success(id);
    }

    /**
     * 根据 ID 查询用户（管理员）
     */
    @AuthCheck(mustRole = "admin")
    @GetMapping("/get")
    public BaseResponse<UserVO> getUserById(@RequestParam Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserVO userVO = userService.getUserById(id);
        return BaseResponse.success(userVO);
    }

    /**
     * 更新用户（管理员）
     */
    @AuthCheck(mustRole = "admin")
    @PostMapping("/update")
    public BaseResponse<Boolean> updateUser(@RequestBody User user) {
        if (user == null || user.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = userService.updateUser(user);
        return BaseResponse.success(result);
    }

    /**
     * 删除用户（管理员）
     */
    @AuthCheck(mustRole = "admin")
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteUser(@RequestParam Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = userService.deleteUser(id);
        return BaseResponse.success(result);
    }

    /**
     * 分页查询用户（管理员）
     */
    @AuthCheck(mustRole = "admin")
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<UserVO>> listUserByPage(@RequestBody UserQueryRequest queryRequest) {
        if (queryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Page<UserVO> userPage = userService.listUserByPage(queryRequest);
        return BaseResponse.success(userPage);
    }
}
```

---

## 其他辅助类

### UserConvertUtils（用户转换工具）

```java
package com.liucc.passage.utils;

import com.liucc.passage.model.entity.User;
import com.liucc.passage.model.vo.UserVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 用户转换工具类
 */
@Component
public class UserConvertUtils {

    /**
     * User 转 UserVO
     */
    public static UserVO toUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        // 计算 VIP 类型
        if (user.getVipTime() != null && user.getVipTime().after(new Date())) {
            userVO.setVipType(1);
        } else {
            userVO.setVipType(0);
        }
        return userVO;
    }
}
```

### UserQueryRequest（用户查询请求）

```java
package com.liucc.passage.model.dto;

import lombok.Data;

/**
 * 用户查询请求
 */
@Data
public class UserQueryRequest {
    /**
     * 当前页码
     */
    private int current = 1;

    /**
     * 每页大小
     */
    private int pageSize = 10;
}
```

---

## 配置文件变更

### application.yml

```yaml
server:
  port: 8567
  servlet:
    context-path: /api

spring:
  session:
    timeout: 86400  # Session 超时时间：1 天（秒）
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/ai_passage_creator?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: ${DB_PASSWORD:123456}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0

mybatis-flex:
  configuration:
    map-underscore-to-camel-case: false
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl

# 日志配置
logging:
  level:
    com.liucc.passage: DEBUG
```

---

## 实现顺序

建议按以下顺序实现：

### Phase 1: 基础模型与工具
1. ✅ 创建 `UserVO.java` - 响应对象
2. ✅ 创建 `UserLoginRequest.java` - 登录请求
3. ✅ 创建 `UserUpdateRequest.java` - 更新请求
4. ✅ 创建 `UserQueryRequest.java` - 查询请求
5. ✅ 创建 `UserConstant.java` - 常量定义
6. ✅ 完善 `ErrorCode.java` - 新增业务错误码
7. ✅ 创建 `BusinessException.java` - 业务异常类

### Phase 2: 工具类与服务
8. ✅ 创建 `PasswordUtils.java` - 密码加密工具
9. ✅ 创建 `LoginFailService.java` - 登录失败限制
10. ✅ 创建 `GlobalExceptionHandler.java` - 全局异常处理

### Phase 3: 鉴权机制
11. ✅ 创建 `AuthCheck.java` - 鉴权注解
12. ✅ 创建 `AuthInterceptor.java` - 鉴权拦截器
13. ✅ 创建 `WebConfig.java` - 拦截器注册配置

### Phase 4: Service 层
14. ✅ 完善 `UserService.java` 接口
15. ✅ 实现 `UserServiceImpl.java`

### Phase 5: Controller 层
16. ✅ 完善 `UserController.java` 所有接口

### Phase 6: Mapper 层
17. 确保 `UserMapper.java` 继承 `BaseMapper<User>`

---

## 验证标准

- [ ] 注册接口能创建用户，密码加密存储
- [ ] 登录接口返回成功，Redis 中能查到 Session 数据
- [ ] 登出后 Session 清除
- [ ] 未登录访问受保护接口返回 40100 错误码
- [ ] 普通用户访问管理员接口被拦截（40101）
- [ ] 分页查询返回 UserVO（不含密码）
- [ ] 连续登录失败 5 次后锁定 15 分钟
- [ ] Knife4j 文档页面可访问所有接口

---

## 注意事项

1. **密码安全性**
   - 生产环境建议使用 BCrypt 替代 MD5
   - 本项目采用 MD5+ 盐是为了简化实现，符合学习路径

2. **Session 安全**
   - Session 存储在 Redis 中，密钥建议加密传输
   - Cookie 应设置 `HttpOnly` 和 `Secure` 标志

3. **SQL 注入防护**
   - 使用 MyBatis-Flex 的 LambdaQueryWrapper，避免拼接 SQL

4. **事务管理**
   - `@Transactional` 只作用于公共方法，内部调用不生效
   - 需要在外部 Service 中调用或注入自身

5. **初始化数据**
   - 手动创建一个 admin 账号进行测试
   ```sql
   INSERT INTO user (userAccount, userPassword, userName, userRole, quota, isDelete) 
   VALUES ('admin', 'salt$md5...', '管理员', 'admin', 100, 0);
   ```

---

## 后续扩展

1. **密码强度验证** - 添加复杂度要求（特殊字符、长度等）
2. **邮箱/手机验证** - 注册时需要验证码
3. **找回密码** - 通过邮箱/手机重置密码
4. **SSO 单点登录** - 对接第三方登录（GitHub、Google 等）
5. **审计日志** - 记录用户操作日志

---

## 参考资源

- MyBatis-Flex 官方文档：https://mybatis-flex.com/
- Spring Session 文档：https://spring.io/projects/spring-session
- Apache Commons Lang: https://commons.apache.org/proper/commons-lang/
