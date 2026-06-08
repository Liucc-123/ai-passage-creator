# 模块6：配图服务模块

> 目标：Pexels 配图跑通 + 策略模式框架，至少实现 2 种配图方式

## 策略模式设计

配图是扩展性最强的模块。项目用策略模式实现 6 种配图方式 + 1 种降级方案，新增配图方式只需实现接口 + 加枚举值。

### 核心接口

```java
public interface ImageSearchService {
    ImageData searchImage(String keywords);       // 搜索/生成图片
    ImageData getImage(ImageRequest request);      // 获取图片（含上传COS）
    ImageData getImageData(ImageRequest request);  // 获取图片数据（不上传）
    ImageMethodEnum getMethod();                   // 返回对应的枚举值
    ImageData getFallbackImage(int position);      // 降级图片
    boolean isAvailable();                         // 当前配置是否可用
}
```

### 策略选择器

```java
@Service
public class ImageServiceStrategy {
    // Spring 自动注入所有 ImageSearchService 实现
    private final Map<ImageMethodEnum, ImageSearchService> serviceMap;

    public ImageServiceStrategy(List<ImageSearchService> services) {
        this.serviceMap = services.stream()
            .collect(Collectors.toMap(ImageSearchService::getMethod, s -> s));
    }

    public ImageData getImageAndUpload(ImageRequest request) {
        ImageMethodEnum method = request.getImageMethod();
        ImageSearchService service = serviceMap.get(method);

        // 1. 尝试指定方式
        if (service != null && service.isAvailable()) {
            try {
                return service.getImage(request);
            } catch (Exception e) {
                log.warn("配图方式 {} 失败，尝试降级", method);
            }
        }

        // 2. 降级到 PICSUM 随机图片
        ImageSearchService fallback = serviceMap.get(ImageMethodEnum.PICSUM);
        return fallback.getImage(request);
    }
}
```

> **关键设计**：每个 `ImageSearchService` 实现用 `@Service("PEXELS")` 等方式注册，`getMethod()` 返回对应枚举。策略选择器自动收集所有实现，无需手动注册。

## 六种配图方式详解

### 1. Pexels（图库搜索）— 全部用户可用

```
用户关键词 → Pexels API 搜索 → 随机选一张 → 下载图片 → 上传 COS → 返回 URL
```

- **配置**：`pexels.api-key`（必需）
- **API**：`https://api.pexels.com/v1/search?query={keywords}&per_page=10`
- **注意**：需要下载图片二进制再上传到 COS，不能直接用 Pexels URL（防盗链）

### 2. Mermaid（流程图生成）— 全部用户可用

```
LLM 生成 Mermaid 代码 → mmdc CLI 渲染为 SVG/PNG → 上传 COS → 返回 URL
```

- **配置**：`mermaid.cli-command`（默认 `mmdc`，需本地安装 `@mermaid-js/mermaid-cli`）
- **流程**：先用 LLM 根据内容生成 Mermaid 语法的图表代码，再调用 CLI 渲染成图片
- **注意**：mmdc 是 Node.js CLI 工具，服务器需要安装。渲染超时设 30 秒

### 3. Iconify（图标检索）— 全部用户可用

```
关键词 → Iconify API 搜索图标 → 下载 SVG → 上传 COS → 返回 URL
```

- **配置**：`iconify.api-url`（默认 `https://api.iconify.design`）
- **API**：`https://api.iconify.design/search?query={keywords}&limit=10`
- **注意**：图标是 SVG 格式，体积小，适合作为点缀

### 4. 表情包（Emoji Pack）— 全部用户可用

```
关键词 + 固定后缀("熊猫头表情包") → Bing 图片搜索 → 抓取图片URL → 下载 → 上传COS
```

- **配置**：`emoji-pack.search-url`、`emoji-pack.suffix`
- **注意**：Bing 图片搜索没有官方 API，需要解析 HTML 页面抓取图片 URL，稳定性较差

### 5. Nano Banana（Gemini AI 生图）— VIP 专属

