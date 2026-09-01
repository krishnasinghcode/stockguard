package com.stockguard.service;

import com.stockguard.dto.ProductRequest;
import com.stockguard.dto.ProductResponse;
import com.stockguard.entity.Inventory;
import com.stockguard.entity.Product;
import com.stockguard.repository.InventoryRepository;
import com.stockguard.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String CACHE_PREFIX = "product:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {

        Product product = new Product();
        product.setName(request.name());
        product.setPrice(request.price());

        productRepository.save(product);

        Inventory inventory = new Inventory(
                product.getId(),
                request.initialStock()
        );
        inventoryRepository.save(inventory);

        // New product => stale cache can't exist yet, but evict defensively
        // in case an id gets reused in tests.
        redisTemplate.delete(CACHE_PREFIX + product.getId());

        return toResponse(product, inventory);
    }

    public long countProducts() {
        return productRepository.count();
    }

    /**
     * Cache-aside pattern, done manually so the hit/miss timing is visible
     * in logs — this is what you screenshot for the "Redis cache" resume
     * line and what you explain in the interview.
     */
    public ProductResponse getProduct(Long id) {

        String key = CACHE_PREFIX + id;
        long start = System.nanoTime();

        ProductResponse cached =
                (ProductResponse) redisTemplate.opsForValue().get(key);

        if (cached != null) {
            log.info(
                    "CACHE HIT product={} took={}ms",
                    id,
                    elapsedMs(start)
            );
            return cached;
        }

        log.info("CACHE MISS product={}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Product not found: " + id
                        )
                );

        Inventory inventory = inventoryRepository.findByProductId(id)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Inventory missing for product: " + id
                        )
                );

        ProductResponse response = toResponse(product, inventory);

        redisTemplate.opsForValue().set(
                key,
                response,
                CACHE_TTL.toSeconds(),
                TimeUnit.SECONDS
        );

        log.info(
                "DB READ product={} took={}ms (cached for next call)",
                id,
                elapsedMs(start)
        );

        return response;
    }

    // Called by ReservationService after a successful reservation so the
    // cached stock numbers don't go stale.
    public void evict(Long productId) {
        redisTemplate.delete(CACHE_PREFIX + productId);
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private ProductResponse toResponse(Product p, Inventory inv) {
        return new ProductResponse(
                p.getId(),
                p.getName(),
                p.getPrice(),
                inv.getAvailableQty(),
                inv.getReservedQty()
        );
    }
}