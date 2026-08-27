CREATE DATABASE IF NOT EXISTS throttlex;

-- 1. Base Raw Ingestion Table
CREATE TABLE IF NOT EXISTS throttlex.url_click_events
(
    id UUID,
    short_code LowCardinality(String),
    original_url String,
    ip_address String,
    user_agent String,
    referer LowCardinality(String),
    country_code LowCardinality(String),
    device_type LowCardinality(String),
    browser LowCardinality(String),
    status_code UInt16,
    decline_reason LowCardinality(String),
    is_bot UInt8,
    created_at DateTime64(3, 'UTC')
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(created_at)
ORDER BY (short_code, toStartOfHour(created_at), ip_address)
TTL created_at + INTERVAL 90 DAY;

-- 2. ClickHouse Native Kafka Engine Queue (Pulls directly from Apache Kafka with 0 Java code!)
CREATE TABLE IF NOT EXISTS throttlex.kafka_click_events_queue
(
    id UUID,
    shortCode String,
    originalUrl String,
    ipAddress String,
    userAgent String,
    referer String,
    countryCode String,
    deviceType String,
    browser String,
    statusCode UInt16,
    declineReason String,
    isBot UInt8,
    createdAt DateTime64(3, 'UTC')
)
ENGINE = Kafka
SETTINGS 
    kafka_broker_list = 'throttlex-kafka:9092',
    kafka_topic_list = 'throttlex-click-events',
    kafka_group_name = 'clickhouse-native-kafka-group-v7',
    kafka_format = 'JSONEachRow';

-- 3. Streaming Materialized View: Pipes Native Kafka Queue into Raw Table url_click_events
CREATE MATERIALIZED VIEW IF NOT EXISTS throttlex.mv_kafka_to_url_click_events
TO throttlex.url_click_events AS
SELECT
    id,
    shortCode AS short_code,
    originalUrl AS original_url,
    ipAddress AS ip_address,
    userAgent AS user_agent,
    referer,
    countryCode AS country_code,
    deviceType AS device_type,
    browser,
    statusCode AS status_code,
    declineReason AS decline_reason,
    isBot AS is_bot,
    createdAt AS created_at
FROM throttlex.kafka_click_events_queue;

-- 4. Hourly Rollup View & Target Table
CREATE TABLE IF NOT EXISTS throttlex.clicks_hourly_target
(
    short_code LowCardinality(String),
    time_grain DateTime,
    total_clicks SimpleAggregateFunction(sum, UInt64),
    unique_visitors AggregateFunction(uniqExact, String)
)
ENGINE = AggregatingMergeTree()
ORDER BY (short_code, time_grain);

CREATE MATERIALIZED VIEW IF NOT EXISTS throttlex.mv_clicks_hourly
TO throttlex.clicks_hourly_target AS
SELECT
    shortCode AS short_code,
    toStartOfHour(createdAt) AS time_grain,
    count() AS total_clicks,
    uniqExactState(ipAddress) AS unique_visitors
FROM throttlex.kafka_click_events_queue
GROUP BY short_code, time_grain;

-- 5. Daily Rollup View & Target Table
CREATE TABLE IF NOT EXISTS throttlex.clicks_daily_target
(
    short_code LowCardinality(String),
    time_grain Date,
    total_clicks SimpleAggregateFunction(sum, UInt64),
    unique_visitors AggregateFunction(uniqExact, String)
)
ENGINE = AggregatingMergeTree()
ORDER BY (short_code, time_grain);

CREATE MATERIALIZED VIEW IF NOT EXISTS throttlex.mv_clicks_daily
TO throttlex.clicks_daily_target AS
SELECT
    shortCode AS short_code,
    toStartOfDay(createdAt) AS time_grain,
    count() AS total_clicks,
    uniqExactState(ipAddress) AS unique_visitors
FROM throttlex.kafka_click_events_queue
GROUP BY short_code, time_grain;

-- 6. Weekly Rollup View & Target Table
CREATE TABLE IF NOT EXISTS throttlex.clicks_weekly_target
(
    short_code LowCardinality(String),
    time_grain Date,
    total_clicks SimpleAggregateFunction(sum, UInt64),
    unique_visitors AggregateFunction(uniqExact, String)
)
ENGINE = AggregatingMergeTree()
ORDER BY (short_code, time_grain);

CREATE MATERIALIZED VIEW IF NOT EXISTS throttlex.mv_clicks_weekly
TO throttlex.clicks_weekly_target AS
SELECT
    shortCode AS short_code,
    toStartOfWeek(createdAt) AS time_grain,
    count() AS total_clicks,
    uniqExactState(ipAddress) AS unique_visitors
FROM throttlex.kafka_click_events_queue
GROUP BY short_code, time_grain;

-- 7. Geographic Rollup View & Target Table
CREATE TABLE IF NOT EXISTS throttlex.clicks_by_country_target
(
    short_code LowCardinality(String),
    country_code LowCardinality(String),
    click_count SimpleAggregateFunction(sum, UInt64)
)
ENGINE = SummingMergeTree()
ORDER BY (short_code, country_code);

CREATE MATERIALIZED VIEW IF NOT EXISTS throttlex.mv_clicks_by_country
TO throttlex.clicks_by_country_target AS
SELECT
    shortCode AS short_code,
    countryCode AS country_code,
    count() AS click_count
FROM throttlex.kafka_click_events_queue
GROUP BY short_code, country_code;

-- 8. Referral Channel Rollup View & Target Table
CREATE TABLE IF NOT EXISTS throttlex.clicks_by_referrer_target
(
    short_code LowCardinality(String),
    referer LowCardinality(String),
    click_count SimpleAggregateFunction(sum, UInt64)
)
ENGINE = SummingMergeTree()
ORDER BY (short_code, referer);

CREATE MATERIALIZED VIEW IF NOT EXISTS throttlex.mv_clicks_by_referrer
TO throttlex.clicks_by_referrer_target AS
SELECT
    shortCode AS short_code,
    referer,
    count() AS click_count
FROM throttlex.kafka_click_events_queue
GROUP BY short_code, referer;

-- 9. Device & Browser Rollup View & Target Table
CREATE TABLE IF NOT EXISTS throttlex.clicks_by_device_target
(
    short_code LowCardinality(String),
    device_type LowCardinality(String),
    browser LowCardinality(String),
    click_count SimpleAggregateFunction(sum, UInt64)
)
ENGINE = SummingMergeTree()
ORDER BY (short_code, device_type, browser);

CREATE MATERIALIZED VIEW IF NOT EXISTS throttlex.mv_clicks_by_device
TO throttlex.clicks_by_device_target AS
SELECT
    shortCode AS short_code,
    deviceType AS device_type,
    browser,
    count() AS click_count
FROM throttlex.kafka_click_events_queue
GROUP BY short_code, device_type, browser;

-- 10. Request Decline Reason Rollup View & Target Table
CREATE TABLE IF NOT EXISTS throttlex.clicks_declined_target
(
    short_code LowCardinality(String),
    decline_reason LowCardinality(String),
    declined_count SimpleAggregateFunction(sum, UInt64)
)
ENGINE = SummingMergeTree()
ORDER BY (short_code, decline_reason);

CREATE MATERIALIZED VIEW IF NOT EXISTS throttlex.mv_clicks_declined
TO throttlex.clicks_declined_target AS
SELECT
    shortCode AS short_code,
    declineReason AS decline_reason,
    count() AS declined_count
FROM throttlex.kafka_click_events_queue
WHERE statusCode != 307
GROUP BY short_code, decline_reason;

