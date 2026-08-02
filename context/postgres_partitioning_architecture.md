# PostgreSQL Partitioning Architecture (URL Shortener)

## The Core Problem: Partition Pruning Failure
In a massive-scale URL shortener, the `urls` table will eventually grow to billions of rows. If left as a single table, the B-Tree index becomes too large for RAM (Shared Buffers), causing massive disk I/O spikes and making Auto-Vacuum impossible. 

Partitioning the table by Date (e.g., one partition per month) solves the archival problem, but introduces a new fatal flaw: **Partition Pruning Failure**. 
When a user clicks a short code (e.g., `SELECT * FROM urls WHERE short_code = 'XYZ'`), PostgreSQL doesn't know which month the URL was created in. It is forced to scan the index of every single partition, destroying read performance.

## The Solution: Snowflake IDs + Base62 Encoding
To achieve perfect Date-Range Partitioning while maintaining $O(1)$ partition pruning, Throttlex uses a Snowflake ID architecture combined with Base62 Encoding.

### 1. ID Generation (Write Path)
Instead of using random strings or `UUIDs`, the Primary Key for every URL is a **64-bit Snowflake ID (`Long`)**.
The Snowflake ID is constructed using bitwise operations:
- **41 Bits:** Epoch Timestamp in Milliseconds (Custom Epoch: Jan 1, 2026).
- **10 Bits:** Node/Machine ID (Prevents collisions across distributed instances).
- **12 Bits:** Sequence Number (Handles sub-millisecond concurrency).

This 64-bit integer is then encoded into a **Base62 String**. 
The Base62 String becomes the actual Short URL (e.g., `Xy7bA2`).

### 2. Real-Time Partition Selection (Read Path)
When an HTTP request comes in for a Short URL:
1. The Java application decodes the Base62 String back into the original 64-bit Snowflake `Long`.
2. The application performs a **Bitwise Right Shift (`>> 22`)** to strip away the Sequence and Node ID, extracting the exact Epoch Timestamp (in milliseconds).
3. The timestamp is converted into a standard Date/Month.
4. The application queries PostgreSQL with both the ID and the exact Date bounds:
   ```sql
   SELECT * FROM urls 
   WHERE id = 123456789 
   AND created_at >= '2026-01-01' 
   AND created_at < '2026-02-01';
   ```
5. **Result:** PostgreSQL uses flawless Partition Pruning. It ignores all other partitions and instantly fetches the record from the January 2026 partition index (which is hot in RAM).

## Benefits
- **Zero Collisions:** Unlike random strings, Snowflake IDs are guaranteed globally unique.
- **Lightning Fast Reads:** $O(1)$ partition lookups regardless of how many billions of rows exist.
- **Zero-Downtime Archival:** Old URLs can be instantly deleted by dropping the old partition (`DROP TABLE urls_2026_01`), bypassing the WAL and preventing dead tuples.
