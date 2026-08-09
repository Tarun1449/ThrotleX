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
    }

    /**
     * Initializes the Distributed Bloom Filter.
     * We expect 100 Million short codes, and aim for a 1% false positive rate.
     * The underlying bit array is stored in Redis, so it is shared across all API instances
     * and persists through server restarts (because we enabled AOF in docker-compose).
     */
    @Bean
    public RBloomFilter<String> urlBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("urlBloomFilter");
        
        // Only initialize if it doesn't already exist in Redis
        if (!bloomFilter.isExists()) {
            // expectedInsertions = 100,000,000, falseProbability = 0.01 (1%)
            bloomFilter.tryInit(100_000_000L, 0.01);
        }
        
        return bloomFilter;
    }
}
