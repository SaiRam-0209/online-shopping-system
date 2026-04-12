package com.shopping.cart.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Individual item in a shopping cart.
 */
public class CartItem {

    private UUID productId;
    private String productName;
    private String imageUrl;
    private BigDecimal price;
    private int quantity;
    private BigDecimal lineTotal;

    public CartItem() {}

    public CartItem(UUID productId, String productName, String imageUrl,
                    BigDecimal price, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.imageUrl = imageUrl;
        this.price = price;
        this.quantity = quantity;
        this.lineTotal = price.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * Recalculate line total when quantity changes.
     */
    public void recalculateLineTotal() {
        if (this.price != null && this.quantity > 0) {
            this.lineTotal = this.price.multiply(BigDecimal.valueOf(this.quantity));
        }
    }

    // Getters and Setters
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) {
        this.quantity = quantity;
        recalculateLineTotal();
    }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}
