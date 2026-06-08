# 模块5：智能体模块（核心）

> 目标：三阶段 Agent 编排跑通，LLM 调用成功，流式输出到前端

这是整个项目最核心也最复杂的模块。建议分两步走：
- **Day 5**：先跑通单个 Agent（标题生成），理解 LLM 调用 + SSE 推送
- **Day 6**：完成三阶段编排 + 流式输出 + 并行配图

## 整体架构

```
ArticleAsyncService（异步调度层）
  │
  ├─ orchestrator.enabled = true  → ArticleAgentOrchestrator（StateGraph 编排）
  └─ orchestrator.enabled = false → ArticleAgentService（传统顺序执行）

ArticleAgentOrchestrator（编排层）
  │
  ├─ executePhase1()  → TitleGeneratorAgent
  ├─ executePhase2()  → OutlineGeneratorAgent
  └─ executePhase3()  → StateGraph:
       START → ContentGenerator → ImageAnalyzer → ParallelImageGenerator → ContentMerger → END
```

> **建议**：先用传统顺序模式（`ArticleAgentService`）跑通全流程，理解每个 Agent 的职责后再切换到 StateGraph 编排模式。

## LLM 集成

项目使用 **Spring AI Alibaba + DashScope**（通义千问）：

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
```

### 两种调用模式

| 模式 | 用途 | 方法 |
|------|------|------|
| 同步调用 | 标题生成、配图分析 | `chatModel.call(Prompt)` → 返回完整结果 |
| 流式调用 | 大纲生成、正文生成 | `chatModel.stream(Prompt)` → 返回 `Flux<ChatResponse>` |

### 流式输出的关键

流式调用 LLM 后，每个 token 片段需要实时推送到前端：

```
LLM stream token → StreamHandlerContext (ThreadLocal) → SseEmitterManager → 前端 EventSource
```

**StreamHandlerContext** 的设计意图：StateGraph 的节点方法签名只接收 `OverAllState`，无法传入 SSE handler。用 ThreadLocal 绕过这个限制：

```java
public class StreamHandlerContext {
    private static final ThreadLocal<Consumer<String>> HANDLER = new ThreadLocal<>();

    public static void set(Consumer<String> handler) { HANDLER.set(handler); }
    public static Consumer<String> get() { return HANDLER.get(); }
    public static void clear() { HANDLER.remove(); }
}
```

在 `ArticleAsyncService` 中，异步线程启动前设置 handler：

```java
StreamHandlerContext.set(chunk -> {
    sseEmitterManager.send(taskId, AGENT2_STREAMING, chunk);
});
```

在 Agent 内部流式调用时读取：

```java
chatModel.stream(prompt).subscribe(chunk -> {
    Consumer<String> handler = StreamHandlerContext.get();
    if (handler != null) {
        handler.accept(chunk);
    }
});
```

## 五个 Agent 的职责

### Agent 1：TitleGeneratorAgent（标题生成）

- **输入**：topic（主题）+ style（风格）
- **输出**：3-5 个标题方案（mainTitle + subTitle）
- **LLM 模式**：同步调用
- **Prompt 要点**：要求 LLM 返回 JSON 格式的标题列表，指定字段名

```
你是一个爆款文章标题专家。请根据以下主题生成3-5个标题方案。
每个标题包含主标题(mainTitle)和副标题(subTitle)。
主题：{topic}
风格：{stylePrompt}
请返回JSON数组格式。
```

### Agent 2：OutlineGeneratorAgent（大纲生成）

- **输入**：mainTitle + subTitle + style
- **输出**：大纲 JSON（章节 + 要点）
- **LLM 模式**：流式调用
- **Prompt 要点**：要求返回结构化大纲，每个章节有 sectionTitle 和 keyPoints

### Agent 3：ContentGeneratorAgent（正文生成）

- **输入**：大纲 + style + 配图方式列表
- **输出**：Markdown 正文，配图位置用占位符标记
- **LLM 模式**：流式调用
- **Prompt 要点**：要求在正文中用 `[IMAGE_1]`、`[IMAGE_2]` 等占位符标记配图位置

> **为什么正文要插入占位符？** 正文生成时还不知道图片 URL，先用占位符标记位置，等 Agent 4 分析出配图需求、Agent 5 生成图片后，再由 ContentMerger 替换占位符。

### Agent 4：ImageAnalyzerAgent（配图需求分析）

- **输入**：正文（含占位符）
- **输出**：配图需求列表 `List<ImageRequirement>`
- **LLM 模式**：同步调用
- **Prompt 要点**：分析每个占位符位置需要什么样的图片，输出搜索关键词和图片来源类型

```java
public class ImageRequirement {
    private int position;          // 对应 [IMAGE_N] 中的 N
    private String keywords;       // 搜索关键词
    private String imageSource;    // PEXELS / NANO_BANANA / MERMAID 等
    private String description;    // 图片描述（用于 AI 生图的 prompt）
}
```

### Agent 5：ContentMergerAgent（图文合成）

- **输入**：正文（含占位符）+ 配图结果列表
- **输出**：完整 Markdown（占位符替换为 `![描述](URL)`）
- **LLM 模式**：**不需要 LLM**，纯字符串替换

```
遍历 images：
  在 content 中找到 [IMAGE_N]
  替换为 ![图片描述](imageUrl)
