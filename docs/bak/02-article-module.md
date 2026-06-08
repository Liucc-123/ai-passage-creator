# 文章基础模块设计文档

## 📌 模块概述

文章模块是系统的核心模块之一，负责文章的创建、编辑、查询、删除等基础功能。本模块支持文章状态管理、分阶段创作、图文存储等功能。

## 🎯 学习目标

完成本模块后，你将能够：
1. 理解文章状态机的设计
2. 实现文章的 CRUD 操作
3. 掌握分阶段创作的数据结构设计
4. 实现文章列表和详情展示

## 🗄️ 数据库设计

### article 表

```sql
CREATE TABLE article (
    id              bigint auto_increment comment 'id' primary key,
    taskId          varchar(64) not null comment '任务ID（UUID）',
    userId          bigint not null comment '用户ID',
    topic           varchar(500) not null comment '选题',
    mainTitle       varchar(200) null comment '主标题',
    subTitle        varchar(300) null comment '副标题',
    outline         json null comment '大纲（JSON格式）',
    content         text null comment '正文（Markdown格式）',
    fullContent     text null comment '完整图文（Markdown格式，含配图）',
    coverImage      varchar(512) null comment '封面图 URL',
    images          json null comment '配图列表（JSON数组，包含封面图 position=1）',
    style           varchar(50) null comment '文章风格',
    phase           varchar(50) null comment '当前阶段',
    status          varchar(20) default 'PENDING' not null comment '状态：PENDING/PROCESSING/COMPLETED/FAILED',
    errorMessage    text null comment '错误信息',
    createTime      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    completedTime   datetime null comment '完成时间',
    updateTime      datetime default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint default 0 not null comment '是否删除',
    UNIQUE KEY uk_taskId (taskId),
    INDEX idx_userId (userId),
    INDEX idx_status (status),
    INDEX idx_createTime (createTime),
    INDEX idx_userId_status (userId, status)
) comment '文章表';
```

**关键字段说明：**
- `taskId`: 任务唯一标识，用于追踪整个创作流程
- `phase`: 当前创作阶段（TITLE_SELECTION/OUTLINE_EDITING/CONTENT_GENERATION/COMPLETED）
- `status`: 文章状态（PENDING/PROCESSING/COMPLETED/FAILED）
- `outline`: 大纲数据，JSON 格式存储
- `images`: 配图列表，JSON 数组格式

## 🔧 后端实现

### 1. 实体类设计

**路径：** `src/main/java/com/yupi/template/model/entity/Article.java`

**设计要点：**
- 使用 MyBatis-Flex 注解
- JSON 字段使用 `@Column(isLarge = true)` 注解
- 添加字段注释

**参考字段：**
```java
@Data
@Table("article")
public class Article implements Serializable {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private String taskId;
    private Long userId;
    private String topic;
    private String mainTitle;
    private String subTitle;
    private String outline;
    private String content;
    private String fullContent;
    private String coverImage;
    private String images;
    private String style;
    private String phase;
    private String status;
    private String errorMessage;
    private Date createTime;
    private Date completedTime;
    private Date updateTime;
    private Integer isDelete;
}
```

### 2. 枚举类设计

#### 2.1 文章状态枚举

**路径：** `src/main/java/com/yupi/template/model/enums/ArticleStatusEnum.java`

```java
public enum ArticleStatusEnum {
    PENDING("PENDING", "待处理"),
    PROCESSING("PROCESSING", "处理中"),
    COMPLETED("COMPLETED", "已完成"),
    FAILED("FAILED", "失败");

    private final String value;
    private final String text;

    // 构造函数、getter 方法
}
```

#### 2.2 文章阶段枚举

**路径：** `src/main/java/com/yupi/template/model/enums/ArticlePhaseEnum.java`

```java
public enum ArticlePhaseEnum {
    TITLE_SELECTION("TITLE_SELECTION", "标题选择"),
    OUTLINE_EDITING("OUTLINE_EDITING", "大纲编辑"),
    CONTENT_GENERATION("CONTENT_GENERATION", "正文生成"),
    COMPLETED("COMPLETED", "已完成");

    private final String value;
    private final String text;

    // 构造函数、getter 方法
}
```

#### 2.3 文章风格枚举

**路径：** `src/main/java/com/yupi/template/model/enums/ArticleStyleEnum.java`

