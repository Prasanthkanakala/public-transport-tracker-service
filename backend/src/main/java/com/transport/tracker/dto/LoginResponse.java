package com.transport.tracker.dto;

/**
 * DTO for authentication login responses.
 * Contains the JWT token, username, and assigned role.
 */
public record LoginResponse(
        String token,
        String username,
        String role
) {
    @Override
    public String toString() {
        // Never log JWT tokens
        return "LoginResponse{username='" + username + "', role='" + role + "'}";
    }
}
