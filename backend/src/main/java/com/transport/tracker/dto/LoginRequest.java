package com.transport.tracker.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for authentication login requests.
 * Contains username and password fields with validation.
 */
public record LoginRequest(
        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        String password
) {
    @Override
    public String toString() {
        // Never log passwords
        return "LoginRequest{username='" + username + "'}";
    }
}
