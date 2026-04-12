package com.shopping.user.dto;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    UserProfileResponse user
) {
    public AuthResponse(String accessToken, String refreshToken, UserProfileResponse user) {
        this(accessToken, refreshToken, "Bearer", 900, user); // 15 min = 900s
    }
}
