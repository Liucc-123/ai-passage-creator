# 配图模块设计文档

## 📌 模块概述

配图模块负责为文章生成和管理配图，采用策略模式实现多种配图方式，支持自动降级机制。本模块集成多个第三方服务，包括 Pexels、Mermaid、Iconify、表情包搜索、AI 生图等。

## 🎯 学习目标

完成本模块后，你将能够：
1. 理解策略模式的设计和应用
2. 掌握多种第三方服务的集成
3. 实现自动降级机制
4. 理解图片上传和管理

## 🏗️ 架构设计

### 配图策略架构

```
┌─────────────────────────────────────────────────────────────┐
│                   ImageServiceStrategy                       │
│                      (策略选择器)                            │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  Pexels      │    │   Mermaid    │    │  Iconify     │
│  Service     │    │   Service    │    │  Service     │
└──────────────┘    └──────────────┘    └──────────────┘
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  表情包      │    │ Nano Banana  │    │ SVG Diagram  │
│  Service     │    │   Service    │    │   Service    │
└──────────────┘    └──────────────┘    └──────────────┘
        │                     │                     │
        └─────────────────────┼─────────────────────┘
                              │
                              ▼
                    ┌──────────────┐
                    │  Picsum      │
                    │ (降级方案)   │
                    └──────────────┘
```

### 配图方式说明

| 方式 | 说明 | 数据来源 | 权限 |
|------|------|---------|------|
| Pexels | 高质量图库检索 | 关键词检索 | 全部用户 |
| Mermaid | 流程图/架构图生成 | AI Prompt 生成 | 全部用户 |
| Iconify | 图标库检索 | 关键词检索 | 全部用户 |
| 表情包 | Bing 图片搜索 | 关键词检索 | 全部用户 |
| Nano Banana | Gemini AI 生图 | AI Prompt 生成 | VIP |
| SVG Diagram | AI 概念示意图 | AI Prompt 生成 | VIP |
| Picsum | 随机图片 | 降级方案 | 自动触发 |

## 🔧 后端实现

### 1. 枚举类设计

**路径：** `src/main/java/com/yupi/template/model/enums/ImageMethodEnum.java`

```java
public enum ImageMethodEnum {
    PEXELS("PEXELS", "Pexels图库", false),
    MERMAID("MERMAID", "Mermaid流程图", false),
    ICONIFY("ICONIFY", "Iconify图标", false),
    EMOJI_PACK("EMOJI_PACK", "表情包", false),
    NANO_BANANA("NANO_BANANA", "AI生图", true),
    SVG_DIAGRAM("SVG_DIAGRAM", "SVG示意图", true),
    PICSUM("PICSUM", "随机图片", false);

    private final String value;
    private final String text;
    private final boolean vipOnly;

    // 构造函数、getter 方法
}
```

### 2. DTO 设计

**路径：** `src/main/java/com/yupi/template/model/dto/image/`

#### 2.1 ImageRequest

```java
@Data
public class ImageRequest {
    private String keyword;          // 关键词
    private String prompt;           // AI Prompt
    private String imageMethod;      // 配图方式
    private Integer position;        // 图片位置
    private String description;      // 图片描述
}
```

#### 2.2 ImageData

```java
@Data
public class ImageData {
    private String url;              // 图片URL
    private Integer position;        // 图片位置
    private String imageMethod;      // 配图方式
    private String description;      // 图片描述
    private String altText;          // 替代文本
}
```

### 3. 策略接口设计

**路径：** `src/main/java/com/yupi/template/service/ImageServiceStrategy.java`

```java
public interface ImageServiceStrategy {

    /**
     * 生成配图
     * @param request 配图请求
     * @return 配图数据
     */
    ImageData generateImage(ImageRequest request);

    /**
     * 获取策略名称
     */
    String getStrategyName();

    /**
     * 是否需要VIP权限
     */
    boolean isVipOnly();
}
```

### 4. 策略选择器实现

**路径：** `src/main/java/com/yupi/template/service/ImageServiceStrategy.java`

