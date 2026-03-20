package com.tirupurconnect.repository;

import com.tirupurconnect.model.SearchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SearchLogRepository extends JpaRepository<SearchLog, UUID> {

    @Query("SELECT s.queryText, COUNT(s) FROM SearchLog s WHERE s.tenantId = :tid AND s.zeroResult = true AND s.searchedAt >= :since GROUP BY s.queryText ORDER BY COUNT(s) DESC")
    List<Object[]> findTopZeroResultQueries(@Param("tid") String tenantId, @Param("since") Instant since);
}
