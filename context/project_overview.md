# Throttlex: Advanced URL Shortener & Rate Limiting System

## Project Overview
Throttlex is an enterprise-grade URL Shortener built with high throughput and strict rate limiting in mind. The primary goal is to provide a robust system where users can shorten URLs and explicitly configure rate-limiting algorithms (e.g., Token Bucket, Sliding Window) to protect their endpoints.

## Tech Stack (Current & Future V2)
- **Language:** Java 21 (Utilizing modern features like Records and **Virtual Threads** for massive concurrency).
- **Framework:** Spring Boot 3.2+
- **Primary Database (OLTP):** PostgreSQL (Storing core business logic: Users, URLs, and Rate Limit configurations).
- **Distributed Cache & Rate Limiting Engine:** Redis (Using custom Lua scripts for atomicity).
- **Resiliency / Circuit Breaking:** Resilience4j (Protecting internal infrastructure from cascading failures).
- **L1 Cache:** Caffeine (Serving as an in-memory lock to prevent Cache Stampedes/Thundering Herds).
- **Analytics Ingestion (V2):** Apache Kafka (Buffering raw click events to avoid Postgres write overload).
- **Analytics Datastore (V2):** ClickHouse (Columnar OLAP database for real-time dashboard generation).
- **Frontend Dashboard (V2):** React / Next.js with Tremor/Recharts for time-series data visualization.

## Architectural Thinking & Context

### 1. High Availability Caching & Redis Integration
Because Redis is central to both caching and rate limiting, the system is designed to survive complete Redis failure gracefully.
We use a combination of Resilience4j for circuit breaking (Fail-Fast) and Caffeine for synchronous locking (Stampede protection). 
👉 **Detailed Context:** For a complete understanding of how this operates, please read [`redis_architecture.md`](./redis_architecture.md) in this folder. It covers the Fallback strategies, Lettuce Pooling, and Half-Open Live Testing configurations.

### 2. Analytics Architecture (Event Sourcing vs CDC)
To handle viral traffic, we do **not** write raw click events to PostgreSQL. Instead, the Spring Boot application will shoot raw click events directly into **Kafka** (Direct Publish / Event Sourcing). ClickHouse will then consume these events natively.
We only use CDC (Change Data Capture) if we need to sync core metadata (like URL creations) from PostgreSQL to ClickHouse.

### 3. Graceful Degradation (Rate Limiting)
If the primary Rate Limiting Engine (Redis) crashes, the system is configured to use a **Local In-Memory Fallback** (Fail-Closed globally, but Fail-Open locally via `application.yml` configs). This keeps the system online for legitimate users while still providing best-effort protection against massive DDoS attacks.

## Next Development Steps
We are currently building out the core PostgreSQL Entities and Repositories to support the business logic:
- `Url` (The shortened link data).
- `RateLimitConfig` (The specific algorithm and constraints chosen by the user for a URL).
