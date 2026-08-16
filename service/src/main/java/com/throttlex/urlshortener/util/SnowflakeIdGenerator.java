package com.throttlex.urlshortener.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Enumeration;

@Slf4j
@Component
public class SnowflakeIdGenerator {

    // Custom Epoch: Jan 1, 2026 00:00:00 UTC
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

    public SnowflakeIdGenerator() {
        this.nodeId = generateNodeId();
        log.info("SnowflakeIdGenerator initialized with Node ID: {}", this.nodeId);
    }

    /**
     * Tension-Free Node ID Generation!
     * Attempts to get the MAC address. If it fails, falls back to the IP address.
     * If that fails, falls back to a SecureRandom number.
     * No YAML config or root access needed.
     */
    private long generateNodeId() {
        long id;
        try {
            InetAddress ip = InetAddress.getLocalHost();
            NetworkInterface network = NetworkInterface.getByInetAddress(ip);
            
            if (network != null && network.getHardwareAddress() != null) {
                // Use MAC Address
                byte[] mac = network.getHardwareAddress();
                id = ((0x000000FF & (long) mac[mac.length - 2]) | (0x0000FF00 & (((long) mac[mac.length - 1]) << 8))) >> 6;
            } else {
                // Fallback to IP Address Hash
                id = Math.abs(ip.getHostAddress().hashCode());
            }
        } catch (Exception e) {
            log.warn("Could not determine MAC/IP Address for Node ID. Falling back to SecureRandom.", e);
            id = new SecureRandom().nextInt((int) MAX_NODE_ID + 1);
        }
        return id & MAX_NODE_ID;
    }

    public synchronized long nextId() {
        long currentTimestamp = timestamp();

        if (currentTimestamp < lastTimestamp) {
            throw new IllegalStateException("Clock moved backwards.");
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                currentTimestamp = waitNextMillis(currentTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - CUSTOM_EPOCH) << TIMESTAMP_SHIFT)
                | (nodeId << NODE_ID_SHIFT)
                | sequence;
    }

    public static long extractTimestamp(long snowflakeId) {
        return (snowflakeId >> TIMESTAMP_SHIFT) + CUSTOM_EPOCH;
    }

    public static Instant extractInstant(long snowflakeId) {
        return Instant.ofEpochMilli(extractTimestamp(snowflakeId));
    }

    public static long getLowerBoundForTimestamp(long timestampMillis) {
        if (timestampMillis < CUSTOM_EPOCH) {
            return 0L;
        }
        return (timestampMillis - CUSTOM_EPOCH) << TIMESTAMP_SHIFT;
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
