package com.shopping.user.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.UUID;

/**
 * Shipping address entity linked to a user.
 */
@Entity
@Table(name = "addresses")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank(message = "Label is required")
    @Column(name = "label", length = 50)
    private String label; // e.g., "Home", "Office"

    @NotBlank(message = "Street address is required")
    @Column(name = "street", nullable = false)
    private String street;

    @NotBlank(message = "City is required")
    @Column(name = "city", nullable = false)
    private String city;

    @NotBlank(message = "State is required")
    @Column(name = "state", nullable = false)
    private String state;

    @NotBlank(message = "ZIP code is required")
    @Pattern(regexp = "^[0-9]{5}(-[0-9]{4})?$", message = "Invalid ZIP code format")
    @Column(name = "zip_code", nullable = false)
    private String zipCode;

    @NotBlank(message = "Country is required")
    @Column(name = "country", nullable = false)
    private String country = "US";

    @Column(name = "is_default")
    private Boolean isDefault = false;

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    /**
     * Formatted address string.
     */
    public String getFormattedAddress() {
        return street + ", " + city + ", " + state + " " + zipCode + ", " + country;
    }
}
