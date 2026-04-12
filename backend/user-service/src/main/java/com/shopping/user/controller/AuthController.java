package com.shopping.user.controller;

import com.shopping.user.dto.AuthResponse;
import com.shopping.user.dto.LoginRequest;
import com.shopping.user.dto.RegisterRequest;
import com.shopping.user.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication REST Controller.
 * Unsecured endpoints for user registration and login.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Validated
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * POST /api/v1/auth/register
     * Register a new user account.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("POST /api/v1/auth/register - email={}", request.email());
        AuthResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/auth/login
     * Authenticate and get JWT tokens.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /api/v1/auth/login - email={}", request.email());
        AuthResponse response = userService.login(request);
        return ResponseEntity.ok(response);
    }
}
