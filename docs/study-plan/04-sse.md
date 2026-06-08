# 模块4：SSE 实时通信

> 目标：SSE 连接建立，能向前端推送消息，前端 EventSource 能接收

## 为什么用 SSE 而不是 WebSocket？

- 文章生成是**单向推送**场景（服务端 → 客户端），不需要双向通信
- SSE 基于 HTTP，天然兼容代理/负载均衡，不需要额外的连接升级
- 浏览器原生 `EventSource` API，前端实现简单
- 断线自动重连，不需要自己实现心跳

## 消息类型设计

定义枚举 `SseMessageTypeEnum`，每条消息有 `type` 和 `data`：

```java
public enum SseMessageTypeEnum {
    // 阶段1：标题
    AGENT1_COMPLETE,      // 标题方案生成完成
    TITLES_GENERATED,     // 标题列表已准备好（供前端展示选择）

    // 阶段2：大纲
    AGENT2_STREAMING,     // 大纲流式输出中（data 是增量片段）
    AGENT2_COMPLETE,      // 大纲 Agent 完成
    OUTLINE_GENERATED,    // 大纲已准备好（供前端进入编辑）

    // 阶段3：正文
    AGENT3_STREAMING,     // 正文流式输出中（data 是增量片段）
    AGENT3_COMPLETE,      // 正文 Agent 完成
    AGENT4_COMPLETE,      // 配图分析完成
    IMAGE_COMPLETE,       // 单张配图生成完成（data 含进度信息）
    AGENT5_COMPLETE,      // 所有配图生成完成
    MERGE_COMPLETE,       // 图文合成完成

    // 结束
    ALL_COMPLETE,         // 全部完成
    ERROR;                // 错误

    // 流式消息需要前缀来区分增量片段
    public String getStreamingPrefix() {
        return this.name() + ":";
    }
}
```

> **流式消息的特殊处理**：`AGENT2_STREAMING` 和 `AGENT3_STREAMING` 的 data 不是完整内容，而是增量片段。前端需要拼接。消息格式为 `AGENT2_STREAMING:这是增量内容\n\n`，用冒号分隔类型和内容。

## SseEmitterManager 设计

核心数据结构：`ConcurrentHashMap<String, SseEmitter>`

```
taskId → SseEmitter
```

### 关键方法

| 方法 | 说明 |
|------|------|
| `createEmitter(taskId)` | 创建 SseEmitter（超时30分钟），注册 onTimeout/onCompletion/onError 回调自动清理 |
| `send(taskId, type, data)` | 发送 SSE 事件，格式：`event: message\ndata: {"type":"...","data":"..."}\n\n` |
| `complete(taskId)` | 正常完成，关闭连接并从 Map 移除 |
| `remove(taskId)` | 异常/超时时从 Map 移除 |

### 超时与清理

```java
// 创建时设置30分钟超时（文章生成可能较慢）
SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

// 注册回调，防止内存泄漏
emitter.onTimeout(() -> emitterMap.remove(taskId));
emitter.onCompletion(() -> emitterMap.remove(taskId));
emitter.onError(e -> emitterMap.remove(taskId));
```

## Controller 端点

```java
@GetMapping("/article/progress/{taskId}")
public SseEmitter getProgress(@PathVariable String taskId) {
    SseEmitter emitter = sseEmitterManager.createEmitter(taskId);
    return emitter;
}
```

> 前端调用此接口后，连接保持打开，服务端通过 `sseEmitterManager.send()` 推送消息。

## 前端对接

```javascript
// 建立 SSE 连接
const eventSource = new EventSource(`/api/article/progress/${taskId}`);

eventSource.onmessage = (event) => {
    const message = JSON.parse(event.data);
    // message.type → 消息类型
    // message.data → 消息内容

    switch (message.type) {
        case 'AGENT1_COMPLETE':
            // 标题生成了，展示选择
            break;
        case 'AGENT2_STREAMING':
            // 大纲增量内容，追加显示
            break;
        case 'ALL_COMPLETE':
            // 全部完成，关闭连接
            eventSource.close();
            break;
        case 'ERROR':
            // 出错，关闭连接
            eventSource.close();
            break;
    }
};

eventSource.onerror = () => {
    eventSource.close();
};
```

## 消息发送的时机

SSE 消息由 `ArticleAsyncService`（模块5）在 Agent 执行的各个节点发送。本模块只负责**传输管道**，不关心何时发送什么消息。

```
ArticleAsyncService（模块5）
  │
  │  调用 sseEmitterManager.send(taskId, type, data)
  │
  ▼
SseEmitterManager（本模块）
  │
  │  通过 SseEmitter 发送到客户端
  │
  ▼
前端 EventSource.onmessage
```

## 本模块验证标准

- [ ] GET `/article/progress/test-task` 返回 `text/event-stream`，连接保持
- [ ] 手动调用 `sseEmitterManager.send()` 后，前端能收到消息
- [ ] 超时/关闭连接后，SseEmitter 从 Map 中移除，无内存泄漏
- [ ] 流式消息（带前缀）前端能正确拼接增量内容
- [ ] 多个 taskId 同时存在时互不干扰