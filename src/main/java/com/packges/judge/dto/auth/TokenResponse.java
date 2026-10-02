package com.packges.judge.dto.auth;

import java.time.Instant;

public record TokenResponse(
        String token,
        Instant expiresAt
) {
}
