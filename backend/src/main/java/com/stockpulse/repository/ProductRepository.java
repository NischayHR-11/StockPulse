package com.stockpulse.repository;

import com.stockpulse.entity.Product;
import com.stockpulse.entity.ProductCategory;
import com.stockpulse.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    boolean existsBySkuIgnoreCase(String sku);
    List<Product> findAllByStatusAndCategory(ProductStatus status, ProductCategory category);
    List<Product> findAllByStatus(ProductStatus status);
    List<Product> findAllByCategory(ProductCategory category);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    java.util.Optional<Product> findByIdForUpdate(@Param("id") UUID id);

    @Query("select avg(p.demandVelocity) from Product p where p.category = :category")
    Double averageDemandVelocityByCategory(@Param("category") ProductCategory category);
}
