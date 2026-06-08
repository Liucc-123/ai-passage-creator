# 智能体模块设计文档

## 📌 模块概述

智能体模块是系统的核心创新点，采用多智能体协作架构，通过 5 个专业智能体分工协作完成文章创作。本模块使用 Spring AI Alibaba 的 StateGraph 实现智能体编排，支持流式输出和人机协作。

## 🎯 学习目标

完成本模块后，你将能够：
1. 理解多智能体协作的架构设计
2. 掌握 StateGraph 状态图的使用
3. 实现流式输出（SSE）
4. 理解智能体间的数据流转

## 🏗️ 架构设计

### 智能体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    ArticleAgentOrchestrator                  │
│                    (智能体编排器)                             │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   阶段1      │    │   阶段2      │    │   阶段3      │
│ 标题生成     │    │ 大纲生成     │    │ 正文+配图    │
└──────────────┘    └──────────────┘    └──────────────┘
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│TitleGenerator│    │OutlineGenerator│   │ContentGenerator│
│   Agent      │    │   Agent      │    │   Agent      │
└──────────────┘    └──────────────┘    └──────────────┘
                                              │
                        ┌─────────────────────┼─────────────────────┐
                        │                     │                     │
                        ▼                     ▼                     ▼
                ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
                │ImageAnalyzer │    │ParallelImage │    │ContentMerger │
                │   Agent      │    │  Generator   │    │   Agent      │
                └──────────────┘    └──────────────┘    └──────────────┘
```

### 创作流程

```
阶段1：标题生成
  输入：选题、风格
  输出：3-5个标题方案
  用户操作：选择标题

阶段2：大纲生成
  输入：标题、用户描述、风格
  输出：文章大纲（流式）
  用户操作：编辑/优化大纲

阶段3：正文生成+配图
  输入：标题、大纲、风格
  输出：正文（流式）、配图列表
  最终输出：完整图文
```

## 🔧 后端实现

### 1. 目录结构

```
src/main/java/com/yupi/template/agent/
├── ArticleAgentOrchestrator.java    # 智能体编排器
├── agents/                          # 各智能体实现
│   ├── TitleGeneratorAgent.java
│   ├── OutlineGeneratorAgent.java
│   ├── ContentGeneratorAgent.java
│   ├── ImageAnalyzerAgent.java
│   └── ContentMergerAgent.java
├── parallel/                        # 并行处理
│   └── ParallelImageGenerator.java
├── config/                          # 配置
│   └── AgentConfig.java
├── context/                         # 上下文
│   └── StreamHandlerContext.java
└── tools/                           # 工具
    └── ImageGenerationTool.java
```

### 2. 核心类设计

#### 2.1 智能体编排器

**路径：** `src/main/java/com/yupi/template/agent/ArticleAgentOrchestrator.java`

**职责：**
- 编排多个智能体的执行流程
- 管理智能体间的数据流转
- 控制创作阶段的转换

**核心方法：**

```java
@Service
public class ArticleAgentOrchestrator {

    // 阶段1：生成标题方案
    public void executePhase1_GenerateTitles(
        ArticleState state, 
        Consumer<String> streamHandler
    );

    // 阶段2：生成大纲
    public void executePhase2_GenerateOutline(
        ArticleState state, 
        Consumer<String> streamHandler
    );

    // 阶段3：生成正文+配图
    public void executePhase3_GenerateContent(
        ArticleState state, 
        Consumer<String> streamHandler
    );
}
```

**实现要点：**
- 使用 StateGraph 构建状态图
- 每个阶段独立构建图
- 使用 Consumer 实现流式输出
- 异常处理和日志记录

#### 2.2 标题生成智能体

**路径：** `src/main/java/com/yupi/template/agent/agents/TitleGeneratorAgent.java`

**职责：**
- 根据选题和风格生成标题方案
- 生成 3-5 个标题供用户选择

**核心方法：**

```java
@Component
public class TitleGeneratorAgent {

    public List<ArticleState.TitleOption> generateTitles(
        String topic, 
        String style
    ) {
        // 1. 构建 Prompt
        // 2. 调用 AI 模型
        // 3. 解析结果
        // 4. 返回标题列表
    }
}
```

**实现要点：**
- 使用 Prompt 模板
- 调用通义千问 API
- 解析 JSON 格式结果
- 错误处理和重试

#### 2.3 大纲生成智能体

**路径：** `src/main/java/com/yupi/template/agent/agents/OutlineGeneratorAgent.java`

**职责：**
- 根据标题生成文章大纲
- 支持流式输出

**核心方法：**

```java
@Component
public class OutlineGeneratorAgent {

