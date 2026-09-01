package com.stockguard.repository;

import com.stockguard.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    // Plain read, no locking.
    Optional<Inventory> findByProductId(Long productId);

    /**
     * SELECT ... FOR UPDATE. Acquires a row-level lock on the inventory
     * row so concurrent reservations for the same product serialize
     * instead of both reading a stale available_qty.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.productId = :productId")
    Optional<Inventory> lockByProductId(@Param("productId") Long productId);
}