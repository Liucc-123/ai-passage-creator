# 管理后台模块设计文档

## 📌 模块概述

管理后台模块为管理员提供系统管理功能，包括用户管理、统计分析、智能体日志查询等。本模块采用 RBAC（基于角色的访问控制）模型，确保只有管理员可以访问管理功能。

## 🎯 学习目标

完成本模块后，你将能够：
1. 理解 RBAC 权限模型
2. 实现用户管理功能
3. 实现数据统计分析
4. 实现日志查询功能

## 🏗️ 架构设计

### 管理后台架构

```
┌─────────────────────────────────────────────────────────────┐
│                        管理后台                              │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  用户管理    │    │  统计分析    │    │  日志查询    │
└──────────────┘    └──────────────┘    └──────────────┘
```

## 🔧 后端实现

### 1. 统计数据 VO

**路径：** `src/main/java/com/yupi/template/model/vo/StatisticsVO.java`

```java
@Data
public class StatisticsVO implements Serializable {
    // 用户统计
    private Long totalUsers;           // 总用户数
    private Long todayNewUsers;        // 今日新增用户
    private Long vipUsers;             // VIP 用户数

    // 文章统计
    private Long totalArticles;        // 总文章数
    private Long todayArticles;        // 今日文章数
    private Long completedArticles;    // 已完成文章数

    // 智能体统计
    private Long totalAgentExecutions; // 智能体总执行次数
    private Long avgExecutionTime;     // 平均执行时间（毫秒）
    private Map<String, Long> agentExecutionCount; // 各智能体执行次数

    // 支付统计
    private Long totalPayments;        // 总支付次数
    private BigDecimal totalRevenue;   // 总收入
    private BigDecimal todayRevenue;   // 今日收入
}
```

### 2. Service 层设计

#### 2.1 用户管理服务

**路径：** `src/main/java/com/yupi/template/service/UserService.java`

**管理员专用方法：**

```java
// 分页查询用户（管理员）
Page<UserVO> listUserByPage(UserQueryRequest request);

// 删除用户（管理员）
boolean deleteUser(Long id);

// 更新用户角色（管理员）
boolean updateUserRole(Long userId, String userRole);

// 重置用户密码（管理员）
boolean resetUserPassword(Long userId);
```

#### 2.2 统计服务

**路径：** `src/main/java/com/yupi/template/service/StatisticsService.java`

```java
@Service
public class StatisticsService {

    /**
     * 获取统计数据
     */
    public StatisticsVO getStatistics();

    /**
     * 获取用户增长趋势
     */
    public List<ChartDataVO> getUserGrowthTrend(int days);

    /**
     * 获取文章创作趋势
     */
    public List<ChartDataVO> getArticleCreationTrend(int days);

    /**
     * 获取智能体执行统计
     */
    public List<AgentExecutionStatsVO> getAgentExecutionStats();
}
```

#### 2.3 智能体日志服务

**路径：** `src/main/java/com/yupi/template/service/AgentLogService.java`

```java
@Service
public class AgentLogService {

    /**
     * 记录智能体执行日志
     */
    void logAgentExecution(AgentLog log);

    /**
     * 分页查询智能体日志
     */
    Page<AgentLog> listAgentLogsByPage(AgentLogQueryRequest request);

    /**
     * 获取任务的所有日志
     */
    List<AgentLog> getLogsByTaskId(String taskId);

    /**
     * 获取智能体执行统计
     */
    AgentExecutionStatsVO getAgentStats(String agentName);
}
```

### 3. Controller 层设计

#### 3.1 统计控制器

**路径：** `src/main/java/com/yupi/template/controller/StatisticsController.java`

```java
@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    @Resource
    private StatisticsService statisticsService;

    /**
     * 获取统计数据
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<StatisticsVO> getStatistics() {
        StatisticsVO statistics = statisticsService.getStatistics();
        return ResultUtils.success(statistics);
    }

    /**
     * 获取用户增长趋势
     */
    @GetMapping("/user/growth")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<List<ChartDataVO>> getUserGrowthTrend(
        @RequestParam(defaultValue = "7") int days
    ) {
        List<ChartDataVO> trend = statisticsService.getUserGrowthTrend(days);
        return ResultUtils.success(trend);
    }

    /**
     * 获取文章创作趋势
     */
    @GetMapping("/article/trend")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<List<ChartDataVO>> getArticleCreationTrend(
        @RequestParam(defaultValue = "7") int days
    ) {
        List<ChartDataVO> trend = statisticsService.getArticleCreationTrend(days);
        return ResultUtils.success(trend);
    }
}
```

## 🎨 前端实现

### 1. 统计页面

**路径：** `frontend/src/pages/admin/StatisticsPage.vue`

