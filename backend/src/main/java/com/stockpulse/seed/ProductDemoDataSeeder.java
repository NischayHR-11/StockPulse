package com.stockpulse.seed;

import com.stockpulse.entity.Product;
import com.stockpulse.entity.ProductCategory;
import com.stockpulse.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class ProductDemoDataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(ProductDemoDataSeeder.class);

    private final ProductRepository productRepository;

    public ProductDemoDataSeeder(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        if (productRepository.count() > 0) {
            log.info("Demo product seed skipped because products already exist.");
            return;
        }

        productRepository.saveAll(List.of(
                new Product("SKU-APP-001", "Organic Cotton T-Shirt", ProductCategory.APPAREL,
                        new BigDecimal("24.99"), 8, 15, 12),
                new Product("SKU-ELEC-001", "Wireless Noise-Canceling Headphones", ProductCategory.ELECTRONICS,
                        new BigDecimal("129.99"), 42, 20, 7),
                new Product("SKU-HOME-001", "Stoneware Pour-Over Set", ProductCategory.HOME,
                        new BigDecimal("38.50"), 0, 10, 5)
        ));
        log.info("Inserted demo product catalog.");
    }
}
