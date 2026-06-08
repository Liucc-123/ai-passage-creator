# 支付与会员模块设计文档

## 📌 模块概述

支付与会员模块负责系统的商业化功能，包括 VIP 会员体系、Stripe 支付集成、配额管理等。本模块采用 Stripe Checkout 实现支付流程，支持永久会员购买。

## 🎯 学习目标

完成本模块后，你将能够：
1. 理解 Stripe 支付流程
2. 实现 VIP 会员体系
3. 掌握配额管理机制
4. 实现支付回调处理

## 🏗️ 架构设计

### 支付流程

```
┌──────────┐    1.点击购买    ┌──────────┐    2.创建会话    ┌──────────┐
│  用户    │ ──────────────> │  后端    │ ──────────────> │  Stripe  │
└──────────┘                 └──────────┘                 └──────────┘
      │                           │                           │
      │ 3.跳转到支付页             │ 4.支付完成                │
      │<──────────────────────────│<──────────────────────────│
      │                           │                           │
      │ 5.支付成功                 │ 6.处理Webhook             │
      │ ─────────────────────────>│ ─────────────────────────>│
      │                           │                           │
      │ 7.更新会员状态             │                           │
      │<──────────────────────────│                           │
      └──────────┘                 └──────────┘                 └──────────┘
```

## 🗄️ 数据库设计

### payment_record 表

```sql
CREATE TABLE payment_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    userId BIGINT NOT NULL COMMENT '用户ID',
    stripeSessionId VARCHAR(128) COMMENT 'Stripe Checkout Session ID',
    stripePaymentIntentId VARCHAR(128) COMMENT 'Stripe 支付意向ID',
    amount DECIMAL(10,2) NOT NULL COMMENT '金额（美元）',
    currency VARCHAR(8) DEFAULT 'usd' COMMENT '货币',
    status VARCHAR(32) NOT NULL COMMENT '状态：PENDING/SUCCEEDED/FAILED/REFUNDED',
    productType VARCHAR(32) NOT NULL COMMENT '产品类型：VIP_PERMANENT',
    description VARCHAR(256) COMMENT '描述',
    refundTime DATETIME NULL COMMENT '退款时间',
    refundReason VARCHAR(512) NULL COMMENT '退款原因',
    createTime DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updateTime DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_userId (userId),
    INDEX idx_stripeSessionId (stripeSessionId),
    INDEX idx_status (status),
    INDEX idx_createTime (createTime)
) COMMENT '支付记录表';
```

**字段说明：**
- `stripeSessionId`: Stripe 会话ID，用于关联支付流程
- `stripePaymentIntentId`: Stripe 支付意向ID，用于追踪支付
- `status`: 支付状态（PENDING/SUCCEEDED/FAILED/REFUNDED）
- `productType`: 产品类型（VIP_PERMANENT）
- `refundTime`: 退款时间
- `refundReason`: 退款原因

### user 表扩展字段

```sql
ALTER TABLE user
ADD COLUMN vipTime DATETIME NULL COMMENT '成为会员时间',
ADD COLUMN quota INT DEFAULT 0 COMMENT '剩余配额';
```

## 🔧 后端实现

### 1. 枚举类设计

#### 1.1 支付状态枚举

**路径：** `src/main/java/com/yupi/template/model/enums/PaymentStatusEnum.java`

```java
public enum PaymentStatusEnum {
    PENDING("PENDING", "待支付"),
    SUCCEEDED("SUCCEEDED", "支付成功"),
    FAILED("FAILED", "支付失败"),
    REFUNDED("REFUNDED", "已退款");

    private final String value;
    private final String text;

    // 构造函数、getter 方法
}
```

#### 1.2 产品类型枚举

**路径：** `src/main/java/com/yupi/template/model/enums/ProductTypeEnum.java`

```java
public enum ProductTypeEnum {
    VIP_PERMANENT("VIP_PERMANENT", "永久会员");

    private final String value;
    private final String text;

    // 构造函数、getter 方法
}
```

### 2. 实体类设计

**路径：** `src/main/java/com/yupi/template/model/entity/PaymentRecord.java`

```java
@Data
@Table("payment_record")
public class PaymentRecord implements Serializable {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private Long userId;
    private String stripeSessionId;
    private String stripePaymentIntentId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String productType;
    private String description;
    private Date refundTime;
    private String refundReason;
    private Date createTime;
    private Date updateTime;
}
```

### 3. DTO 设计

#### 3.1 创建支付会话请求

```java
@Data
public class CreateCheckoutSessionRequest {
    private Long userId;
    private String productType;
    private String successUrl;
    private String cancelUrl;
}
```

### 4. Mapper 接口设计

**路径：** `src/main/java/com/yupi/template/mapper/PaymentRecordMapper.java`

```java
@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {
    // 基础 CRUD 由 BaseMapper 提供
}
```

### 5. Service 层设计

