package com.shopping.product.repository;

import com.shopping.product.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Product entity with custom queries for filtering and search.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    /**
     * Find active products with optional filtering.
     * Uses parameterized queries to prevent SQL injection.
     */
    @Query("SELECT p FROM Product p WHERE p.active = true " +
           "AND (:category IS NULL OR p.category = :category) " +
           "AND (:minPrice IS NULL OR p.price >= :minPrice) " +
           "AND (:maxPrice IS NULL OR p.price <= :maxPrice) " +
           "AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Product> findProductsWithFilters(
        @Param("category") String category,
        @Param("minPrice") BigDecimal minPrice,
        @Param("maxPrice") BigDecimal maxPrice,
        @Param("search") String search,
        Pageable pageable
    );

    Optional<Product> findByIdAndActiveTrue(UUID id);

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.active = true ORDER BY p.category")
    List<String> findDistinctCategories();

    @Query("SELECT p.category, COUNT(p) FROM Product p WHERE p.active = true GROUP BY p.category")
    List<Object[]> countByCategory();

    Page<Product> findByCategoryAndActiveTrue(String category, Pageable pageable);
}
