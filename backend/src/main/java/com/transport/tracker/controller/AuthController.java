package com.transport.tracker.controller;

import com.transport.tracker.dto.LoginRequest;
import com.transport.tracker.dto.LoginResponse;
import com.transport.tracker.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication controller providing JWT-based login.
 * <p>
 * Exposes a single POST endpoint for user authentication.
 * On successful authentication, returns a signed JWT token
 * along with the user's role.
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "JWT authentication endpoints")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Operation(
            summary = "Authenticate user and obtain JWT token",
            description = "Validates credentials and returns a signed JWT token with the user's role."
    )
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for user: {}", request.username());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(),
                            request.password()
                    )
            );

            String role = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(a -> a.startsWith("ROLE_"))
                    .map(a -> a.substring(5)) // Remove ROLE_ prefix
                    .findFirst()
                    .orElse("VIEWER");

            String token = jwtService.generateToken(request.username(), role);

            log.info("Login successful for user: {} with role: {}", request.username(), role);

            return ResponseEntity.ok(new LoginResponse(token, request.username(), role));
        } catch (BadCredentialsException e) {
            log.warn("Login failed for user: {}", request.username());
            return ResponseEntity.status(401).build();
        }
    }
}
