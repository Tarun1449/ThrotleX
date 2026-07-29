# Redis Cache Architecture & High Availability Design

This document outlines the production-grade caching architecture implemented in Throttlex. It is designed to handle massive traffic spikes (Thundering Herd) and tolerate complete Redis failure without bringing down the PostgreSQL database or the application layer.

## 1. The Core Problem
In a high-throughput read-heavy system like a URL shortener, if a highly popular link expires from the cache or Redis crashes, thousands of concurrent requests will suddenly hit the database for the exact same URL. This is known as a **Cache Stampede** or **Thundering Herd**. 

Furthermore, if Redis is down, waiting for the TCP connection timeout on every single request will cause all application threads to block, leading to cascading failure.

## 2. Our Architecture Solution

We solved this using a custom `SyncFallbackCache` which combines three powerful tools: **Caffeine (L1)**, **Redis (L2)**, and **Resilience4j (Circuit Breaker)**.

### A. The Caffeine Cache (Stampede Protector)
We use a local, in-memory Caffeine cache with a very short TTL (e.g., 500ms). However, its primary purpose is *not* normal caching. It is used as a **Synchronous Loader Lock**.

```java
caffeineCache.get(key, () -> valueLoader.call())
```
**How it works:**
1. 10,000 requests come in for short-code `XYZ`.
2. Caffeine sees `XYZ` is not in memory.
3. Caffeine **locks** the key `XYZ` for all 10,000 threads.
4. Only **one thread** is allowed to execute the `valueLoader` (which calls Redis, and if that fails, calls Postgres).
5. Once the single thread gets the result, Caffeine unlocks and hands the result to all 10,000 threads simultaneously.

*Result:* The database only receives **1 query** instead of 10,000.

### B. The Resilience4j Circuit Breaker (Fail-Fast Mechanism)
Instead of waiting for a background ping to tell us Redis is down, we monitor real traffic. 

Every Redis read/write is wrapped in a `circuitBreaker.executeSupplier(...)`.
* **Closed State (Healthy):** Requests go to Redis normally.
* **Open State (Failing):** If the failure rate crosses our threshold (e.g., 50% in the last 50 calls), the circuit "opens". Subsequent requests throw a `CallNotPermittedException` *instantly* without touching the network. This eliminates connection timeout latency, allowing the app to fail-fast to the database (which is safely protected by Caffeine).
* **Half-Open (Real Traffic Testing):** Rather than using a background CRON job to check if Redis is back online, the circuit waits for a configured duration (e.g., 10 seconds). It then transitions to `HALF_OPEN` and allows a small number of *real user requests* (e.g., 5) to pass through to Redis. 
  * If the real requests succeed, the circuit closes (recovered).
  * If the real requests fail, the circuit opens again (still failing).

### C. Lettuce Connection Pooling
We use `LettucePoolingClientConfiguration` with a `GenericObjectPoolConfig`. This ensures we maintain a healthy pool of persistent TCP connections to Redis, rather than spending expensive CPU cycles opening and closing sockets for every request.

## 3. The Execution Flow

When a user requests a URL:
1. Hit `SyncFallbackCache`.
2. Wrap the request in the Caffeine synchronous lock.
3. Check Resilience4j Circuit Breaker.
    * If Open: Skip Redis, fetch from PostgreSQL, return.
    * If Closed: Attempt Redis fetch.
        * If Hit: Return value.
        * If Miss or Exception: Fetch from PostgreSQL, attempt to save to Redis asynchronously, return.

## 4. Rate Limiting Fallback Strategy
If Redis crashes, our centralized Rate Limiter goes down. To handle this, we implement the **Local Fallback (Graceful Degradation)** strategy.

* **Fail-Closed by Default:** We do not want to "Fail Open" and allow a DDoS attack to crush our PostgreSQL database if Redis is down.
* **Local In-Memory Limiting:** If the circuit breaker opens, we catch the exception and immediately switch to a **Local In-Memory Rate Limiter** running directly on the Spring Boot server (configured in `application.yml`). 
* **The Result:** We maintain high availability so legitimate users can still click shortened URLs, while still offering "best-effort" protection against malicious traffic spikes until Redis recovers.

## Conclusion
This architecture guarantees that the application can sustain millions of reads with predictable latency, seamlessly surviving both massive viral traffic spikes and catastrophic Redis infrastructure outages.
