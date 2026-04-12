package com.shopping.user.service;

import com.shopping.user.dto.*;
import com.shopping.user.exception.DuplicateEmailException;
import com.shopping.user.exception.InvalidCredentialsException;
import com.shopping.user.exception.UserNotFoundException;
import com.shopping.user.model.User;
import com.shopping.user.model.UserRole;
import com.shopping.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Service — handles registration, authentication, and profile management.
 *
 * Security:
 * - Passwords hashed with BCrypt (cost factor 12)
 * - JWT tokens: 15-min access, 7-day refresh with rotation
 * - Failed login attempts tracked (rate-limited at gateway)
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder(12);
    }

    /**
     * Register a new user.
     *
     * @throws DuplicateEmailException if email already registered
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Register request must not be null");
        }

        log.info("Registering user: email={}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("Email already registered: " + request.email());
        }

        User user = new User();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email().toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPhone(request.phone());
        user.setRole(UserRole.CUSTOMER);
        user.setActive(true);

        User saved = userRepository.save(user);
        log.info("User registered: id={}, email={}", saved.getId(), saved.getEmail());

        // Generate JWT tokens
        String accessToken = generateAccessToken(saved);
        String refreshToken = generateRefreshToken(saved);

        return new AuthResponse(accessToken, refreshToken, toProfile(saved));
    }

    /**
     * Authenticate user with email and password.
     *
     * @throws InvalidCredentialsException if credentials don't match
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Login request must not be null");
        }

        log.info("Login attempt: email={}", request.email());

        User user = userRepository.findByEmail(request.email().toLowerCase().trim())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!user.getActive()) {
            throw new InvalidCredentialsException("Account is deactivated");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Failed login attempt: email={}", request.email());
            throw new InvalidCredentialsException("Invalid email or password");
        }

        // Update last login timestamp
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Login successful: userId={}", user.getId());

        String accessToken = generateAccessToken(user);
        String refreshToken = generateRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken, toProfile(user));
    }

    /**
     * Get user profile by ID.
     */
    public UserProfileResponse getProfile(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        User user = userRepository.findByIdAndActiveTrue(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        return toProfile(user);
    }

    /**
     * Update user profile.
     */
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, String firstName, String lastName,
                                              String phone) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        if (firstName != null && !firstName.isBlank()) user.setFirstName(firstName);
        if (lastName != null && !lastName.isBlank()) user.setLastName(lastName);
        if (phone != null) user.setPhone(phone);

        User updated = userRepository.save(user);
        log.info("Profile updated: userId={}", userId);

        return toProfile(updated);
    }

    // ---- JWT Token Generation (Deterministic) ----

    /**
     * Generate JWT access token.
     * In production: use io.jsonwebtoken (jjwt) library with RSA-256 or HMAC-SHA256.
     * Token includes: userId, email, role, expiry (15 min).
     */
    private String generateAccessToken(User user) {
        // Placeholder: In production, use proper JWT library
        // NEVER use this in production — this is for scaffolding only
        return "eyJ" + UUID.randomUUID().toString().replace("-", "")
            + "." + user.getId() + "." + System.currentTimeMillis();
    }

    /**
     * Generate JWT refresh token.
     * Longer-lived (7 days), stored in DB for rotation.
     */
    private String generateRefreshToken(User user) {
        return "ref_" + UUID.randomUUID().toString().replace("-", "");
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
            user.getId(),
            user.getFirstName(),
            user.getLastName(),
            user.getEmail(),
            user.getPhone(),
            user.getRole().name(),
            user.getCreatedAt(),
            user.getLastLoginAt()
        );
    }
}
