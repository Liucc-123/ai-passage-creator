# 模块1：基础架构搭建

> 目标：项目能启动，连上 MySQL + Redis，健康检查接口返回 OK

## 技术选型

| 层面 | 选择 | 说明 |
|------|------|------|
| 框架 | Spring Boot 3.x + JDK 21 | 项目实际使用 3.5.9，你可用 3.3+ |
| ORM | MyBatis-Flex | 比 MyBatis-Plus 更轻量，API 更简洁 |
| 数据库 | MySQL 8.0 | 通过 Docker 或本地安装 |
| 缓存/Session | Spring Data Redis + Spring Session | Session 存 Redis 实现分布式会话 |
| 连接池 | HikariCP | Spring Boot 默认自带 |
| 接口文档 | Knife4j (springdoc) | 访问 `/api/doc.html` 查看 |
| 构建工具 | Maven | |

## 项目结构

```
src/main/java/com/yourname/passage/
├── MainApplication.java          # 启动类
├── config/                       # 配置类
│   ├── CorsConfig.java           # 跨域
│   ├── JsonConfig.java           # Long → String 防精度丢失
│   └── AsyncConfig.java          # 异步线程池（后续模块用）
├── constant/                     # 常量
├── controller/                   # 控制器
├── exception/                    # 全局异常处理
├── mapper/                       # MyBatis-Flex Mapper
├── model/
│   ├── entity/                   # 数据库实体
│   ├── dto/                      # 请求对象
│   ├── vo/                       # 响应对象
│   └── enums/                    # 枚举
├── service/                      # 业务接口
│   └── impl/                     # 业务实现
└── utils/                        # 工具类
```

## 数据库设计

这是整个项目的数据基础，后续所有模块共享这些表。建议在模块1一次性建好全部表，后续模块只需关注对应的字段。

### user 表

```sql
CREATE TABLE user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    userAccount VARCHAR(256) NOT NULL COMMENT '账号',
    userPassword VARCHAR(512) NOT NULL COMMENT '密码（MD5+盐加密）',
    userName    VARCHAR(256) NULL     COMMENT '用户名',
    userAvatar  VARCHAR(1024) NULL    COMMENT '头像',
    userProfile VARCHAR(512) NULL     COMMENT '简介',
    userRole    VARCHAR(256) NOT NULL DEFAULT 'user' COMMENT '角色: user/admin',
    vipTime     DATETIME     NULL     COMMENT 'VIP 过期时间',
    quota       INT          NOT NULL DEFAULT 5 COMMENT '剩余配额',
    editTime    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    createTime  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    isDelete    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_userAccount (userAccount)
);
```

### article 表

```sql
CREATE TABLE article (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    taskId               VARCHAR(128) NOT NULL COMMENT '任务UUID',
    userId               BIGINT       NOT NULL COMMENT '创建用户',
    topic                TEXT         NOT NULL COMMENT '创作主题',
    userDescription      TEXT         NULL     COMMENT '用户补充描述',
    enabledImageMethods  JSON         NULL     COMMENT '允许的配图方式 ["PEXELS","MERMAID"]',
    style                VARCHAR(20)  NULL     COMMENT '文章风格: TECH/EMOTIONAL/EDUCATIONAL/HUMOROUS',
    mainTitle            VARCHAR(512) NULL     COMMENT '主标题',
    subTitle             VARCHAR(512) NULL     COMMENT '副标题',
    titleOptions         JSON         NULL     COMMENT '标题方案列表',
    outline              JSON         NULL     COMMENT '大纲（JSON结构）',
    content              LONGTEXT     NULL     COMMENT '纯文本正文',
    fullContent          LONGTEXT     NULL     COMMENT '图文正文（含配图Markdown）',
    coverImage           VARCHAR(1024) NULL    COMMENT '封面图URL',
    images               JSON         NULL     COMMENT '配图列表',
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/PROCESSING/COMPLETED/FAILED',
    phase                VARCHAR(50)  NULL     COMMENT '当前阶段',
    errorMessage         TEXT         NULL     COMMENT '错误信息',
    createTime           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completedTime        DATETIME     NULL     COMMENT '完成时间',
    updateTime           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    isDelete             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_taskId (taskId),
    KEY idx_userId (userId)
);
```

### agent_log 表

```sql
CREATE TABLE agent_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    taskId       VARCHAR(128) NOT NULL COMMENT '关联任务',
    agentName    VARCHAR(128) NOT NULL COMMENT 'Agent名称',
    startTime    DATETIME     NOT NULL,
    endTime      DATETIME     NULL,
    durationMs   BIGINT       NULL     COMMENT '执行耗时(毫秒)',
    status       VARCHAR(20)  NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED',
    errorMessage TEXT         NULL,
    prompt       TEXT         NULL     COMMENT '使用的Prompt',
    inputData    JSON         NULL     COMMENT '输入数据摘要',
    outputData   JSON         NULL     COMMENT '输出数据摘要',
    createTime   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    isDelete     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_taskId (taskId)
);
```

### payment_record 表

```sql
CREATE TABLE payment_record (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    userId              BIGINT        NOT NULL,
    stripeSessionId     VARCHAR(256)  NULL,
    stripePaymentIntentId VARCHAR(256) NULL,
    amount              DECIMAL(10,2) NOT NULL,
    currency            VARCHAR(10)   NOT NULL DEFAULT 'CNY',
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    productType         VARCHAR(50)   NOT NULL,
    description         VARCHAR(512)  NULL,
    refundTime          DATETIME      NULL,
    refundReason        VARCHAR(512)  NULL,
    createTime          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    isDelete            TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_userId (userId)
);
```

## 配置体系

设计三份配置文件：

| 文件 | 用途 |
|------|------|
| `application.yml` | 公共配置（端口、MyBatis、Session 超时等），不含敏感信息 |
| `application-local.yml` | 本地开发覆盖（数据库地址、API Key），**加入 .gitignore** |
| `application-prod.yml` | 生产环境，所有敏感值用 `${ENV_VAR}` 占位 |

关键配置项：

```yaml
server:
  port: 8567
  servlet:
    context-path: /api

spring:
  session:
    store-type: redis
    timeout: 2592000        # 30天
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/ai_passage_creator
    username: root
    password: 123456
  data:
    redis:
      host: localhost
      port: 6379

mybatis-flex:
  configuration:
    map-underscore-to-camel-case: false  # 注意：本项目关闭了驼峰映射
```

> **为什么关闭驼峰映射？** 项目中数据库列名直接用驼峰（如 `taskId` 而非 `task_id`），所以 MyBatis-Flex 不需要做转换。你可以选择保留驼峰映射然后改列名为下划线风格，但需要注意和原项目保持一致。

## 全局响应封装

设计一个统一响应类 `BaseResponse<T>`：

```java
public class BaseResponse<T> {
    private int code;       // 状态码，0=成功
    private T data;         // 数据
    private String message; // 提示信息
}
```

所有 Controller 返回 `BaseResponse<T>`，通过全局异常处理器捕获 `BusinessException` 统一包装错误响应。

## 本模块验证标准

- [ ] Spring Boot 启动成功，访问 `/api/health/` 返回 OK
- [ ] MySQL 连接成功，4 张表自动创建/手动建好
- [ ] Redis 连接成功，Session 能写入 Redis
- [ ] Knife4j 文档页面可访问 (`/api/doc.html`)
- [ ] CORS 配置生效，前端开发服务器能跨域调用