    public ArticleState.OutlineResult generateOutline(
        String mainTitle,
        String subTitle,
        String userDescription,
        String style
    ) {
        // 1. 构建 Prompt
        // 2. 调用 AI 模型（流式）
        // 3. 实时推送大纲内容
        // 4. 解析并返回大纲结构
    }
}
```

**实现要点：**
- 使用流式 API
- 通过 ThreadLocal 传递 streamHandler
- 实时推送 SSE 消息
- 解析 Markdown 格式大纲

#### 2.4 正文生成智能体

**路径：** `src/main/java/com/yupi/template/agent/agents/ContentGeneratorAgent.java`

**职责：**
- 根据大纲生成正文
- 支持流式输出
- 生成配图占位符

**核心方法：**

```java
@Component
public class ContentGeneratorAgent {

    public String generateContent(
        String mainTitle,
        String subTitle,
        ArticleState.OutlineResult outline,
        String style
    ) {
        // 1. 构建 Prompt
        // 2. 调用 AI 模型（流式）
        // 3. 实时推送正文内容
        // 4. 生成配图占位符
        // 5. 返回完整正文
    }
}
```

**实现要点：**
- 按章节生成正文
- 插入配图占位符
- 流式输出控制
- 内容长度控制

#### 2.5 配图分析智能体

**路径：** `src/main/java/com/yupi/template/agent/agents/ImageAnalyzerAgent.java`

**职责：**
- 分析正文内容
- 生成配图需求列表

**核心方法：**

```java
@Component
public class ImageAnalyzerAgent {

    public List<ArticleState.ImageRequirement> analyzeImageRequirements(
        String content
    ) {
        // 1. 提取配图占位符
        // 2. 分析上下文
        // 3. 生成配图需求
        // 4. 返回需求列表
    }
}
```

**实现要点：**
- 正则提取占位符
- 上下文分析
- 需求优先级排序
- 配图方式建议

#### 2.6 并行配图生成器

**路径：** `src/main/java/com/yupi/template/agent/parallel/ParallelImageGenerator.java`

**职责：**
- 并行生成多张配图
- 管理配图任务队列
- 处理配图失败降级

**核心方法：**

```java
@Component
public class ParallelImageGenerator {

    public List<ArticleState.ImageResult> generateImages(
        List<ArticleState.ImageRequirement> requirements,
        List<String> enabledImageMethods
    ) {
        // 1. 创建线程池
        // 2. 并行执行配图任务
        // 3. 处理失败降级
        // 4. 返回配图结果
    }
}
```

**实现要点：**
- 使用线程池
- 异常处理和降级
- 实时推送进度
- 资源管理

#### 2.7 内容合成智能体

**路径：** `src/main/java/com/yupi/template/agent/agents/ContentMergerAgent.java`

**职责：**
- 合并正文和配图
- 生成完整图文

**核心方法：**

```java
@Component
public class ContentMergerAgent {

    public String mergeContent(
        String content,
        List<ArticleState.ImageResult> images
    ) {
        // 1. 替换配图占位符
        // 2. 生成封面图
        // 3. 返回完整图文
    }
}
```

**实现要点：**
- 占位符替换
- 图片位置计算
- Markdown 格式化

### 3. 流式输出实现

#### 3.1 流式上下文

**路径：** `src/main/java/com/yupi/template/agent/context/StreamHandlerContext.java`

```java
public class StreamHandlerContext {
    private static final ThreadLocal<Consumer<String>> CONTEXT = new ThreadLocal<>();

    public static void set(Consumer<String> streamHandler) {
        CONTEXT.set(streamHandler);
    }

    public static Consumer<String> get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
```

#### 3.2 SSE 消息类型

**路径：** `src/main/java/com/yupi/template/model/enums/SseMessageTypeEnum.java`

```java
public enum SseMessageTypeEnum {
    AGENT1_COMPLETE("AGENT1_COMPLETE", "标题方案生成完成"),
    AGENT2_STREAMING("AGENT2_STREAMING", "大纲流式输出中"),
    AGENT2_COMPLETE("AGENT2_COMPLETE", "大纲生成完成"),
    AGENT3_STREAMING("AGENT3_STREAMING", "正文流式输出中"),
    AGENT3_COMPLETE("AGENT3_COMPLETE", "正文生成完成"),
    AGENT4_COMPLETE("AGENT4_COMPLETE", "配图需求分析完成"),
    IMAGE_COMPLETE("IMAGE_COMPLETE", "单张配图生成完成"),
    AGENT5_COMPLETE("AGENT5_COMPLETE", "所有配图生成完成"),
    MERGE_COMPLETE("MERGE_COMPLETE", "图文合成完成"),
    ERROR("ERROR", "错误通知");

