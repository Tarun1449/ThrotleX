package com.throttlex.urlshortener.repository;

import com.throttlex.urlshortener.entity.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {
    // We query by ID and the Start/End of the month (IST) to perfectly prune PostgreSQL partitions
    Optional<UrlProjection> findByIdAndCreatedAtBetween(Long id, Instant start, Instant end);

    // Cursor-based pagination
    List<Url> findByOrderByIdDesc(org.springframework.data.domain.Pageable pageable);
    List<Url> findByIdLessThanOrderByIdDesc(Long id, org.springframework.data.domain.Pageable pageable);
}
