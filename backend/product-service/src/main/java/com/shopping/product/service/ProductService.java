package com.shopping.product.service;

import com.shopping.product.dto.CreateProductRequest;
import com.shopping.product.dto.ProductResponse;
import com.shopping.product.dto.UpdateProductRequest;
import com.shopping.product.exception.ProductNotFoundException;
import com.shopping.product.exception.DuplicateSkuException;
import com.shopping.product.model.Product;
import com.shopping.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for product operations.
 * Implements business logic and maps between entities and DTOs.
 */
@Service
@Transactional(readOnly = true)
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Get paginated and filtered list of products.
     */
    public Page<ProductResponse> getProducts(
            int page, int size, String category,
            BigDecimal minPrice, BigDecimal maxPrice,
            String search, String sortBy, String sortDir) {

        log.info("Fetching products: page={}, size={}, category={}, search={}", page, size, category, search);

        Sort sort = Sort.by(
            "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC,
            mapSortField(sortBy)
        );

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> products = productRepository.findProductsWithFilters(
            category, minPrice, maxPrice, search, pageable
        );

        return products.map(this::toResponse);
    }

    /**
     * Get a single product by ID.
     *
     * @throws ProductNotFoundException if product not found or inactive
     */
    public ProductResponse getProductById(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Product ID must not be null");
        }

        log.info("Fetching product: id={}", id);

        Product product = productRepository.findByIdAndActiveTrue(id)
            .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));

        return toResponse(product);
    }

    /**
     * Create a new product.
     *
     * @throws DuplicateSkuException if SKU already exists
     */
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Create product request must not be null");
        }

        log.info("Creating product: name={}, sku={}", request.name(), request.sku());

        // Check for duplicate SKU
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateSkuException("Product with SKU '" + request.sku() + "' already exists");
        }

        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCategory(request.category());
        product.setImageUrl(request.imageUrl());
        product.setStock(request.stock());
        product.setSku(request.sku());
        product.setActive(true);

        Product saved = productRepository.save(product);
        log.info("Product created: id={}", saved.getId());

        return toResponse(saved);
    }

    /**
     * Update an existing product. Only non-null fields are applied.
     *
     * @throws ProductNotFoundException if product not found
     */
    @Transactional
    public ProductResponse updateProduct(UUID id, UpdateProductRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("Product ID must not be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Update product request must not be null");
        }

        log.info("Updating product: id={}", id);

        Product product = productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));

        // Apply only non-null fields (partial update)
        if (request.name() != null) product.setName(request.name());
        if (request.description() != null) product.setDescription(request.description());
        if (request.price() != null) product.setPrice(request.price());
        if (request.category() != null) product.setCategory(request.category());
        if (request.imageUrl() != null) product.setImageUrl(request.imageUrl());
        if (request.stock() != null) product.setStock(request.stock());
        if (request.active() != null) product.setActive(request.active());

        Product updated = productRepository.save(product);
        log.info("Product updated: id={}", updated.getId());

        return toResponse(updated);
    }

    /**
     * Soft-delete a product by setting active to false.
     *
     * @throws ProductNotFoundException if product not found
     */
    @Transactional
    public void deleteProduct(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Product ID must not be null");
        }

        log.info("Deleting product (soft): id={}", id);

        Product product = productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));

        product.setActive(false);
        productRepository.save(product);
        log.info("Product soft-deleted: id={}", id);
    }

    /**
     * Get all categories with product counts.
     */
    public List<Map<String, Object>> getCategories() {
        log.info("Fetching categories");
        return productRepository.countByCategory().stream()
            .map(row -> Map.<String, Object>of(
                "name", row[0],
                "productCount", row[1]
            ))
            .collect(Collectors.toList());
    }

    // ---- Private helpers ----

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getCategory(),
            product.getImageUrl(),
            product.getStock(),
            product.getSku(),
            product.getRating(),
            product.getActive(),
            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }

    private String mapSortField(String sortBy) {
        if (sortBy == null) return "createdAt";
        return switch (sortBy.toLowerCase()) {
            case "name" -> "name";
            case "price" -> "price";
            case "rating" -> "rating";
            default -> "createdAt";
        };
    }
}
