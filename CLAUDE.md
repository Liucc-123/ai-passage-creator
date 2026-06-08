# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI Passage Creator (AI 爆款文章创作器) is a multi-agent collaborative text-image creation platform. Users input a topic, and 5 AI Agents work through a 3-phase process (outline → draft → images) to generate a complete illustrated article.

**Status**: Scaffold only — core modules are not yet implemented. This is a learning project based on the detailed study plans in `docs/study-plan/`.

## Technology Stack

| Layer | Technology |
|-------|------------|
| Framework | Spring Boot 4.0.6 + JDK 17 |
| ORM | MyBatis-Flex (camelCase disabled) |
| Database | MySQL 8.0 |
| Cache/Session | Spring Data Redis + Spring Session (30-day timeout) |
| AI Integration | Spring AI Alibaba + DashScope (通义千问) |
| Payment | Stripe Checkout |
| Image Storage | Tencent Cloud COS |
| Build | Maven |

## Project Structure

```
src/main/java/com/liucc/passage/
├── config/              # Configuration classes
│   ├── CorsConfig.java      # CORS configuration
│   ├── JsonConfig.java      # Long → String serialization
│   └── AsyncConfig.java     # Async thread pool
├── constant/            # Constants
├── controller/          # REST controllers
├── exception/           # Global exception handling
├── mapper/              # MyBatis-Flex mappers
├── model/
│   ├── entity/          # Database entities
│   ├── dto/             # Request DTOs
│   ├── vo/              # Response DTOs
│   └── enums/           # Enumerations
├── service/             # Business services
│   └── impl/            # Service implementations
└── utils/               # Utility classes
```

## Database Schema

Four core tables created during scaffold setup:

| Table | Purpose |
|-------|---------|
| `user` | User accounts with quota/VIP tracking |
| `article` | Article content with phase/state tracking |
| `agent_log` | Execution logs for AI agents |
| `payment_record` | Stripe payment records |

SQL files are in `sql/` directory.

## Development Commands

```bash
# Start the application
./mvnw spring-boot:run

# Run tests
./mvnw test

# Build JAR
./mvnw package

# Build executable JAR with dependencies
./mvnw spring-boot:repackage
```

The server runs on `http://localhost:8567/api` by default.

## Module Architecture & Implementation Order

The project follows a 8-module learning path documented in `docs/study-plan/`:

1. **Scaffold** (01-scaffold.md) — Base setup, MySQL+Redis connection, health check
2. **User Module** (02-user.md) — Registration/login with Session, admin role checks
3. **Article Module** (03-article.md) — CRUD + state machine with phase transitions
4. **SSE Communication** (04-sse.md) — Server-sent events for real-time progress
5. **Agent Module** (05-agent.md) — 5 AI agents with 3-phase orchestration (core)
6. **Image Module** (06-image.md) — Strategy pattern with 6 image sources + fallback
7. **Payment Module** (07-payment.md) — Stripe Checkout + VIP quota system
8. **Frontend** (08-frontend.md) — Vue 3 + Ant Design Vue SPA

## Key Concepts

### Article State Machine

Articles progress through phases that must transition linearly:

```
PENDING → TITLE_GENERATING → TITLE_SELECTING → OUTLINE_GENERATING → OUTLINE_EDITING → CONTENT_GENERATING → COMPLETED
```

Each phase has:
- `status`: PENDING/PROCESSING/COMPLETED/FAILED (overall state)
- `phase`: Current stage of the创作 pipeline

### Five AI Agents

| Agent | Phase | Responsibility |
|-------|-------|----------------|
| TitleGeneratorAgent | 1 | Generate 3-5 title options |
| OutlineGeneratorAgent | 2 | Create structured outline with sections |
| ContentGeneratorAgent | 3 | Generate markdown body with image placeholders |
| ImageAnalyzerAgent | 3 | Extract image requirements from content |
| ContentMergerAgent | 3 | Replace placeholders with actual image URLs |

### SSE Message Types

SSE events use `SseMessageTypeEnum`:
- `AGENT*_STREAMING` — Incremental token chunks (use prefix + data format)
- `AGENT*_COMPLETE` — Agent finished this phase
- `IMAGE_COMPLETE` — Individual image ready
- `ALL_COMPLETE` / `ERROR` — Final status

### Image Strategy Pattern

Six image sources implemented via strategy pattern:
- `PEXELS` — Stock photo search (all users)
- `MERMAID` — Flowchart generation (requires mmdc CLI)
- `ICONIFY` — Icon library
- `EMOJI_PACK` — Meme search via Bing
- `NANO_BANANA` — AI image generation (VIP only)
- `SVG_DIAGRAM` — SVG diagram generation (VIP only)
- `PICSUM` — Fallback random images

## Configuration Files

| File | Purpose |
|------|---------|
| `application.yaml` | Public configuration (port, DB, Redis) |
| `application-local.yaml` | Local overrides (add to .gitignore) |
| `application-prod.yaml` | Production with ENV variable placeholders |

Key settings:
- Context path: `/api`
- MyBatis-Flex: `map-underscore-to-camel-case: false` (tables use camelCase columns like `taskId`)
- Session: Redis-backed, 30-day timeout

## Common Patterns

### Unified Response

All controllers return `BaseResponse<T>`:
```java
{
  code: 0,          // 0 = success
  data: T,
  message: string
}
```

### Authentication

- Session-based with Redis storage
- `@AuthCheck(mustRole = "admin")` annotation for admin endpoints
- Login state stored as `USER_LOGIN_STATE` in session

### Quota Management

- `QuotaService` checks `user.quota > 0` unless VIP
- VIP status: `vipTime != null && vipTime > now()`
- Quota consumed on article creation

## Testing

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=YourTestClass
```

## Next Steps for Development

1. Run database initialization SQL from `sql/`
2. Set up `application-local.yaml` with API keys (DashScope, Stripe, COS)
3. Follow study plan modules in order — start with 01-scaffold.md
4. Verify health check endpoint: `GET /api/health/`
