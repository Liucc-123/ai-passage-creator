# 部署与运维设计文档

## 📌 模块概述

本模块介绍如何将 AI 爆款文章创作器项目部署到生产环境。采用 Docker 容器化部署方案，支持一键启动所有服务，包括前端、后端、MySQL、Redis 等。

## 🎯 学习目标

完成本模块后，你将能够：
1. 理解 Docker 容器化部署
2. 掌握 Docker Compose 编排
3. 实现生产环境配置
4. 掌握基本运维操作

## 🏗️ 架构设计

### 部署架构

```
┌─────────────────────────────────────────────────────────────┐
│                        Nginx                                │
│                      (反向代理)                              │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   前端       │    │   后端       │    │   MySQL      │
│  (Vue)       │    │  (Spring)    │    │  (数据库)    │
└──────────────┘    └──────────────┘    └──────────────┘
                              │
                              ▼
                        ┌──────────────┐
                        │   Redis      │
                        │  (缓存)      │
                        └──────────────┘
```

## 🐳 Docker 部署

### 1. 项目结构

```
ai-passage-creator/
├── docker-compose.yml           # Docker 编排文件
├── .env.example                # 环境变量示例
├── .env                        # 实际环境变量（不提交到 Git）
├── backend/
│   ├── Dockerfile              # 后端 Dockerfile
│   └── src/main/resources/
│       └── application-prod.yml
├── frontend/
│   ├── Dockerfile              # 前端 Dockerfile
│   └── nginx.conf
└── sql/
    └── create_table.sql        # 数据库初始化脚本
```

### 2. 环境变量配置

创建 `.env` 文件：

```bash
# 数据库配置
MYSQL_ROOT_PASSWORD=your_root_password
MYSQL_DATABASE=ai_passage_creator

# Redis 配置
REDIS_PASSWORD=your_redis_password

# 后端配置
BACKEND_PORT=8123
SPRING_PROFILES_ACTIVE=prod

# AI 服务配置
DASHSCOPE_API_KEY=your_dashscope_api_key
PEXELS_API_KEY=your_pexels_api_key
NANO_BANANA_API_KEY=your_nano_banana_api_key

# 支付配置
STRIPE_API_KEY=your_stripe_api_key
STRIPE_WEBHOOK_SECRET=your_webhook_secret

# 对象存储配置
TENCENT_COS_SECRET_ID=your_cos_secret_id
TENCENT_COS_SECRET_KEY=your_cos_secret_key
TENCENT_COS_BUCKET=your_bucket_name
TENCENT_COS_REGION=your_region
```

### 3. Docker Compose 配置

创建 `docker-compose.yml`：

```yaml
version: '3.8'

services:
  # MySQL 数据库
  mysql:
    image: mysql:8.0
    container_name: ai-passage-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - ./sql:/docker-entrypoint-initdb.d
    networks:
      - ai-passage-network

  # Redis 缓存
  redis:
    image: redis:7-alpine
    container_name: ai-passage-redis
    command: redis-server --requirepass ${REDIS_PASSWORD}
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - ai-passage-network

  # 后端服务
  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: ai-passage-backend
    ports:
      - "${BACKEND_PORT}:8123"
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE}
      MYSQL_HOST: mysql
      MYSQL_PORT: 3306
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      MYSQL_USERNAME: root
      MYSQL_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      DASHSCOPE_API_KEY: ${DASHSCOPE_API_KEY}
      PEXELS_API_KEY: ${PEXELS_API_KEY}
      NANO_BANANA_API_KEY: ${NANO_BANANA_API_KEY}
      STRIPE_API_KEY: ${STRIPE_API_KEY}
      STRIPE_WEBHOOK_SECRET: ${STRIPE_WEBHOOK_SECRET}
      TENCENT_COS_SECRET_ID: ${TENCENT_COS_SECRET_ID}
      TENCENT_COS_SECRET_KEY: ${TENCENT_COS_SECRET_KEY}
      TENCENT_COS_BUCKET: ${TENCENT_COS_BUCKET}
      TENCENT_COS_REGION: ${TENCENT_COS_REGION}
    depends_on:
      - mysql
      - redis
    networks:
      - ai-passage-network

  # 前端服务
  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: ai-passage-frontend
    ports:
      - "80:80"
    depends_on:
      - backend
    networks:
      - ai-passage-network

volumes:
  mysql-data:
  redis-data:

networks:
  ai-passage-network:
    driver: bridge
```

### 4. 后端 Dockerfile

创建 `backend/Dockerfile`：

```dockerfile
FROM maven:3.8-openjdk-21-slim AS builder

WORKDIR /app

# 复制 pom.xml 并下载依赖
COPY pom.xml .
RUN mvn dependency:go-offline

# 复制源代码并构建
COPY src ./src
RUN mvn clean package -DskipTests

# 运行阶段
FROM openjdk:21-slim

WORKDIR /app

# 复制构建产物
COPY --from=builder /app/target/*.jar app.jar

# 暴露端口
EXPOSE 8123

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 5. 前端 Dockerfile

创建 `frontend/Dockerfile`：

```dockerfile
# 构建阶段
FROM node:18-alpine AS builder