```
图片描述 prompt → Google Gemini API → 生成图片 → 上传 COS → 返回 URL
```

- **配置**：`nano-banana.api-key`（需要 Google AI Studio API Key）
- **模型**：`gemini-2.5-flash-image`（快速）或 `gemini-3-pro-image-preview`（高质量）
- **注意**：VIP 专属功能，需在 Controller 层校验用户权限

### 6. SVG Diagram（AI 概念示意图）— VIP 专属

```
图片描述 prompt → LLM 生成 SVG 代码 → 保存为 .svg 文件 → 上传 COS → 返回 URL
```

- **配置**：`svg-diagram.default-width`、`svg-diagram.default-height`
- **流程**：用 DashScope LLM 生成 SVG 代码，保存为文件后上传
- **注意**：LLM 生成的 SVG 可能不合法，需要 try-catch 兜底

### 7. Picsum（降级方案）— 自动触发

```
position → https://picsum.photos/800/600?random={position} → 下载 → 上传COS → 返回URL
```

- **无需配置**，当其他所有方式失败时自动降级
- 返回随机图片，与文章内容无关，但保证文章生成不中断

## ImageMethodEnum 设计

```java
public enum ImageMethodEnum {
    PEXELS("PEXELS", "Pexels 图库", false, false),
    NANO_BANANA("NANO_BANANA", "AI 生图", true, false),
    MERMAID("MERMAID", "流程图", true, false),
    ICONIFY("ICONIFY", "图标库", false, false),
    EMOJI_PACK("EMOJI_PACK", "表情包", false, false),
    SVG_DIAGRAM("SVG_DIAGRAM", "示意图", true, false),
    PICSUM("PICSUM", "随机图片", false, true);

    private final String value;
    private final String label;
    private final boolean aiGenerated;   // 是否 AI 生成
    private final boolean fallback;       // 是否降级方案
}
```

- `aiGenerated=true` 的方式需要 LLM 生成 prompt（Nano Banana、Mermaid、SVG Diagram）
- `fallback=true` 的是 Picsum，仅作为降级

## COS 上传服务

所有配图最终都要上传到腾讯云 COS，统一由 `CosService` 处理：

```java
@Service
public class CosService {
    public String upload(byte[] data, String folder, String filename);
    public String uploadFromUrl(String imageUrl, String folder);
    public String uploadDataUrl(String dataUrl, String folder);
}
```

> **设计意图**：不直接使用第三方图片 URL，因为：1) 防盗链 2) 第三方链接可能失效 3) 加载速度不可控。统一上传 COS 保证稳定性。

## 添加新配图方式的步骤

1. 在 `ImageMethodEnum` 添加枚举值
2. 实现 `ImageSearchService` 接口，加 `@Service("YOUR_METHOD")`
3. 添加对应配置类（如需要 API Key）
4. 策略选择器自动注册，无需修改

## 实现优先级建议

| 优先级 | 方式 | 原因 |
|--------|------|------|
| P0 | Pexels | 最简单，API 调用 + 图片下载 + COS 上传，跑通整个链路 |
| P1 | Picsum | 降级方案，无 API Key 依赖，保证流程不中断 |
| P2 | Mermaid | 需要 mmdc CLI，但效果独特 |
| P3 | Iconify | API 简单，但图标用途有限 |
| P4 | Nano Banana | VIP 功能，需 Google API Key |
| P5 | Emoji Pack | 依赖 HTML 抓取，不稳定 |
| P6 | SVG Diagram | VIP 功能，LLM 生成 SVG 可能不合法 |

## 本模块验证标准

- [ ] Pexels 搜索关键词能返回图片 URL
- [ ] 图片下载后上传 COS 成功，返回可访问的 URL
- [ ] 策略模式框架跑通：指定方式 → 调用对应 Service
- [ ] 指定方式失败时自动降级到 Picsum
- [ ] `isAvailable()` 在 API Key 未配置时返回 false
- [ ] VIP 专属方式对非 VIP 用户不可用
- [ ] 至少实现 2 种配图方式（Pexels + Picsum）