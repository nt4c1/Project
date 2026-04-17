package com.health.doctor.infrastructure;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.inject.Singleton;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Singleton
public class JwtProvider {

    private static final String SECRET =
            "super-secret-key-for-healthcare-system-jwt-2026";
    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private static final long ACCESS_EXPIRY  = 86_400_000L;   // 24h
    private static final long REFRESH_EXPIRY = 604_800_000L;  // 7d

    public String generateAccessToken(String userId, String role) {
        return Jwts.builder()
                .claim("uid", userId)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_EXPIRY))
                .signWith(KEY)
                .compact();
    }

    public String generateRefreshToken(String userId) {
        return Jwts.builder()
                .claim("uid", userId)
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + REFRESH_EXPIRY))
                .signWith(KEY)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        return extractClaims(token).get("uid", String.class);
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    public boolean isValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}