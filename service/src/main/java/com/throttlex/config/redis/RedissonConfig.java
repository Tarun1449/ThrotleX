package com.throttlex.config.redis;

import org.redisson.Redisson;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        try {
            Config config = new Config();
            String prefix = redisProperties.ssl() != null && redisProperties.ssl().enabled() ? "rediss://" : "redis://";
            String address = prefix + redisProperties.host() + ":" + redisProperties.port();

            config.useSingleServer()
                    .setAddress(address)
                    .setDatabase(redisProperties.database());

            if (redisProperties.password() != null && !redisProperties.password().isBlank()) {
                config.useSingleServer().setPassword(redisProperties.password());
            }

            return Redisson.create(config);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(RedissonConfig.class)
                    .warn("[RedissonConfig] Failed to initialize RedissonClient on startup (Redis down?). Booting in Fail-Open mode.", e);
            return null;
        }
    }

    /**
     * Initializes the Distributed Bloom Filter.
     * We expect 100 Million short codes, and aim for a 1% false positive rate.
     * The underlying bit array is stored in Redis, so it is shared across all API instances
     * and persists through server restarts (because we enabled AOF in docker-compose).
     */
    @Bean
    public RBloomFilter<String> urlBloomFilter(RedissonClient redissonClient) {
        if (redissonClient == null) {
            org.slf4j.LoggerFactory.getLogger(RedissonConfig.class)
                    .warn("[RedissonConfig] RedissonClient is null (Redis down?). Bloom filter operating in Fail-Open mode.");
            return null;
        }

        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("urlBloomFilter");
        
        try {
            // Only initialize if it doesn't already exist in Redis
            if (!bloomFilter.isExists()) {
                // expectedInsertions = 100,000,000, falseProbability = 0.01 (1%)
                bloomFilter.tryInit(100_000_000L, 0.01);
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(RedissonConfig.class)
                    .warn("[RedissonConfig] Could not initialize Bloom Filter on startup (Redis down?). Application will boot safely in Fail-Open mode.");
        }
        
        return bloomFilter;
    }
}
