package com.example.saga.inventory.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StockItemRepository extends JpaRepository<StockItem, String> {

    /**
     * Loads and row-locks the given SKUs. Always locking in SKU order prevents deadlocks between
     * concurrent reservations that touch overlapping SKUs.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockItem s where s.sku in :skus order by s.sku")
    List<StockItem> findAllForUpdate(@Param("skus") Collection<String> skus);
}
