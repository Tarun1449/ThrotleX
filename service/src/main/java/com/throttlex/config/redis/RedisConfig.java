package com.throttlex.config.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulConnection;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfig {

    private final RedisProperties redisProperties;

    public RedisConfig(RedisProperties redisProperties) {
        this.redisProperties = redisProperties;
    }

    @Bean
    public LettuceConnectionFactory lettuceConnectionFactory() {
        RedisStandaloneConfiguration standaloneConfiguration = new RedisStandaloneConfiguration();
        standaloneConfiguration.setHostName(redisProperties.host());
        standaloneConfiguration.setPort(redisProperties.port());
        standaloneConfiguration.setDatabase(redisProperties.database());

        if (redisProperties.password() != null && !redisProperties.password().isBlank()) {
            standaloneConfiguration.setPassword(RedisPassword.of(redisProperties.password()));
        }

        LettuceClientConfiguration clientConfiguration = buildClientConfiguration();
        return new LettuceConnectionFactory(standaloneConfiguration, clientConfiguration);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    private LettuceClientConfiguration buildClientConfiguration() {
        RedisProperties.Lettuce lettuce = redisProperties.lettuce();
        RedisProperties.Pool pool = lettuce != null ? lettuce.pool() : null;

        if (pool != null && pool.enabled()) {
            GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig = new GenericObjectPoolConfig<>();
            poolConfig.setMaxTotal(pool.maxActive());
            poolConfig.setMaxIdle(pool.maxIdle());
            poolConfig.setMinIdle(pool.minIdle());
            poolConfig.setMaxWait(pool.maxWait());
            poolConfig.setTimeBetweenEvictionRuns(pool.timeBetweenEvictionRuns());

            LettucePoolingClientConfiguration.LettucePoolingClientConfigurationBuilder builder =
                    LettucePoolingClientConfiguration.builder();
            applyCommonClientSettings(builder);
            builder.poolConfig(poolConfig);
            return builder.build();
        }

        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
                LettuceClientConfiguration.builder();
        return builder.build();
    }

    private void applyCommonClientSettings(LettuceClientConfiguration.LettuceClientConfigurationBuilder builder) {
        builder.commandTimeout(redisProperties.timeout());
        builder.clientOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(redisProperties.connectTimeout())
                        .build())
                .build());

        if (redisProperties.ssl() != null && redisProperties.ssl().enabled()) {
            builder.useSsl();
        }
    }

}
