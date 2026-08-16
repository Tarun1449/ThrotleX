package com.throttlex.ratelimit.repository;

import com.throttlex.ratelimit.entity.RateLimitAlgorithm;
import com.throttlex.ratelimit.entity.RateLimitConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RateLimitConfigRepository extends JpaRepository<RateLimitConfig, Long> {
    Optional<RateLimitConfig> findByUrlId(Long urlId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RateLimitConfig r SET r.algorithm = :algorithm, r.limitCapacity = :capacity, r.windowSeconds = :window WHERE r.url.id = :urlId")
    int updateConfigByUrlId(@Param("urlId") Long urlId, 
                            @Param("algorithm") RateLimitAlgorithm algorithm, 
                            @Param("capacity") Integer capacity, 
                            @Param("window") Integer window);
}
