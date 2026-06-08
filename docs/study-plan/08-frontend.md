# 模块8：前端应用

> 目标：Vue 3 项目搭建 + 文章创作全流程页面 + SSE 实时交互

## 技术选型

| 层面 | 选择 | 说明 |
|------|------|------|
| 框架 | Vue 3 + TypeScript | Composition API + `<script setup>` |
| UI 库 | Ant Design Vue 4.x | 中文友好，组件丰富 |
| 状态管理 | Pinia | Vue 3 官方推荐 |
| 路由 | Vue Router 4 | 路由守卫做权限控制 |
| HTTP | Axios | 统一拦截器处理鉴权/错误 |
| 构建 | Vite | 开发热更新快 |
| Markdown | marked | 正文渲染 |
| 图表 | ECharts | 管理后台统计 |
| 拖拽 | SortableJS | 大纲编辑拖拽排序 |

## 项目结构

```
frontend/src/
├── App.vue                     # 根组件
├── main.ts                     # 入口：安装 Pinia/Router/Antd
├── access.ts                   # 全局路由守卫
├── request.ts                  # Axios 实例（拦截器）
│
├── config/
│   └── env.ts                  # API_BASE_URL 配置
│
├── constants/
│   ├── index.ts                # 通用常量
│   ├── article.ts              # 文章状态/阶段枚举
│   └── user.ts                 # 用户角色常量
│
├── stores/
│   └── loginUser.ts            # 登录用户 Pinia Store
│
├── api/
│   ├── typings.d.ts            # TypeScript 类型定义（OpenAPI 生成）
│   ├── articleController.ts    # 文章 API
│   ├── userController.ts       # 用户 API
│   ├── paymentController.ts    # 支付 API
│   └── statisticsController.ts # 统计 API
│
├── utils/
│   ├── sse.ts                  # SSE 连接工具
│   ├── article.ts              # 文章状态/颜色映射
│   ├── markdown.ts             # Markdown 渲染
│   └── permission.ts           # isAdmin() / isVip() / hasQuota()
│
├── components/
│   ├── GlobalHeader.vue        # 顶部导航
│   └── GlobalFooter.vue        # 底部
│
├── layouts/
│   └── BasicLayout.vue         # 布局壳：Header + Content + Footer
│
├── pages/
│   ├── HomePage.vue            # 首页
│   ├── VipPage.vue             # VIP 购买页
│   ├── user/
│   │   ├── UserLoginPage.vue
│   │   └── UserRegisterPage.vue
│   ├── article/
│   │   ├── ArticleCreatePage.vue    # ★ 核心页面
│   │   ├── ArticleListPage.vue
│   │   ├── ArticleDetailPage.vue
│   │   └── components/
│   │       ├── InputState.vue           # 输入主题
│   │       ├── TitleSelectingStage.vue  # 选择标题
│   │       ├── OutlineEditingStage.vue  # 编辑大纲
│   │       ├── CreatingState.vue        # 生成中（SSE 进度）
│   │       └── CompletedState.vue       # 生成完成
│   └── admin/
│       ├── UserManagePage.vue
│       └── StatisticsPage.vue
│
└── router/
    └── index.ts
```

## 路由设计

| 路径 | 组件 | 鉴权 | 加载方式 |
|------|------|------|---------|
| `/` | HomePage | 无 | 立即加载 |
| `/create` | ArticleCreatePage | 登录 | 懒加载 |
| `/article/list` | ArticleListPage | 登录 | 懒加载 |
| `/article/:taskId` | ArticleDetailPage | 登录 | 懒加载 |
| `/user/login` | UserLoginPage | 无 | 立即加载 |
| `/user/register` | UserRegisterPage | 无 | 立即加载 |
| `/admin/userManage` | UserManagePage | 管理员 | 立即加载 |
| `/admin/statistics` | StatisticsPage | 管理员 | 懒加载 |
| `/vip` | VipPage | 登录 | 懒加载 |

## 路由守卫

```typescript
// access.ts
router.beforeEach(async (to, from, next) => {
  // 1. 首次导航，获取登录用户信息
  if (!loginUserStore.user) {
    await loginUserStore.fetchLoginUser();
  }

  // 2. 管理员页面权限校验
  if (to.path.startsWith('/admin') && loginUserStore.user?.userRole !== 'admin') {
    message.error('无权限');
    next('/user/login');
    return;
  }

  next();
});
```

## Axios 拦截器

```typescript
// request.ts
const instance = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,   // Cookie 跨域
  timeout: 60000,
});

// 响应拦截：统一处理未登录
instance.interceptors.response.use((response) => {
  const { code, data, message } = response.data;
  if (code === 40100) {
    // 未登录，跳转登录页
    router.push(`/user/login?redirect=${encodeURIComponent(window.location.href)}`);
    return Promise.reject(new Error('未登录'));
  }
  return response.data;
});
```

## ★ 核心页面：ArticleCreatePage

