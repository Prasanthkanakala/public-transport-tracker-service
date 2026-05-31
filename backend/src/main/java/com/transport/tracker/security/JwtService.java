package com.transport.tracker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;

/**
 * Service responsible for JWT token generation and validation.
 * <p>
 * Uses HMAC-SHA256 for token signing. The secret key and expiration
 * are externalized via application properties.
 * </p>
 * <p>
 * Security notes:
 * <ul>
 *   <li>Tokens are stateless — no server-side session storage</li>
 *   <li>JWT tokens are never logged</li>
 *   <li>Expired tokens are explicitly rejected</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret:MySecretKeyForJWTAuthenticationThatIsAtLeast256BitsLong!}") String secret,
            @Value("${jwt.expiration-ms:3600000}") long expirationMs) {
        Objects.requireNonNull(secret, "JWT secret must not be null");
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        log.info("JwtService initialized with expiration={}ms", expirationMs);
    }

    /**
     * Generates a signed JWT token containing the username and role.
     *
     * @param username the subject (user identifier)
     * @param role     the user's role (e.g., ADMIN, OPERATOR, VIEWER)
     * @return signed JWT token string
     */
    public String generateToken(String username, String role) {
        Objects.requireNonNull(username, "Username must not be null");
        Objects.requireNonNull(role, "Role must not be null");

        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        String token = Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();

        log.debug("JWT token generated for user: {}", username);
        return token;
    }

    /**
     * Extracts the username (subject) from a valid JWT token.
     *
     * @param token the JWT token
     * @return the username
     */
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Extracts the role claim from a valid JWT token.
     *
     * @param token the JWT token
     * @return the role string
     */
    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    /**
     * Validates the token: checks signature, expiration, and subject match.
     *
     * @param token    the JWT token
     * @param username the expected username
     * @return true if the token is valid
     */
    public boolean isTokenValid(String token, String username) {
        try {
            String tokenUsername = extractUsername(token);
            return tokenUsername.equals(username) && !isTokenExpired(token);
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired for user");
            return false;
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
