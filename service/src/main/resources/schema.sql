-- PostgreSQL DDL Script for Initializing Partitioned Tables
-- Run this script on a fresh database to set up the partitioning architecture correctly.

-- 1. Create the `urls` parent table
CREATE TABLE IF NOT EXISTS urls (
    id BIGINT NOT NULL,
    short_code VARCHAR(20) NOT NULL,
    original_url VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (id)
) PARTITION BY RANGE (id);

-- Create default partitions to catch all Snowflake IDs
CREATE TABLE IF NOT EXISTS urls_default PARTITION OF urls DEFAULT;

-- Create a standard index on short_code (Cannot be a UNIQUE constraint globally across partitions without the partition key)
CREATE INDEX IF NOT EXISTS idx_url_short_code ON urls (short_code);


-- 2. Create the `bloom_filter_outbox` parent table
CREATE TABLE IF NOT EXISTS bloom_filter_outbox (
    id BIGINT NOT NULL,
    short_code VARCHAR(10) NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (id)
) PARTITION BY RANGE (id);

-- Create default partition for outbox
CREATE TABLE IF NOT EXISTS bloom_filter_outbox_default PARTITION OF bloom_filter_outbox DEFAULT;

-- The highly optimized partial index for background processing
CREATE INDEX IF NOT EXISTS idx_outbox_unprocessed ON bloom_filter_outbox (id, short_code) WHERE processed = false;


-- 3. Create the standard `rate_limit_configs` table (Not partitioned)
CREATE TABLE IF NOT EXISTS rate_limit_configs (
    id BIGSERIAL PRIMARY KEY,
    url_id BIGINT NOT NULL,
    algorithm VARCHAR(255) NOT NULL,
    limit_capacity INT NOT NULL,
    window_seconds INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT fk_rate_limit_url FOREIGN KEY (url_id) REFERENCES urls(id) ON DELETE CASCADE
);