**路径：** `src/main/java/com/yupi/template/service/PaymentService.java`

**核心方法：**

1. **创建支付会话**
   ```java
   String createCheckoutSession(CreateCheckoutSessionRequest request);
   ```
   - 创建 Stripe Checkout Session
   - 保存支付记录
   - 返回支付URL

2. **处理支付成功**
   ```java
   void handlePaymentSuccess(String sessionId);
   ```
   - 更新支付记录状态
   - 更新用户会员状态
   - 增加用户配额

3. **处理支付失败**
   ```java
   void handlePaymentFailed(String sessionId);
   ```
   - 更新支付记录状态
   - 记录失败原因

4. **处理退款**
   ```java
   void handleRefund(String paymentIntentId, String reason);
   ```
   - 更新支付记录状态
   - 取消用户会员状态
   - 记录退款信息

### 6. Controller 层设计

**路径：** `src/main/java/com/yupi/template/controller/PaymentController.java`

**接口列表：**

1. POST `/api/payment/create-checkout-session` - 创建支付会话
2. GET `/api/payment/success` - 支付成功页面
3. GET `/api/payment/cancel` - 支付取消页面

**路径：** `src/main/java/com/yupi/template/controller/StripeWebhookController.java`

**接口列表：**

1. POST `/api/payment/webhook` - Stripe Webhook 回调

### 7. Stripe 配置

**路径：** `src/main/java/com/yupi/template/config/StripeConfig.java`

```java
@Configuration
public class StripeConfig {

    @Value("${stripe.api-key}")
    private String apiKey;

    @Bean
    public Stripe stripe() {
        Stripe.apiKey = apiKey;
        return new Stripe();
    }
}
```

## 🎨 前端实现

### 1. VIP 页面

**路径：** `frontend/src/pages/VipPage.vue`

```vue
<template>
  <div class="vip-page">
    <a-card title="VIP 会员">
      <div class="vip-benefits">
        <h3>会员权益</h3>
        <ul>
          <li>✨ AI 智能生图</li>
          <li>🎨 SVG 示意图生成</li>
          <li>🚀 更高的配额</li>
          <li>💎 专属客服</li>
        </ul>
      </div>
      <a-button 
        type="primary" 
        @click="handlePurchase"
        :loading="loading"
      >
        购买永久会员 ($9.99)
      </a-button>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { createCheckoutSession } from '@/api/paymentController';
import { useLoginUserStore } from '@/stores/loginUser';

const loginUserStore = useLoginUserStore();
const loading = ref(false);

const handlePurchase = async () => {
  try {
    loading.value = true;
    const res = await createCheckoutSession({
      productType: 'VIP_PERMANENT',
      successUrl: `${window.location.origin}/payment/success`,
      cancelUrl: `${window.location.origin}/payment/cancel`,
    });

    if (res.data) {
      window.location.href = res.data;
    }
  } catch (error) {
    console.error('创建支付会话失败:', error);
  } finally {
    loading.value = false;
  }
};
</script>
```

### 2. API 封装

**路径：** `frontend/src/api/paymentController.ts`

```typescript
import request from '../request';

// 创建支付会话
export const createCheckoutSession = (data: API.CreateCheckoutSessionRequest) => {
  return request.post('/api/payment/create-checkout-session', data);
};
```

## 📝 学习任务清单

### 后端任务
- [ ] 创建 payment_record 表
- [ ] 扩展 user 表字段
- [ ] 实现 PaymentStatusEnum 枚举
- [ ] 实现 ProductTypeEnum 枚举
- [ ] 实现 PaymentRecord 实体类
- [ ] 实现支付相关 DTO
- [ ] 实现 PaymentRecordMapper 接口
- [ ] 实现 PaymentService 接口及实现类
- [ ] 实现 PaymentController 控制器
- [ ] 实现 StripeWebhookController 控制器
- [ ] 实现 StripeConfig 配置

### 前端任务
- [ ] 实现 VIP 页面
- [ ] 封装支付相关 API
- [ ] 实现支付成功页面
- [ ] 实现支付取消页面

## 💡 实现提示

1. **支付安全**：使用 Stripe Webhook 验证支付状态
2. **幂等性**：Webhook 处理要保证幂等性
3. **会员状态**：支付成功后立即更新会员状态
4. **配额管理**：VIP 用户获得更高的配额
5. **错误处理**：完善的错误处理和日志记录
6. **测试环境**：使用 Stripe 测试环境进行测试

## 🔍 测试建议

1. 测试创建支付会话
2. 测试支付成功流程
3. 测试支付失败流程
4. 测试 Webhook 回调
5. 测试会员状态更新
6. 测试配额管理
7. 测试退款流程

## 📚 参考资源

- Stripe 官方文档
- Stripe Checkout 文档
- Stripe Webhook 文档
- Spring Boot 官方文档
- Vue 3 官方文档
