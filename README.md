# ThrottleX

ThrottleX is an enterprise-grade, high-throughput, and resilient **Distributed URL Shortener**. Built with modern backend engineering principles, it goes beyond simple CRUD operations to tackle challenges like cache penetration, race conditions in distributed environments, and real-time streaming analytics.

---

## 🚀 Key Features & Architecture Diagrams

### 1. Basic System Overview
This diagram shows the complete lifecycle of a URL Redirection request, from the user clicking the link to the eventual analytics ingestion in ClickHouse.

```mermaid
graph TD
    Client([Client]) -->|GET /shortCode| Interceptor{RateLimitInterceptor}
    
    Interceptor -->|Blocked| 429[429 Too Many Requests]
    Interceptor -->|Allowed| API[UrlShortenerController]
    
    API --> Bloom{Bloom Filter}
    Bloom -->|Not Found| 404[404 Not Found]
    Bloom -->|Might Exist| Cache[(Redis Read-Through)]
    
    Cache -->|Cache Miss| DB[(PostgreSQL)]
    DB --> Cache
    
    Cache -->|Redirect URL| API
    API -->|307 Redirect| Client
    
    API -.->|Async Click Event| Kafka[Apache Kafka]
    Kafka -.->|Batch Ingest| ClickHouse[(ClickHouse OLAP)]
```

### 2. Cache Penetration Protection (Distributed Bloom Filter)
Prevents malicious users from spamming the database with non-existent short codes. Uses a Redis-backed `RBloomFilter`, with additions asynchronously synced via an event-driven Kafka pipeline to decouple API latency.

```mermaid
sequenceDiagram
    participant Client
    participant API as URL Controller
    participant Kafka as Kafka Topic
    participant Consumer as Sync Consumer
    participant Redis as Redis Bloom Filter
    
    Client->>API: POST /api/v1/urls
    API->>Database: Save URL
    API->>Kafka: Publish [BloomFilterSyncEvent]
    API-->>Client: 201 Created (Fast Return)
    
    Note over Kafka,Redis: Asynchronous Background Sync
    Kafka->>Consumer: Consume Event
    Consumer->>Redis: bloomFilter.add(shortCode)
```

### 3. Atomic Distributed Rate Limiting
A custom Token Bucket rate limiter engineered using a **Redis Lua Script** to ensure lock-free, race-condition-free token consumption. Limits are dynamic (per URL) and fetched from PostgreSQL with aggressive Redis caching.

```mermaid
graph LR
    Req[Incoming Request] --> Interceptor[RateLimitInterceptor]
    Interceptor --> CacheCheck{Redis Cache}
    
    CacheCheck -->|Config Missing| DB[(Postgres config)]
    DB --> CacheCheck
    
    CacheCheck -->|Config Found| Lua[Redis Lua Script]
    
    Lua --> Math[Lazy Refill Math]
    Math --> Decide{Tokens > 0?}
    
    Decide -->|Yes| Allow[Consume 1 Token & Allow]
    Decide -->|No| Block[Return 429]
```

### 4. Fail-Open Circuit Breakers & Outbox Pattern
Wraps critical infrastructure calls (like Redis) in `resilience4j` circuit breakers. If the caching layer fails, the application gracefully degrades (Fail-Open) to ensure 100% API availability for URL redirections.

```mermaid
stateDiagram-v2
    [*] --> Redis_Healthy: Normal Operation
    
    state Redis_Healthy {
        BloomFilter --> Redis
        RateLimiter --> Redis
    }
    
    Redis_Healthy --> Redis_Down: Connection Timeout
    
    state Redis_Down {
        CircuitBreaker --> OPEN
        RateLimiter --> FailOpen(Allow)
        CreationAPI --> Outbox(Save to Postgres Outbox)
    }
    
    Redis_Down --> Recovery: Redis Restored
    
    state Recovery {
        CircuitBreaker --> CLOSED
        Outbox --> KafkaSync(Publish Outbox to Kafka)
    }
    
    Recovery --> Redis_Healthy
```

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
