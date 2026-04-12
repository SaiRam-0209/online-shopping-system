package com.shopping.user.controller;

import com.shopping.user.dto.UserProfileResponse;
import com.shopping.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * User Profile REST Controller.
 * Secured endpoints for managing user profile.
 */
@RestController
@RequestMapping("/api/v1/users")
@Validated
@CrossOrigin(origins = "*", maxAge = 3600)
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * GET /api/v1/users/me
     * Get current user's profile.
     * Uses X-User-Id header injected by API Gateway.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @RequestHeader("X-User-Id") UUID userId) {
        log.info("GET /api/v1/users/me - userId={}", userId);
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    /**
     * PUT /api/v1/users/me
     * Update current user's profile.
     */
    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody Map<String, String> request) {
        
        log.info("PUT /api/v1/users/me - userId={}", userId);
        
        String firstName = request.get("firstName");
        String lastName = request.get("lastName");
        String phone = request.get("phone");
        
        return ResponseEntity.ok(userService.updateProfile(userId, firstName, lastName, phone));
    }
}