```vue
<template>
  <div class="statistics-page">
    <!-- 统计卡片 -->
    <a-row :gutter="16">
      <a-col :span="6">
        <a-card title="总用户数">
          <a-statistic :value="statistics.totalUsers" />
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card title="总文章数">
          <a-statistic :value="statistics.totalArticles" />
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card title="总收入">
          <a-statistic 
            :value="statistics.totalRevenue" 
            :precision="2"
            prefix="$"
          />
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card title="智能体执行次数">
          <a-statistic :value="statistics.totalAgentExecutions" />
        </a-card>
      </a-col>
    </a-row>

    <!-- 图表 -->
    <a-row :gutter="16" style="margin-top: 16px">
      <a-col :span="12">
        <a-card title="用户增长趋势">
          <div ref="userGrowthChartRef" style="height: 300px"></div>
        </a-card>
      </a-col>
      <a-col :span="12">
        <a-card title="文章创作趋势">
          <div ref="articleTrendChartRef" style="height: 300px"></div>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import * as echarts from 'echarts';
import { getStatistics, getUserGrowthTrend, getArticleCreationTrend } from '@/api/statisticsController';

const statistics = ref<API.StatisticsVO>({});
const userGrowthChartRef = ref<HTMLElement>();
const articleTrendChartRef = ref<HTMLElement>();

// 初始化图表
const initCharts = async () => {
  // 获取统计数据
  const statsRes = await getStatistics();
  statistics.value = statsRes.data;

  // 获取用户增长趋势
  const userTrend = await getUserGrowthTrend(7);
  initUserGrowthChart(userTrend.data);

  // 获取文章创作趋势
  const articleTrend = await getArticleCreationTrend(7);
  initArticleTrendChart(articleTrend.data);
};

const initUserGrowthChart = (data: API.ChartDataVO[]) => {
  if (!userGrowthChartRef.value) return;

  const chart = echarts.init(userGrowthChartRef.value);
  chart.setOption({
    xAxis: {
      type: 'category',
      data: data.map(item => item.date),
    },
    yAxis: {
      type: 'value',
    },
    series: [{
      data: data.map(item => item.value),
      type: 'line',
    }],
  });
};

const initArticleTrendChart = (data: API.ChartDataVO[]) => {
  if (!articleTrendChartRef.value) return;

  const chart = echarts.init(articleTrendChartRef.value);
  chart.setOption({
    xAxis: {
      type: 'category',
      data: data.map(item => item.date),
    },
    yAxis: {
      type: 'value',
    },
    series: [{
      data: data.map(item => item.value),
      type: 'bar',
    }],
  });
};

onMounted(() => {
  initCharts();
});
</script>
```

### 2. 用户管理页面

**路径：** `frontend/src/pages/admin/UserManagePage.vue`

```vue
<template>
  <div class="user-manage-page">
    <a-card title="用户管理">
      <!-- 搜索表单 -->
      <a-form :model="searchForm" layout="inline">
        <a-form-item label="账号">
          <a-input v-model:value="searchForm.userAccount" />
        </a-form-item>
        <a-form-item label="角色">
          <a-select v-model:value="searchForm.userRole" style="width: 120px">
            <a-select-option value="">全部</a-select-option>
            <a-select-option value="user">普通用户</a-select-option>
            <a-select-option value="admin">管理员</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleSearch">搜索</a-button>
        </a-form-item>
      </a-form>

      <!-- 用户列表 -->
      <a-table
        :columns="columns"
        :data-source="userList"
        :pagination="pagination"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'userRole'">
            <a-tag :color="record.userRole === 'admin' ? 'red' : 'blue'">
              {{ record.userRole === 'admin' ? '管理员' : '普通用户' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" @click="handleEdit(record)">编辑</a-button>
              <a-button type="link" danger @click="handleDelete(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue';
import { listUserByPage, deleteUser } from '@/api/userController';

const searchForm = reactive({
  userAccount: '',
  userRole: '',
});

const userList = ref<API.UserVO[]>([]);
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
});

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id' },
  { title: '账号', dataIndex: 'userAccount', key: 'userAccount' },
  { title: '昵称', dataIndex: 'userName', key: 'userName' },
  { title: '角色', dataIndex: 'userRole', key: 'userRole' },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
  { title: '操作', key: 'action' },
];

const handleSearch = async () => {
  const res = await listUserByPage({
    ...searchForm,
    current: pagination.current,
    pageSize: pagination.pageSize,
  });
  userList.value = res.data.records;
  pagination.total = res.data.total;
};

const handleTableChange = (pag: any) => {
  pagination.current = pag.current;
  pagination.pageSize = pag.pageSize;
  handleSearch();
};

const handleDelete = async (record: API.UserVO) => {
  await deleteUser(record.id);
  handleSearch();
};

handleSearch();
</script>
```

### 3. API 封装

**路径：** `frontend/src/api/statisticsController.ts`

```typescript
import request from '../request';

// 获取统计数据
export const getStatistics = () => {
  return request.get('/api/statistics/get');
};

// 获取用户增长趋势
export const getUserGrowthTrend = (days: number) => {
  return request.get(`/api/statistics/user/growth?days=${days}`);
};

// 获取文章创作趋势
export const getArticleCreationTrend = (days: number) => {
  return request.get(`/api/statistics/article/trend?days=${days}`);
};
```

## 📝 学习任务清单

### 后端任务
- [ ] 实现 StatisticsVO
- [ ] 实现用户管理服务方法
- [ ] 实现统计服务
- [ ] 实现智能体日志服务
- [ ] 实现统计控制器
- [ ] 实现用户管理控制器

### 前端任务
- [ ] 实现统计页面
- [ ] 实现用户管理页面
- [ ] 实现智能体日志查询页面
- [ ] 封装统计相关 API
- [ ] 集成 ECharts 图表

## 💡 实现提示

1. **权限控制**：所有管理接口都要加 `@AuthCheck` 注解
2. **数据统计**：使用 SQL 聚合函数提高查询效率
3. **图表展示**：使用 ECharts 实现数据可视化
4. **分页查询**：使用 MyBatis-Flex 分页插件
5. **日志记录**：记录管理员操作日志
6. **性能优化**：统计数据可以缓存

## 🔍 测试建议

1. 测试用户管理功能
2. 测试统计数据显示
3. 测试图表展示
4. 测试权限控制
5. 测试日志查询
6. 测试数据导出

## 📚 参考资源

- RBAC 权限模型
- ECharts 官方文档
- Ant Design Vue 文档
- Spring Boot 官方文档
- MyBatis-Flex 官方文档