    private final String value;
    private final String text;
}
```

### 4. Service 层设计

**路径：** `src/main/java/com/yupi/template/service/ArticleAgentService.java`

**核心方法：**

```java
@Service
public class ArticleAgentService {

    // 创建文章并生成标题
    public Long createArticleAndGenerateTitles(
        ArticleCreateRequest request,
        SseEmitter emitter
    );

    // 确认标题并生成大纲
    public void confirmTitleAndGenerateOutline(
        ArticleConfirmTitleRequest request,
        SseEmitter emitter
    );

    // 确认大纲并生成正文
    public void confirmOutlineAndGenerateContent(
        ArticleConfirmOutlineRequest request,
        SseEmitter emitter
    );
}
```

### 5. Controller 层设计

**路径：** `src/main/java/com/yupi/template/controller/ArticleController.java`

**SSE 接口：**

```java
@GetMapping("/api/article/sse/{taskId}")
public SseEmitter handleSse(@PathVariable String taskId) {
    // 1. 创建 SseEmitter
    // 2. 注册到管理器
    // 3. 返回 Emitter
}
```

## 🎨 前端实现

### 1. SSE 工具类

**路径：** `frontend/src/utils/sse.ts`

```typescript
export const createSseConnection = (
  taskId: string,
  onMessage: (message: string) => void,
  onError: (error: Event) => void,
  onComplete: () => void
): EventSource => {
  const eventSource = new EventSource(
    `${BASE_URL}/api/article/sse/${taskId}`
  );

  eventSource.onmessage = (event) => {
    onMessage(event.data);
  };

  eventSource.onerror = (error) => {
    onError(error);
    eventSource.close();
    onComplete();
  };

  return eventSource;
};
```

### 2. 状态管理

**路径：** `frontend/src/stores/article.ts`

```typescript
import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useArticleStore = defineStore('article', () => {
  const currentArticle = ref<API.ArticleVO>();
  const sseMessages = ref<string[]>([]);

  function setCurrentArticle(article: API.ArticleVO) {
    currentArticle.value = article;
  }

  function addSseMessage(message: string) {
    sseMessages.value.push(message);
  }

  function clearSseMessages() {
    sseMessages.value = [];
  }

  return { 
    currentArticle, 
    sseMessages,
    setCurrentArticle,
    addSseMessage,
    clearSseMessages
  };
});
```

### 3. 页面组件

#### 3.1 标题选择阶段

**路径：** `frontend/src/pages/article/components/TitleSelectingStage.vue`

**功能：**
- 展示标题方案列表
- 支持选择标题
- 确认后进入下一阶段

#### 3.2 大纲编辑阶段

**路径：** `frontend/src/pages/article/components/OutlineEditingStage.vue`

**功能：**
- 展示大纲内容
- 支持编辑大纲
- 支持 AI 优化
- 确认后进入下一阶段

#### 3.3 内容生成阶段

**路径：** `frontend/src/pages/article/components/CreatingState.vue`

**功能：**
- 实时展示生成进度
- 流式展示正文内容
- 展示配图生成进度
- 完成后展示完整图文

## 📝 学习任务清单

### 后端任务
- [ ] 实现智能体编排器
- [ ] 实现标题生成智能体
- [ ] 实现大纲生成智能体
- [ ] 实现正文生成智能体
- [ ] 实现配图分析智能体
- [ ] 实现并行配图生成器
- [ ] 实现内容合成智能体
- [ ] 实现流式上下文
- [ ] 实现 SSE 消息类型枚举
- [ ] 实现 ArticleAgentService
- [ ] 实现 SSE 接口

### 前端任务
- [ ] 实现 SSE 工具类
- [ ] 实现文章状态管理
- [ ] 实现标题选择组件
- [ ] 实现大纲编辑组件
- [ ] 实现内容生成组件
- [ ] 实现进度展示组件

## 💡 实现提示

1. **状态管理**：使用 ArticleState 统一管理创作状态
2. **流式输出**：使用 ThreadLocal 传递 streamHandler
3. **错误处理**：每个智能体都要有完善的错误处理
4. **性能优化**：配图生成使用线程池并行处理
5. **资源管理**：SSE 连接要及时关闭，避免资源泄漏
6. **日志记录**：记录每个智能体的执行日志

## 🔍 测试建议

1. 测试标题生成功能
2. 测试大纲流式输出
3. 测试正文流式输出
4. 测试配图并行生成
5. 测试图文合成
6. 测试错误处理
7. 测试 SSE 连接稳定性

## 📚 参考资源

- Spring AI Alibaba 官方文档
- StateGraph 使用指南
- SSE 规范文档
- 通义千问 API 文档
- Pinia 官方文档
