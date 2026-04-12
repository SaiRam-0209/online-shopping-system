package com.shopping.product.controller;

import com.shopping.product.dto.CreateProductRequest;
import com.shopping.product.dto.ProductResponse;
import com.shopping.product.dto.UpdateProductRequest;
import com.shopping.product.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Product Service.
 * Contract-first implementation aligned with product-service-openapi.yaml.
 *
 * Endpoints:
 *   GET    /api/v1/products           - List products (paginated, filterable)
 *   GET    /api/v1/products/{id}      - Get product by ID
 *   POST   /api/v1/products           - Create product
 *   PUT    /api/v1/products/{id}      - Update product
 *   DELETE /api/v1/products/{id}      - Soft-delete product
 *   GET    /api/v1/products/categories - List categories
 */
@RestController
@RequestMapping("/api/v1/products")
@Validated
@CrossOrigin(origins = "*", maxAge = 3600)
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * GET /api/v1/products
     * List all products with pagination and filtering.
     */
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> listProducts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        log.info("GET /api/v1/products - page={}, size={}, category={}", page, size, category);

        Page<ProductResponse> products = productService.getProducts(
            page, size, category, minPrice, maxPrice, search, sortBy, sortDir
        );

        return ResponseEntity.ok(products);
    }

    /**
     * GET /api/v1/products/categories
     * List all product categories with counts.
     * NOTE: This endpoint is mapped BEFORE {id} to avoid path conflict.
     */
    @GetMapping("/categories")
    public ResponseEntity<List<Map<String, Object>>> listCategories() {
        log.info("GET /api/v1/products/categories");
        return ResponseEntity.ok(productService.getCategories());
    }

    /**
     * GET /api/v1/products/{id}
     * Get a single product by its UUID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable UUID id) {
        log.info("GET /api/v1/products/{}", id);
        return ResponseEntity.ok(productService.getProductById(id));
    }

    /**
     * POST /api/v1/products
     * Create a new product in the catalog.
     */
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        log.info("POST /api/v1/products - name={}", request.name());
        ProductResponse created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /api/v1/products/{id}
     * Update an existing product (partial update).
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        log.info("PUT /api/v1/products/{}", id);
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    /**
     * DELETE /api/v1/products/{id}
     * Soft-delete a product.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable UUID id) {
        log.info("DELETE /api/v1/products/{}", id);
        productService.deleteProduct(id);
    }
}
