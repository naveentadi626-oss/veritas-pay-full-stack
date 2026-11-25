package com.veritaspay.unit;

import com.veritaspay.auth.JwtService;
import com.veritaspay.auth.Role;
import com.veritaspay.auth.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String TEST_SECRET = "TestOnlySuperSecureJwtSigningKeyThatIsLongEnough256Bits!";

    @Test
    void issuesAndValidatesJwtWithRoleClaim() {
        JwtService jwtService = new JwtService(TEST_SECRET, 60_000);
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "alice@veritaspay.io", "Alice Chen", "hash", Role.COMPLIANCE_OFFICER);

        String token = jwtService.generateAccessToken(user);
        Claims claims = jwtService.parseClaims(token);

        assertThat(jwtService.extractUserId(claims)).isEqualTo(userId);
        assertThat(claims.get("email", String.class)).isEqualTo("alice@veritaspay.io");
        assertThat(claims.get("role", String.class)).isEqualTo("COMPLIANCE_OFFICER");
    }

    @Test
    void rejectsTamperedToken() {
        JwtService jwtService = new JwtService(TEST_SECRET, 60_000);
        User user = new User(UUID.randomUUID(), "bob@veritaspay.io", "Bob", "hash", Role.CUSTOMER);
        String token = jwtService.generateAccessToken(user) + "tampered";

        assertThatThrownBy(() -> jwtService.parseClaims(token))
                .isInstanceOf(JwtException.class);
    }
}
