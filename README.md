# ThrottleX

ThrottleX is an enterprise-grade, high-throughput, and resilient **Distributed URL Shortener**. Built with modern backend engineering principles, it goes beyond simple CRUD operations to tackle challenges like cache penetration, race conditions in distributed environments, and real-time streaming analytics.

---

## 🚀 Key Features

*   **Cache Penetration Protection (Distributed Bloom Filter):** Prevents malicious users from spamming the database with non-existent short codes. Uses a Redis-backed `RBloomFilter`, with additions asynchronously synced via an event-driven Kafka pipeline to decouple API latency.
*   **Atomic Distributed Rate Limiting:** A custom Token Bucket rate limiter engineered using a **Redis Lua Script** to ensure lock-free, race-condition-free token consumption. Limits are dynamic (per URL) and fetched from PostgreSQL with aggressive Redis caching.
*   **Fail-Open Circuit Breakers:** Wraps critical infrastructure calls (like Redis) in `resilience4j` circuit breakers. If the caching layer fails, the application gracefully degrades (Fail-Open) to ensure 100% API availability for URL redirections.
*   **Streaming Analytics Pipeline (In Progress):** Instead of writing click analytics synchronously to a database, redirection events are published to **Apache Kafka**. These streams will be batch-ingested into **ClickHouse** for real-time OLAP (Online Analytical Processing) capabilities.
*   **Database Optimization:** PostgreSQL tables are tuned with custom `FILLFACTOR` settings to optimize HOT (Heap-Only Tuple) updates, preventing expensive page-splits under heavy write loads.
*   **Snowflake ID Generation:** Uses a decentralized Snowflake ID generator encoded into Base62 for high-speed, collision-free short code generation.

---

## 🛠️ Tech Stack

*   **Language:** Java 21
*   **Framework:** Spring Boot 3.x
*   **Database:** PostgreSQL (with manual Fillfactor tuning)
*   **Distributed Cache:** Redis (Lettuce for standard caching, Redisson for advanced structures)
*   **Message Broker:** Apache Kafka (Event-driven tasks and streaming)
*   **OLAP Database:** ClickHouse (Coming soon)
*   **Resiliency:** Resilience4j (Circuit Breakers)

---

## 🏗️ Architecture & Design Patterns

### 1. Interceptor & Strategy Patterns
Rate limiting logic is completely decoupled from the core business controllers. 
*   A Spring `HandlerInterceptor` targets specific API paths (e.g., `GET` redirections).
*   The `RateLimitStrategy` interface and `RateLimiterFactory` allow seamless swapping of throttling algorithms (Token Bucket, Fixed Window) without modifying the interceptor.

### 2. Event-Driven Task Queue
Instead of introducing complex external task queues (like Celery), **Kafka** acts as the backbone for background processing. 
*   Example: The `BloomFilterSyncConsumer` listens to Kafka topics to safely update the Redis Bloom Filter in the background, keeping the primary API response times under 10ms.

### 3. Lazy-Refill Math (Lua)
To prevent crushing the server with background threads trying to refill millions of rate-limit buckets every second, the Token Bucket Lua script uses "Lazy Refill" math. It calculates elapsed time and instantly drops tokens into the bucket *only* at the exact millisecond a user makes a request.

---

## 🚧 Roadmap

- [x] Snowflake Base62 URL Generation
- [x] Redis Read-Through Caching
- [x] Distributed Bloom Filter (Cache Penetration Defense)
- [x] Lua-Scripted Token Bucket Rate Limiting (Dynamic config)
- [ ] Kafka Analytics Producer (Streaming click events)
- [ ] ClickHouse OLAP Integration for real-time dashboards
- [ ] Prometheus & Grafana Observability stack