```java
@Service
public class ImageServiceStrategy {

    @Resource
    private Map<String, ImageServiceStrategy> strategyMap;

    /**
     * 根据配图方式选择策略
     */
    public ImageServiceStrategy getStrategy(String imageMethod) {
        return strategyMap.get(imageMethod);
    }

    /**
     * 生成配图（带降级）
     */
    public ImageData generateImageWithFallback(ImageRequest request) {
        try {
            // 1. 尝试使用指定方式生成
            ImageServiceStrategy strategy = getStrategy(request.getImageMethod());
            return strategy.generateImage(request);
        } catch (Exception e) {
            log.error("配图生成失败，使用降级方案: {}", e.getMessage());
            // 2. 降级到 Picsum
            return generatePicsumImage(request);
        }
    }
}
```

### 5. 各策略实现

#### 5.1 Pexels 策略

**路径：** `src/main/java/com/yupi/template/service/PexelsService.java`

```java
@Service("PEXELS")
public class PexelsService implements ImageServiceStrategy {

    @Value("${pexels.api-key}")
    private String apiKey;

    @Override
    public ImageData generateImage(ImageRequest request) {
        // 1. 调用 Pexels API 搜索图片
        // 2. 选择合适图片
        // 3. 上传到 COS
        // 4. 返回图片信息
    }

    @Override
    public String getStrategyName() {
        return ImageMethodEnum.PEXELS.getValue();
    }

    @Override
    public boolean isVipOnly() {
        return false;
    }
}
```

#### 5.2 Mermaid 策略

**路径：** `src/main/java/com/yupi/template/service/MermaidService.java`

```java
@Service("MERMAID")
public class MermaidService implements ImageServiceStrategy {

    @Override
    public ImageData generateImage(ImageRequest request) {
        // 1. 解析 Prompt 生成 Mermaid 代码
        // 2. 渲染 Mermaid 图表
        // 3. 转换为图片
        // 4. 上传到 COS
        // 5. 返回图片信息
    }

    @Override
    public String getStrategyName() {
        return ImageMethodEnum.MERMAID.getValue();
    }

    @Override
    public boolean isVipOnly() {
        return false;
    }
}
```

#### 5.3 Iconify 策略

**路径：** `src/main/java/com/yupi/template/service/IconifyService.java`

```java
@Service("ICONIFY")
public class IconifyService implements ImageServiceStrategy {

    @Override
    public ImageData generateImage(ImageRequest request) {
        // 1. 调用 Iconify API 搜索图标
        // 2. 选择合适图标
        // 3. 转换为图片
        // 4. 上传到 COS
        // 5. 返回图片信息
    }

    @Override
    public String getStrategyName() {
        return ImageMethodEnum.ICONIFY.getValue();
    }

    @Override
    public boolean isVipOnly() {
        return false;
    }
}
```

#### 5.4 表情包策略

**路径：** `src/main/java/com/yupi/template/service/EmojiPackService.java`

```java
@Service("EMOJI_PACK")
public class EmojiPackService implements ImageServiceStrategy {

    @Override
    public ImageData generateImage(ImageRequest request) {
        // 1. 调用 Bing 搜索表情包
        // 2. 选择合适图片
        // 3. 上传到 COS
        // 4. 返回图片信息
    }

    @Override
    public String getStrategyName() {
        return ImageMethodEnum.EMOJI_PACK.getValue();
    }

    @Override
    public boolean isVipOnly() {
        return false;
    }
}
```

#### 5.5 Nano Banana 策略（VIP）

**路径：** `src/main/java/com/yupi/template/service/NanoBananaService.java`

```java
@Service("NANO_BANANA")
public class NanoBananaService implements ImageServiceStrategy {

    @Value("${nano.banana.api-key}")
    private String apiKey;

    @Override
    public ImageData generateImage(ImageRequest request) {
        // 1. 调用 Gemini AI 生图
        // 2. 获取生成图片
        // 3. 上传到 COS
        // 4. 返回图片信息
    }

    @Override
    public String getStrategyName() {
        return ImageMethodEnum.NANO_BANANA.getValue();
    }

    @Override
    public boolean isVipOnly() {
        return true;
    }
}
```

#### 5.6 SVG Diagram 策略（VIP）

**路径：** `src/main/java/com/yupi/template/service/SvgDiagramService.java`

