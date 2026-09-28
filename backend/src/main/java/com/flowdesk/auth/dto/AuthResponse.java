package com.flowdesk.auth.dto;

import com.flowdesk.user.dto.UserSummaryResponse;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UserSummaryResponse user) {

    public static AuthResponse of(String accessToken, String refreshToken, long expiresInSeconds, UserSummaryResponse user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, user);
    }
}