```

## ParallelImageGenerator（并行配图生成）

这是 Agent 5 之外的独立节点，负责并行获取/生成所有图片：

```
1. 将 imageRequirements 按 imageSource 分组
2. 每组内串行执行（避免同一 API 限流）
3. 组间并行执行（CompletableFuture.runAsync）
4. 每完成一张图，通过 SSE 推送 IMAGE_COMPLETE
5. 全部完成后推送 AGENT5_COMPLETE
```

> **为什么组内串行？** 同一个 API（如 Pexels）连续调用太快会触发限流。组间并行是因为不同 API 互不影响。

## ArticleAsyncService — 异步调度层

这是连接 Controller 和 Agent 的桥梁，核心职责：

1. **异步执行**：每个阶段在 `articleExecutor` 线程池上运行（`@Async`）
2. **状态流转**：执行前更新 phase/status，执行后写入数据库
3. **SSE 推送**：在 Agent 执行的关键节点推送消息
4. **错误处理**：捕获异常，更新 status=FAILED，推送 ERROR 消息

### 执行流程（以 Phase 1 为例）

```
1. Controller 调用 articleAsyncService.executePhase1(article)
2. 更新 article: status=PROCESSING, phase=TITLE_GENERATING
3. @Async 线程执行：
   a. 构建 ArticleState
   b. 设置 StreamHandlerContext
   c. 调用 orchestrator.executePhase1(state, streamHandler)
   d. 从 state 取出 titleResult，写入 article.titleOptions
   e. 更新 article: phase=TITLE_SELECTING
   f. 推送 SSE: AGENT1_COMPLETE + TITLES_GENERATED
4. 如果异常：
   a. 更新 article: status=FAILED, errorMessage=...
   b. 推送 SSE: ERROR
```

## StateGraph 编排（进阶）

如果使用 Spring AI Alibaba 的 StateGraph：

```java
StateGraph graph = new StateGraph(keyStrategyFactory)
    .addNode("content_generator", node_async(contentGeneratorAgent))
    .addNode("image_analyzer", node_async(imageAnalyzerAgent))
    .addNode("parallel_image_generator", node_async(parallelImageGenerator))
    .addNode("content_merger", node_async(contentMergerAgent))
    .addEdge(START, "content_generator")
    .addEdge("content_generator", "image_analyzer")
    .addEdge("image_analyzer", "parallel_image_generator")
    .addEdge("parallel_image_generator", "content_merger")
    .addEdge("content_merger", END);
```

State 的 key 定义：

| Key | 类型 | 说明 |
|-----|------|------|
| `KEY_TASK_ID` | String | 任务 ID |
| `KEY_TOPIC` | String | 主题 |
| `KEY_STYLE` | String | 风格 |
| `KEY_MAIN_TITLE` | String | 主标题 |
| `KEY_SUB_TITLE` | String | 副标题 |
| `KEY_OUTLINE` | String | 大纲 JSON |
| `KEY_CONTENT` | String | 正文（含占位符） |
| `KEY_CONTENT_WITH_PLACEHOLDERS` | String | 同上（别名） |
| `KEY_IMAGE_REQUIREMENTS` | List | 配图需求 |
| `KEY_IMAGES` | List | 配图结果 |
| `KEY_FULL_CONTENT` | String | 最终图文正文 |
| `KEY_ENABLED_IMAGE_METHODS` | List | 允许的配图方式 |

使用 `ReplaceStrategy`（每个节点输出覆盖 State 中的 key）。

## Prompt 设计要点

Prompt 是 Agent 效果的关键。建议：

1. **明确输出格式**：要求 LLM 返回 JSON，指定字段名和结构
2. **风格注入**：根据 `ArticleStyleEnum` 追加风格 prompt（科技/情感/教育/幽默）
3. **边界约束**：限制标题长度、大纲章节数、正文字数
4. **占位符规范**：正文中的配图占位符用 `[IMAGE_N]` 格式，N 从 1 递增
5. **错误兜底**：LLM 返回格式异常时，用正则或 try-catch 提取关键信息

## 本模块验证标准

- [ ] 单独调用 DashScope LLM 能返回结果（同步 + 流式）
- [ ] Phase 1：输入 topic，输出 3-5 个标题方案，SSE 推送 AGENT1_COMPLETE
- [ ] Phase 2：输入标题，流式输出大纲，SSE 推送 AGENT2_STREAMING 片段
- [ ] Phase 3：输入大纲，流式输出正文，SSE 推送 AGENT3_STREAMING 片段
- [ ] Agent 4 能从正文中提取配图需求
- [ ] ContentMerger 能将占位符替换为图片 Markdown
- [ ] 异常时 article 状态更新为 FAILED，SSE 推送 ERROR
- [ ] 文章生成完成后 fullContent 包含完整图文 Markdown
