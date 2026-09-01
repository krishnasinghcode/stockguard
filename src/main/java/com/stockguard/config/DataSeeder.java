package com.stockguard.config;

import com.stockguard.dto.ProductRequest;
import com.stockguard.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final ProductService productService;

    @Override
    public void run(String... args) {

        if (productService.countProducts() == 0) {

            productService.createProduct(
                new ProductRequest(
                    "Limited Edition Sneaker",
                    new BigDecimal("129.99"),
                    10
                )
            );

            productService.createProduct(
                new ProductRequest(
                    "Everyday T-Shirt",
                    new BigDecimal("19.99"),
                    500
                )
            );

            log.info("Seed data loaded.");

        } else {
            log.info("Seed data already exists. Skipping.");
        }
    }
}