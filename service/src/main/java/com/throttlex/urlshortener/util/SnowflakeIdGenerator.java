epackage com.throttlex.urlshortener.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SnowflakeIdGenerator {

    // Custom Epoch: Jan 1, 2026 00:00:00 UTC (1767225600000L)
    // Using a custom epoch allows the timestamp to fit in 41 bits for a longer time.
    private static final long CUSTOM_EPOCH = 1767225600000L;

    // Bit lengths
    private static final int NODE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;

    // Max values (Bit masks)
    private static final long MAX_NODE_ID = ~(-1L << NODE_ID_BITS);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    // Shifts
    private static final int NODE_ID_SHIFT = SEQUENCE_BITS;
    private static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + NODE_ID_BITS;

    private final long nodeId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    /**
     * Initializes the generator. Node ID can be passed from application.yml
     * Defaulting to 1 if not provided, but in a real distributed system,
     * every instance MUST have a unique NODE_ID.
     */
    public SnowflakeIdGenerator(@Value("${throttlex.node-id:1}") long nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException(String.format("Node ID must be between 0 and %d", MAX_NODE_ID));
        }
        this.nodeId = nodeId;
    }

    /**
     * Generates a new unique Snowflake ID. Thread-safe.
     */
    public synchronized long nextId() {
        long currentTimestamp = timestamp();

        if (currentTimestamp < lastTimestamp) {
            throw new IllegalStateException("Clock moved backwards. Refusing to generate id for " + (lastTimestamp - currentTimestamp) + " milliseconds.");
        }

        if (currentTimestamp == lastTimestamp) {
            // Same millisecond, increment sequence
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // Sequence overflow, wait for the next millisecond
                currentTimestamp = waitNextMillis(currentTimestamp);
            }
        } else {
            // New millisecond, reset sequence
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        // Pack the bits together
        return ((currentTimestamp - CUSTOM_EPOCH) << TIMESTAMP_SHIFT)
                | (nodeId << NODE_ID_SHIFT)
                | sequence;
    }

    /**
     * Utility method to extract the Epoch Milliseconds from a generated Snowflake ID.
     * This is used by the PostgreSQL Partitioning routing logic!
     */
    public static long extractTimestamp(long snowflakeId) {
        return (snowflakeId >> TIMESTAMP_SHIFT) + CUSTOM_EPOCH;
    }

    /**
     * Utility method to extract an Instant from a generated Snowflake ID.
     */
    public static Instant extractInstant(long snowflakeId) {
        return Instant.ofEpochMilli(extractTimestamp(snowflakeId));
    }

    private long timestamp() {
        return System.currentTimeMillis();
    }

    private long waitNextMillis(long currentTimestamp) {
        while (currentTimestamp <= lastTimestamp) {
            currentTimestamp = timestamp();
        }
        return currentTimestamp;
    }
}
