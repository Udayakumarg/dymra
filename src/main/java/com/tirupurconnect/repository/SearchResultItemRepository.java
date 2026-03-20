package com.tirupurconnect.repository;

import com.tirupurconnect.model.SearchResultItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SearchResultItemRepository extends JpaRepository<SearchResultItem, UUID> {
    List<SearchResultItem> findBySearchLogId(UUID searchLogId);
}
