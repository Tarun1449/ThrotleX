package com.throttlex.urlshortener.repository;

import com.throttlex.urlshortener.entity.BloomFilterOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BloomFilterOutboxRepository extends JpaRepository<BloomFilterOutbox, Long> {


    /**
     * Fetches exactly what is needed (id and shortCode) via an Index-Only Scan.
     * Since the index is (processed, id, shortCode), querying processed=false fetches
     * the exact subset of unprocessed rows natively sorted by ID!
     */
    List<BloomFilterOutboxProjection> findTop1000ByProcessedFalseOrderByIdAsc();

    /**
     * Soft-delete the processed URLs.
     */
    @Modifying
    @Query("UPDATE BloomFilterOutbox o SET o.processed = true WHERE o.id IN :ids")
    void markAsProcessed(@Param("ids") List<Long> ids);
}
