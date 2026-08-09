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
We also employ a **Redisson Distributed Bloom Filter** to completely eliminate Cache Penetration attacks by instantly rejecting invalid short codes without hitting the database.
👉 **Detailed Context:** For a complete understanding of how this operates, please read [`redis_architecture.md`](./redis_architecture.md), [`bloom_filter_architecture.md`](./bloom_filter_architecture.md), and our advanced guide on [`redis_failure_handling.md`](./redis_failure_handling.md) in this folder.

### 2. Analytics Architecture (Event Sourcing vs CDC)
To handle viral traffic, we do **not** write raw click events to PostgreSQL. Instead, the Spring Boot application will shoot raw click events directly into **Kafka** (Direct Publish / Event Sourcing). ClickHouse will then consume these events natively.
We only use CDC (Change Data Capture) if we need to sync core metadata (like URL creations) from PostgreSQL to ClickHouse.

### 3. Graceful Degradation (Rate Limiting)
If the primary Rate Limiting Engine (Redis) crashes, the system is configured to use a **Local In-Memory Fallback** (Fail-Closed globally, but Fail-Open locally via `application.yml` configs). This keeps the system online for legitimate users while still providing best-effort protection against massive DDoS attacks.

### 4. Domain-Driven Design (DDD) Structure
To prevent the monolithic bloat of global `model` and `repository` packages, Throttlex strictly enforces Domain-Driven Design. 
- The `urlshortener` package encapsulates all URL-specific Entities, Repositories, Services, and Controllers.
- The `ratelimit` package encapsulates all rate-limiting specific configurations and algorithms.
This allows for clean microservice extraction in the future if necessary.

### 5. PostgreSQL Partitioning & Snowflake IDs
To handle billions of rows without breaking PostgreSQL's B-Tree indexes, we utilize Date-Range Partitioning. Because standard UUIDs prevent partition pruning, we use a custom **64-bit Snowflake ID Generator** which is then compressed into a Base62 Short Code.
- **Tension-Free Startup:** The Snowflake Node ID is generated autonomously by hashing the container's MAC/IP address, completely bypassing the need for Redis or DevOps configuration during startup.
👉 **Detailed Context:** For a complete breakdown of this math and how it enables $O(1)$ partition pruning, read [`postgres_partitioning_architecture.md`](./postgres_partitioning_architecture.md).

## Next Development Steps
We have successfully completed the foundational layer:
- **Core Entities & Repositories** are strictly encapsulated using DDD.
- **Snowflake & Base62 Utilities** are highly optimized and fully autonomous.
- **Kafka & Redis Configurations** are wired into the infrastructure.

Next, we are building the Business Logic Tier:
- `UrlShortenerService` (Handling Synchronous DB Writes & Collision Retries).
- `UrlShortenerController` (Handling HTTP 201 Creation and HTTP 307 Redirections).