WORKDIR /app

# 复制 package.json 并安装依赖
COPY package*.json ./
RUN npm install

# 复制源代码并构建
COPY ../study-plan .
RUN npm run build

# 运行阶段
FROM nginx:alpine

# 复制构建产物和 Nginx 配置
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf

# 暴露端口
EXPOSE 80

# 启动 Nginx
CMD ["nginx", "-g", "daemon off;"]
```

### 6. Nginx 配置

创建 `frontend/nginx.conf`：

```nginx
user nginx;
worker_processes auto;

events {
    worker_connections 1024;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;

    server {
        listen 80;
        server_name localhost;

        # 前端静态资源
        location / {
            root /usr/share/nginx/html;
            index index.html;
            try_files $uri $uri/ /index.html;
        }

        # 后端 API 代理
        location /api/ {
            proxy_pass http://backend:8123/api/;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }

        # SSE 长连接配置
        location /api/article/sse/ {
            proxy_pass http://backend:8123/api/article/sse/;
            proxy_buffering off;
            proxy_cache off;
            proxy_set_header Connection '';
            proxy_http_version 1.1;
            chunked_transfer_encoding off;
        }
    }
}
```

## 🚀 部署步骤

### 1. 准备工作

1. 安装 Docker 和 Docker Compose
2. 准备服务器（推荐配置：2核4G以上）
3. 购买域名（可选）

### 2. 配置环境变量

```bash
# 复制环境变量示例文件
cp .env.example .env

# 编辑环境变量
vim .env
```

### 3. 构建并启动服务

```bash
# 构建并启动所有服务
docker-compose up -d --build

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

### 4. 初始化数据库

```bash
# 进入 MySQL 容器
docker-compose exec mysql bash

# 连接数据库
mysql -uroot -p${MYSQL_ROOT_PASSWORD}

# 执行初始化脚本
source /docker-entrypoint-initdb.d/create_table.sql
```

### 5. 验证部署

1. 访问前端：http://your-server-ip
2. 访问后端 API：http://your-server-ip/api/doc.html
3. 测试用户注册和登录
4. 测试文章创建功能

## 🔧 运维操作

### 1. 查看日志

```bash
# 查看所有服务日志
docker-compose logs

# 查看特定服务日志
docker-compose logs backend
docker-compose logs frontend

# 实时查看日志
docker-compose logs -f backend
```

### 2. 重启服务

```bash
# 重启所有服务
docker-compose restart

# 重启特定服务
docker-compose restart backend
```

### 3. 更新服务

```bash
# 拉取最新代码
git pull

# 重新构建并启动
docker-compose up -d --build
```

### 4. 备份数据

```bash
# 备份 MySQL 数据
docker-compose exec mysql mysqldump -uroot -p${MYSQL_ROOT_PASSWORD} ${MYSQL_DATABASE} > backup.sql

# 恢复 MySQL 数据
docker-compose exec -T mysql mysql -uroot -p${MYSQL_ROOT_PASSWORD} ${MYSQL_DATABASE} < backup.sql
```

### 5. 监控服务

```bash
# 查看资源使用情况
docker stats

# 查看容器详细信息
docker inspect ai-passage-backend
```

## 🔒 安全配置

### 1. 配置 HTTPS

使用 Let's Encrypt 免费证书：

```bash
# 安装 Certbot
apt-get install certbot python3-certbot-nginx

# 获取证书
certbot --nginx -d your-domain.com

# 自动续期
certbot renew --dry-run
```

### 2. 配置防火墙

```bash
# 只开放必要端口
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 22/tcp
ufw enable
```

### 3. 数据库安全

- 修改默认端口
- 使用强密码
- 限制远程访问
- 定期备份

## 📝 学习任务清单

- [ ] 安装 Docker 和 Docker Compose
- [ ] 配置环境变量
- [ ] 编写 Docker Compose 文件
- [ ] 编写后端 Dockerfile
- [ ] 编写前端 Dockerfile
- [ ] 配置 Nginx
- [ ] 部署到服务器
- [ ] 配置 HTTPS
- [ ] 配置防火墙
- [ ] 设置自动备份

## 💡 实现提示

1. **环境变量**：敏感信息不要提交到 Git
2. **数据持久化**：使用 Docker Volume 持久化数据
3. **日志管理**：配置日志轮转，避免日志过大
4. **资源限制**：为容器设置资源限制
5. **健康检查**：配置容器健康检查
6. **监控告警**：配置监控和告警系统

## 🔍 测试建议

1. 测试服务启动
2. 测试服务重启
3. 测试数据备份和恢复
4. 测试 HTTPS 访问
5. 测试负载情况
6. 测试故障恢复

## 📚 参考资源

- Docker 官方文档
- Docker Compose 官方文档
- Nginx 官方文档
- Let's Encrypt 官方文档
- Spring Boot 官方文档
