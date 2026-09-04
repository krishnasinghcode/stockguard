package com.stockguard.concurrency;

import com.stockguard.dto.ReservationRequest;
import com.stockguard.entity.Inventory;
import com.stockguard.repository.InventoryRepository;
import com.stockguard.service.ReservationService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fires 50 concurrent reservations at a single product with 10 units in
 * stock and asserts that exactly 10 succeed, the rest are rejected, and
 * available_qty never goes negative.
 */
@Testcontainers
@SpringBootTest
class ConcurrentReservationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("stockguard")
            .withUsername("stockguard")
            .withPassword("stockguard");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private InventoryRepository inventoryRepository;

    private static final Long PRODUCT_ID = 1L;
    private static final int STARTING_STOCK = 10;
    private static final int CONCURRENT_REQUESTS = 50;

    @BeforeEach
    void seedInventory() {
        inventoryRepository.save(new Inventory(PRODUCT_ID, STARTING_STOCK));
    }

    @AfterEach
    void cleanup() {
        inventoryRepository.deleteAll();
    }

    @Test
    void exactlyTenSucceed_fortyRejected_stockNeverNegative()
            throws InterruptedException, ExecutionException {

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);

        CountDownLatch startGate = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger rejectedCount = new AtomicInteger();

        List<Callable<Void>> tasks =
                java.util.stream.IntStream.range(0, CONCURRENT_REQUESTS)
                        .mapToObj(i -> (Callable<Void>) () -> {
                            startGate.await();

                            try {
                                reservationService.reserve(
                                        new ReservationRequest(
                                                PRODUCT_ID,
                                                "user-" + i,
                                                1
                                        ),
                                        "idem-key-" + i
                                );

                                successCount.incrementAndGet();

                            } catch (Exception ex) {
                                rejectedCount.incrementAndGet();
                            }

                            return null;
                        })
                        .toList();

        List<Future<Void>> futures =
                tasks.stream()
                        .map(pool::submit)
                        .toList();

        startGate.countDown();

        for (Future<Void> f : futures) {
            f.get();
        }

        pool.shutdown();

        Inventory finalInventory =
                inventoryRepository.findByProductId(PRODUCT_ID)
                        .orElseThrow();

        assertEquals(
                STARTING_STOCK,
                successCount.get(),
                "exactly starting stock should succeed"
        );

        assertEquals(
                CONCURRENT_REQUESTS - STARTING_STOCK,
                rejectedCount.get(),
                "the rest should be rejected"
        );

        assertEquals(
                0,
                finalInventory.getAvailableQty(),
                "no stock left"
        );

        assertTrue(
                finalInventory.getAvailableQty() >= 0,
                "stock must never go negative"
        );
    }
}