```java
public enum ArticleStyleEnum {
    TECH("TECH", "科技风格"),
    EMOTIONAL("EMOTIONAL", "情感风格"),
    EDUCATIONAL("EDUCATIONAL", "教育风格"),
    HUMOROUS("HUMOROUS", "轻松幽默");

    private final String value;
    private final String text;

    // 构造函数、getter 方法
}
```

### 3. DTO 设计

**路径：** `src/main/java/com/yupi/template/model/dto/article/`

需要创建以下 DTO：

1. **ArticleCreateRequest.java** - 创建文章请求
   - topic: 选题
   - style: 文章风格
   - userDescription: 用户描述

2. **ArticleQueryRequest.java** - 查询文章请求
   - id: 文章ID
   - taskId: 任务ID
   - userId: 用户ID
   - status: 状态
   - 支持分页查询

3. **ArticleState.java** - 文章状态对象
   - taskId: 任务ID
   - topic: 选题
   - style: 风格
   - titleOptions: 标题选项列表
   - outline: 大纲结果
   - content: 正文内容
   - images: 配图列表
   - fullContent: 完整内容

4. **ArticleConfirmTitleRequest.java** - 确认标题请求
   - taskId: 任务ID
   - titleIndex: 选中的标题索引

5. **ArticleConfirmOutlineRequest.java** - 确认大纲请求
   - taskId: 任务ID
   - outline: 大纲内容

6. **ArticleAiModifyOutlineRequest.java** - AI 优化大纲请求
   - taskId: 任务ID
   - modifyInstruction: 修改指令

### 4. VO 设计

**路径：** `src/main/java/com/yupi/template/model/vo/`

需要创建以下 VO：

**ArticleVO.java** - 文章信息展示
```java
@Data
public class ArticleVO implements Serializable {
    private Long id;
    private String taskId;
    private Long userId;
    private String topic;
    private String mainTitle;
    private String subTitle;
    private String outline;
    private String content;
    private String fullContent;
    private String coverImage;
    private String images;
    private String style;
    private String phase;
    private String status;
    private String errorMessage;
    private Date createTime;
    private Date completedTime;
}
```

### 5. Mapper 接口设计

**路径：** `src/main/java/com/yupi/template/mapper/ArticleMapper.java`

```java
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {
    // 基础 CRUD 由 BaseMapper 提供
}
```

### 6. Service 层设计

**路径：** `src/main/java/com/yupi/template/service/ArticleService.java`

**核心方法：**

1. **创建文章**
   ```java
   Long createArticle(ArticleCreateRequest request, HttpServletRequest httpRequest);
   ```
   - 生成唯一 taskId
   - 初始化文章状态为 PENDING
   - 保存文章信息
   - 返回文章ID

2. **查询文章详情**
   ```java
   ArticleVO getArticleById(Long id, HttpServletRequest httpRequest);
   ```
   - 根据ID查询文章
   - 校验文章归属
   - 返回文章VO

3. **分页查询文章列表**
   ```java
   Page<ArticleVO> listArticleByPage(ArticleQueryRequest request);
   ```
   - 支持分页
   - 支持条件过滤
   - 返回文章VO列表

4. **删除文章**
   ```java
   boolean deleteArticle(Long id, HttpServletRequest httpRequest);
   ```
   - 校验文章归属
   - 逻辑删除文章
   - 返回是否成功

5. **更新文章状态**
   ```java
   boolean updateArticleStatus(String taskId, String status);
   ```
   - 根据 taskId 更新状态
   - 记录完成时间（如果完成）
   - 返回是否成功

6. **更新文章阶段**
   ```java
   boolean updateArticlePhase(String taskId, String phase);
   ```
   - 根据 taskId 更新阶段
   - 返回是否成功

### 7. Controller 层设计

**路径：** `src/main/java/com/yupi/template/controller/ArticleController.java`

**接口列表：**

1. POST `/api/article/create` - 创建文章
2. GET `/api/article/get` - 获取文章详情
3. POST `/api/article/list/page` - 分页查询文章列表
4. POST `/api/article/delete` - 删除文章
5. POST `/api/article/confirm/title` - 确认标题
6. POST `/api/article/confirm/outline` - 确认大纲
7. POST `/api/article/ai/modify/outline` - AI 优化大纲