```java
@Service("SVG_DIAGRAM")
public class SvgDiagramService implements ImageServiceStrategy {

    @Override
    public ImageData generateImage(ImageRequest request) {
        // 1. 调用 AI 生成 SVG 代码
        // 2. 渲染 SVG
        // 3. 转换为图片
        // 4. 上传到 COS
        // 5. 返回图片信息
    }

    @Override
    public String getStrategyName() {
        return ImageMethodEnum.SVG_DIAGRAM.getValue();
    }

    @Override
    public boolean isVipOnly() {
        return true;
    }
}
```

### 6. COS 服务

**路径：** `src/main/java/com/yupi/template/service/CosService.java`

```java
@Service
public class CosService {

    @Value("${tencent.cos.secret-id}")
    private String secretId;

    @Value("${tencent.cos.secret-key}")
    private String secretKey;

    /**
     * 上传图片到 COS
     * @param imageUrl 图片URL
     * @return COS URL
     */
    public String uploadImage(String imageUrl) {
        // 1. 下载图片
        // 2. 上传到 COS
        // 3. 返回 COS URL
    }

    /**
     * 上传文件到 COS
     * @param file 文件
     * @return COS URL
     */
    public String uploadFile(File file) {
        // 1. 生成文件路径
        // 2. 上传到 COS
        // 3. 返回 COS URL
    }
}
```

## 🎨 前端实现

### 1. 配图组件

**路径：** `frontend/src/pages/article/components/ImageGallery.vue`

```vue
<template>
  <div class="image-gallery">
    <a-image-preview-group>
      <a-image
        v-for="image in images"
        :key="image.position"
        :src="image.url"
        :alt="image.altText"
      />
    </a-image-preview-group>
  </div>
</template>

<script setup lang="ts">
import { defineProps } from 'vue';

const props = defineProps<{
  images: API.ImageData[];
}>();
</script>
```

### 2. 配图选择器

**路径：** `frontend/src/pages/article/components/ImageMethodSelector.vue`

```vue
<template>
  <a-select
    v-model:value="selectedMethod"
    :options="methodOptions"
    placeholder="选择配图方式"
  />
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useLoginUserStore } from '@/stores/loginUser';

const loginUserStore = useLoginUserStore();
const selectedMethod = ref<string>('PEXELS');

const methodOptions = computed(() => {
  const isVip = loginUserStore.loginUser?.vipTime;
  return [
    { label: 'Pexels图库', value: 'PEXELS' },
    { label: 'Mermaid流程图', value: 'MERMAID' },
    { label: 'Iconify图标', value: 'ICONIFY' },
    { label: '表情包', value: 'EMOJI_PACK' },
    { label: 'AI生图', value: 'NANO_BANANA', disabled: !isVip },
    { label: 'SVG示意图', value: 'SVG_DIAGRAM', disabled: !isVip },
  ];
});
</script>
```

## 📝 学习任务清单

### 后端任务
- [ ] 实现 ImageMethodEnum 枚举
- [ ] 实现 ImageRequest DTO
- [ ] 实现 ImageData DTO
- [ ] 实现策略接口
- [ ] 实现策略选择器
- [ ] 实现 Pexels 策略
- [ ] 实现 Mermaid 策略
- [ ] 实现 Iconify 策略
- [ ] 实现表情包策略
- [ ] 实现 Nano Banana 策略
- [ ] 实现 SVG Diagram 策略
- [ ] 实现 COS 服务

### 前端任务
- [ ] 实现配图展示组件
- [ ] 实现配图方式选择器
- [ ] 实现配图预览功能

## 💡 实现提示

1. **策略模式**：使用 Spring 自动注入策略实现类
2. **降级机制**：主策略失败时自动降级到 Picsum
3. **权限控制**：VIP 功能需要校验用户权限
4. **图片上传**：统一使用 COS 管理图片
5. **错误处理**：每个策略都要有完善的错误处理
6. **性能优化**：配图生成使用线程池并行处理

## 🔍 测试建议

1. 测试每种配图方式
2. 测试降级机制
3. 测试 VIP 权限控制
4. 测试图片上传
5. 测试并发配图生成
6. 测试错误处理

## 📚 参考资源

- 策略模式设计模式
- Pexels API 文档
- Mermaid 官方文档
- Iconify API 文档
- 腾讯云 COS SDK 文档
- Gemini AI API 文档
