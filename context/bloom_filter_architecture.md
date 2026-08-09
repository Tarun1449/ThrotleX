# Distributed Bloom Filter Architecture

## The Problem: Cache Penetration Attacks
In a highly-scaled URL shortener, malicious actors (or buggy bots) can attempt a **Cache Penetration Attack**. 
This happens when an attacker requests millions of random, non-existent short codes (e.g., `xyz123`, `abc987`). 
Because these codes don't exist, they will *always* result in a "Cache Miss" in Redis. This forces the application to query the underlying PostgreSQL database for every single request, easily overloading and crashing the database.

## The Solution: Redisson Distributed Bloom Filter
A Bloom Filter is a highly space-efficient probabilistic data structure. It can answer one question with absolute certainty:
- **"Is this item definitely NOT in the set?"**

If the Bloom Filter returns `false`, we know for a fact the short code does not exist. We can instantly return an `HTTP 404 Not Found` without ever touching the Redis cache or PostgreSQL database.

### Implementation Details
We use **Redisson** to implement an `RBloomFilter`. 
Unlike local in-memory Bloom Filters (like Google Guava), Redisson stores the underlying bit-array inside our Redis cluster.

1. **Shared State:** Because the bit-array is in Redis, if we scale the Spring Boot API to 50 instances, they all share the exact same Bloom Filter state instantly.
2. **Persistence:** By enabling AOF (`--appendonly yes`) on our Redis Docker container, the Bloom Filter bit-array survives server restarts. We don't lose our protection if Redis restarts.
3. **Space Efficiency:** We initialized the filter for **100 Million URLs** with a **1% false-positive probability**. This entire structure only consumes roughly **~119 MB** of Redis memory.
4. **Fault Tolerance:** Updating a Bloom Filter is an optimization. If Redis runs out of memory or the connection drops during a `createShortUrl` request, we catch the exception and gracefully continue. The URL is still saved to Postgres, ensuring business logic never fails due to an optimization layer.

### The "False Positive" Caveat
Because of how Bloom Filters work, it is impossible to *remove* an item once it is added. 
If a URL is manually deleted from PostgreSQL, the Bloom Filter will still think it exists. 
This is completely harmless! It simply means the request will bypass the Bloom Filter, get a Cache Miss in Redis, query PostgreSQL, realize it was deleted, and then correctly return a 404. It just costs one database query for that specific deleted URL.
