# 模块7：支付与会员模块

> 目标：Stripe Checkout 跑通 + 配额扣减 + VIP 权限校验

## 核心流程

```
用户点击购买VIP
  → 后端创建 Stripe Checkout Session
  → 返回 Session URL
  → 前端跳转 Stripe 支付页
  → 用户完成支付
  → Stripe Webhook 通知后端
  → 后端更新 user.vipTime + 记录 payment_record
  → 前端轮询或 WebSocket 得知支付成功
```

## VIP 权益设计

| 权益 | 普通用户 | VIP 用户 |
|------|---------|---------|
| 文章创作配额 | quota 字段控制（默认 5） | 不限量 |
| AI 生图（Nano Banana） | 不可用 | 可用 |
| SVG 示意图 | 不可用 | 可用 |
| Mermaid 流程图 | 可用 | 可用 |
| Pexels 图库 | 可用 | 可用 |

> **判断 VIP 状态**：`user.vipTime != null && user.vipTime > now()`。vipTime 是过期时间，永不过期可设为一个很远的未来日期。

## API 设计

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/payment/create-vip-session` | 创建 VIP 支付会话 | 登录 |
| POST | `/webhook/stripe` | Stripe Webhook 回调 | 无（Stripe 签名验证） |
| GET | `/payment/records` | 查询支付记录 | 登录 |
| POST | `/payment/refund` | 退款 | 管理员 |

## Stripe 集成要点

### 1. 创建 Checkout Session

```java
@Service
public class PaymentServiceImpl implements PaymentService {

    public String createVipSession(Long userId, String successUrl, String cancelUrl) {
        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .addLineItem(lineItem -> lineItem
                .setPriceData(priceData -> priceData
                    .setCurrency("cny")
                    .setUnitAmount(19900L)  // 199.00 元，单位分
                    .setProductData(productData -> productData
                        .setName("VIP 永久会员")))
                .setQuantity(1L))
            .putMetadata("userId", userId.toString())
            .build();

        Session session = Session.create(params);
        return session.getUrl();  // 前端跳转到此 URL
    }
}
```

### 2. Webhook 处理

```java
@PostMapping("/webhook/stripe")
public String handleStripeWebhook(@RequestBody String payload,
                                   @RequestHeader("Stripe-Signature") String sigHeader) {
    // 1. 验证签名（防伪造）
    Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

    // 2. 处理 checkout.session.completed 事件
    if ("checkout.session.completed".equals(event.getType())) {
        Session session = (Session) event.getDataObjectDeserializer()
            .getObject().orElse(null);

        // 3. 从 metadata 取 userId
        Long userId = Long.valueOf(session.getMetadata().get("userId"));

        // 4. 更新用户 VIP 状态
        userService.updateVipTime(userId, PERMANENT_VIP_TIME);

        // 5. 记录支付
        paymentRecordService.save(/* ... */);
    }

    return "success";  // Stripe 要求返回 200
}
```

> **安全要点**：Webhook 必须验证 Stripe 签名，否则任何人都能伪造支付成功通知。使用 `Stripe-Signature` 头 + Webhook Secret 验证。

### 3. 退款

```java
public void refund(Long paymentId) {
    // 1. 查询支付记录，获取 stripePaymentIntentId
    // 2. 调用 Stripe API 创建退款
    RefundCreateParams params = RefundCreateParams.builder()
        .setPaymentIntent(paymentRecord.getStripePaymentIntentId())
        .build();
    Refund refund = Refund.create(params);

    // 3. 更新支付记录状态为 REFUNDED
    // 4. 取消用户 VIP 状态
}
```

## 配额管理

### QuotaService 设计

```java
@Service
public class QuotaServiceImpl implements QuotaService {

    public boolean hasQuota(User user) {
        // VIP 不限配额
        if (isVip(user)) return true;
        return user.getQuota() > 0;
    }

    public void consumeQuota(Long userId) {
        User user = userService.getById(userId);
        if (!isVip(user)) {
            // 扣减配额，不能为负
            user.setQuota(Math.max(0, user.getQuota() - 1));
            userService.updateById(user);
        }
    }

    private boolean isVip(User user) {
        return user.getVipTime() != null && user.getVipTime().isAfter(LocalDateTime.now());
    }
}
```

### 配额检查时机

```
POST /article/create
  → QuotaService.hasQuota(user)  // 检查
  → 创建文章
  → QuotaService.consumeQuota(userId)  // 扣减
```

## 配置项

```yaml
stripe:
  api-key: sk_test_xxx           # Stripe 密钥
  webhook-secret: whsec_xxx      # Webhook 签名密钥
  success-url: http://localhost/vip?success=true
  cancel-url: http://localhost/vip?cancelled=true
```

## 本模块验证标准

- [ ] 创建 Checkout Session 返回有效 URL
- [ ] Stripe 测试支付完成后 Webhook 能收到通知
- [ ] Webhook 处理后用户 vipTime 更新
- [ ] payment_record 表记录支付信息
- [ ] VIP 用户创建文章不受配额限制
- [ ] 非VIP用户配额扣减正确，配额为 0 时不能创建文章
- [ ] 管理员能执行退款操作