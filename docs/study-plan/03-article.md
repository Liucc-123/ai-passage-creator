# 模块3：文章基础模块

> 目标：文章 CRUD + 状态机，能创建文章、查列表、看详情、删除

## 核心概念

文章不是一次性写入的，而是经历多个阶段逐步填充。理解这一点是整个项目的关键：

```
创建文章 → 只有 topic（主题）
  ↓
标题生成 → 填入 titleOptions（标题方案列表）
  ↓
用户选标题 → 填入 mainTitle + subTitle
  ↓
大纲生成 → 填入 outline（JSON 结构）
  ↓
用户编辑大纲 → 更新 outline
  ↓
正文生成 → 填入 content（纯文本）、fullContent（含配图）
  ↓
完成 → status = COMPLETED
```

## 状态机设计

文章有**两层状态**，不要混淆：

### 第一层：status（整体状态）

```java
public enum ArticleStatusEnum {
    PENDING,      // 刚创建，等待处理
    PROCESSING,   // 正在生成中
    COMPLETED,    // 生成完成
    FAILED        // 生成失败
}
```

### 第二层：phase（创作阶段）

```java
public enum ArticlePhaseEnum {
    PENDING,            // 初始状态
    TITLE_GENERATING,   // 标题生成中
    TITLE_SELECTING,    // 等待用户选择标题
    OUTLINE_GENERATING, // 大纲生成中
    OUTLINE_EDITING,    // 等待用户编辑大纲
    CONTENT_GENERATING  // 正文+配图生成中
}
```

**阶段必须线性流转**，不能跳跃：

```
PENDING → TITLE_GENERATING → TITLE_SELECTING → OUTLINE_GENERATING → OUTLINE_EDITING → CONTENT_GENERATING
```

建议在 `ArticlePhaseEnum` 中实现 `canTransitionTo(ArticlePhaseEnum target)` 方法，校验流转合法性。

## API 设计

| 方法 | 路径 | 说明 | 阶段 |
|------|------|------|------|
| POST | `/article/create` | 创建文章（传 topic + style + enabledImageMethods） | → PENDING |
| POST | `/article/confirm-title` | 用户确认标题（传 taskId + mainTitle + subTitle） | TITLE_SELECTING → |
| POST | `/article/confirm-outline` | 用户确认大纲（传 taskId + 编辑后的大纲） | OUTLINE_EDITING → |
| POST | `/article/ai-modify-outline` | AI 优化大纲（传 taskId + 修改要求） | OUTLINE_EDITING 停留 |
| GET | `/article/progress/{taskId}` | SSE 进度推送（模块4实现） | — |
| GET | `/article/{taskId}` | 查询文章详情 | — |
| POST | `/article/list` | 分页查询当前用户的文章 | — |
| POST | `/article/delete` | 删除文章 | — |
| GET | `/article/execution-logs/{taskId}` | 查询 Agent 执行日志 | — |

## 数据模型关键字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `taskId` | VARCHAR(128) | UUID，前端轮询/SSE 的唯一标识，与 id 解耦 |
| `titleOptions` | JSON | 标题方案列表，格式：`[{"mainTitle":"...","subTitle":"..."}, ...]` |
| `outline` | JSON | 大纲结构，格式：`[{"sectionTitle":"...","keyPoints":["..."]}, ...]` |
| `content` | LONGTEXT | 纯文本正文（不含配图） |
| `fullContent` | LONGTEXT | 图文正文（配图已嵌入的 Markdown） |
| `images` | JSON | 配图结果，格式：`[{"url":"...","position":1,"source":"PEXELS"}, ...]` |
| `enabledImageMethods` | JSON | 用户选择的配图方式，格式：`["PEXELS","MERMAID"]` |
| `style` | VARCHAR(20) | 文章风格枚举 |

## ArticleState — 跨 Agent 的共享状态

这是整个智能体流程的核心数据载体，后续模块5会大量使用：

```java
public class ArticleState {
    private String taskId;
    private String topic;
    private String style;
    private String userDescription;
    private List<String> enabledImageMethods;

    // Phase 1 输出
    private TitleResult titleResult;        // 包含 List<TitleOption>

    // Phase 2 输出
    private OutlineResult outlineResult;    // 包含 List<OutlineSection>

    // Phase 3 中间产物
    private String content;                          // 正文（含占位符）
    private List<ImageRequirement> imageRequirements; // 配图需求
    private List<ImageResult> images;                 // 配图结果
    private String fullContent;                       // 最终图文正文
}
```

> **设计意图**：ArticleState 是内存中的可变对象，在 Agent 之间传递。Article 实体是数据库中的持久化对象。ArticleAsyncService 负责在两者之间转换——Agent 执行前从 Article 构建 State，Agent 执行后将 State 写回 Article。

## 创建文章的流程

```
1. 前端 POST /article/create {topic, style, enabledImageMethods}
2. 后端：
   a. 校验用户配额（quota > 0）
   b. 生成 taskId (UUID)
   c. 创建 Article 记录，status=PENDING, phase=PENDING
   d. 扣减 quota
   e. 返回 taskId
   f. 异步触发 Phase 1（标题生成）→ 模块5实现
```

> **配额校验**建议独立为 `QuotaService`，检查 `user.quota > 0` 且用户非 VIP（VIP 不限配额）。

## 本模块验证标准

- [ ] 创建文章返回 taskId，数据库记录 status=PENDING
- [ ] 确认标题接口能更新 mainTitle/subTitle，phase 流转正确
- [ ] 确认大纲接口能更新 outline，phase 流转正确
- [ ] 查询文章详情返回完整数据
- [ ] 分页列表只返回当前用户的文章
- [ ] 逻辑删除生效
- [ ] 阶段流转校验：不能从 PENDING 直接跳到 OUTLINE_EDITING