## 🎨 前端实现

### 1. API 封装

**路径：** `frontend/src/api/articleController.ts`

```typescript
import request from '../request';

// 创建文章
export const createArticle = (data: API.ArticleCreateRequest) => {
  return request.post('/api/article/create', data);
};

// 获取文章详情
export const getArticleById = (id: number) => {
  return request.get(`/api/article/get?id=${id}`);
};

// 分页查询文章列表
export const listArticleByPage = (data: API.ArticleQueryRequest) => {
  return request.post('/api/article/list/page', data);
};

// 删除文章
export const deleteArticle = (id: number) => {
  return request.post('/api/article/delete', { id });
};

// 确认标题
export const confirmTitle = (data: API.ArticleConfirmTitleRequest) => {
  return request.post('/api/article/confirm/title', data);
};

// 确认大纲
export const confirmOutline = (data: API.ArticleConfirmOutlineRequest) => {
  return request.post('/api/article/confirm/outline', data);
};

// AI 优化大纲
export const aiModifyOutline = (data: API.ArticleAiModifyOutlineRequest) => {
  return request.post('/api/article/ai/modify/outline', data);
};
```

### 2. 页面实现

#### 2.1 文章列表页

**路径：** `frontend/src/pages/article/ArticleListPage.vue`

**功能要点：**
- 表格展示文章列表
- 支持分页
- 支持状态筛选
- 支持删除操作
- 支持跳转到详情页

#### 2.2 文章详情页

**路径：** `frontend/src/pages/article/ArticleDetailPage.vue`

**功能要点：**
- 展示文章完整信息
- Markdown 渲染
- 支持导出 Markdown
- 支持删除文章

#### 2.3 文章创建页

**路径：** `frontend/src/pages/article/ArticleCreatePage.vue`

**功能要点：**
- 输入选题
- 选择文章风格
- 输入用户描述
- 支持分阶段创作
- 实时展示创作进度

### 3. 常量定义

**路径：** `frontend/src/constants/article.ts`

```typescript
export const ARTICLE_STATUS_MAP = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  FAILED: '失败',
};

export const ARTICLE_PHASE_MAP = {
  TITLE_SELECTION: '标题选择',
  OUTLINE_EDITING: '大纲编辑',
  CONTENT_GENERATION: '正文生成',
  COMPLETED: '已完成',
};

export const ARTICLE_STYLE_OPTIONS = [
  { label: '科技风格', value: 'TECH' },
  { label: '情感风格', value: 'EMOTIONAL' },
  { label: '教育风格', value: 'EDUCATIONAL' },
  { label: '轻松幽默', value: 'HUMOROUS' },
];
```

## 📝 学习任务清单

### 后端任务
- [ ] 创建 article 表
- [ ] 实现 Article 实体类
- [ ] 实现 ArticleStatusEnum 枚举
- [ ] 实现 ArticlePhaseEnum 枚举
- [ ] 实现 ArticleStyleEnum 枚举
- [ ] 实现文章相关 DTO
- [ ] 实现 ArticleVO
- [ ] 实现 ArticleMapper 接口
- [ ] 实现 ArticleService 接口及实现类
- [ ] 实现 ArticleController 控制器

### 前端任务
- [ ] 封装文章相关 API
- [ ] 实现文章列表页
- [ ] 实现文章详情页
- [ ] 实现文章创建页
- [ ] 定义文章相关常量

## 💡 实现提示

1. **状态管理**：使用状态机模式管理文章状态转换
2. **数据验证**：创建文章时验证选题长度和风格合法性
3. **权限控制**：用户只能操作自己的文章
4. **分页查询**：使用 MyBatis-Flex 的分页插件
5. **JSON 存储**：大纲和配图列表使用 JSON 格式存储
6. **Markdown 渲染**：前端使用 markdown-it 或类似库渲染

## 🔍 测试建议

1. 测试创建文章流程
2. 测试文章状态转换
3. 测试文章阶段转换
4. 测试分页查询功能
5. 测试文章删除功能
6. 测试权限控制是否生效
7. 测试 Markdown 渲染是否正确

## 📚 参考资源

- MyBatis-Flex 官方文档
- Spring Boot 官方文档
- Vue 3 官方文档
- Ant Design Vue 文档
- markdown-it 文档
