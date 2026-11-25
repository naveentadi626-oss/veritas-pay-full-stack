package com.veritaspay.auth;

import com.veritaspay.audit.AuditService;
import com.veritaspay.common.BusinessException;
import com.veritaspay.common.MoneyUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final long refreshExpirationMs;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService,
            @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        String normalizedEmail = request.email().toLowerCase().trim();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS",
                    "An account with this email already exists");
        }
        Role assignedRole = request.role() != null && request.role() != Role.ADMIN
                ? request.role()
                : Role.CUSTOMER;

        User user = new User(
                UUID.randomUUID(),
                normalizedEmail,
                request.fullName().trim(),
                passwordEncoder.encode(request.password()),
                assignedRole
        );
        userRepository.save(user);
        auditService.record(user.getId(), user.getEmail(), "USER_REGISTERED",
                "USER", user.getId().toString(), "role=" + assignedRole);
        return issueTokenPair(user);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase().trim())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED,
                        "INVALID_CREDENTIALS", "Invalid email or password"));

        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS", "Invalid email or password");
        }

        auditService.record(user.getId(), user.getEmail(), "USER_LOGIN",
                "USER", user.getId().toString(), "Login succeeded");
        return issueTokenPair(user);
    }

    @Transactional
    public AuthDtos.AuthResponse refresh(AuthDtos.RefreshRequest request) {
        String tokenHash = MoneyUtils.sha256(request.refreshToken());
        RefreshToken existing = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED,
                        "INVALID_REFRESH_TOKEN", "Refresh token is invalid"));

        if (existing.isRevoked() || existing.isExpired()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED,
                    "EXPIRED_REFRESH_TOKEN", "Refresh token has expired or been revoked");
        }

        // Rotate refresh token on every use
        existing.revoke();
        refreshTokenRepository.save(existing);

        User user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED,
                        "USER_NOT_FOUND", "User no longer exists"));
        return issueTokenPair(user);
    }

    private AuthDtos.AuthResponse issueTokenPair(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = UUID.randomUUID() + "." + UUID.randomUUID();
        RefreshToken refreshToken = new RefreshToken(
                UUID.randomUUID(),
                user.getId(),
                MoneyUtils.sha256(rawRefreshToken),
                Instant.now().plusMillis(refreshExpirationMs)
        );
        refreshTokenRepository.save(refreshToken);

        return new AuthDtos.AuthResponse(
                accessToken,
                rawRefreshToken,
                jwtService.getAccessExpirationMs(),
                new AuthDtos.UserProfile(user.getId(), user.getEmail(), user.getFullName(), user.getRole())
        );
    }
}
