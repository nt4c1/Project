package com.health.common.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Singleton
public class JwtProvider {

    private final SecretKey key;
    private final long accessExpiry;
    private final long refreshExpiry;

    public JwtProvider(
            @Property(name = "jwt.secret") String secret,
            @Property(name = "jwt.access-expiry", defaultValue = "86400000") long accessExpiry,
            @Property(name = "jwt.refresh-expiry", defaultValue = "604800000") long refreshExpiry) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpiry = accessExpiry;
        this.refreshExpiry = refreshExpiry;
    }

    public String generateAccessToken(String userId, String role) {
        return Jwts.builder()
                .header()
                .type("JWT_Access")
                .and()
                .claim("uid", userId)
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpiry))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(String userId) {
        return Jwts.builder()
                .header()
                .type("JWT_Refresh")
                .and()
                .claim("uid", userId)
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiry))
                .signWith(key)
                .compact();
    }

    public String generateResetToken(String userId, String role) {
        return Jwts.builder()
                .header()
                .type("JWT_Reset")
                .and()
                .claim("uid", userId)
                .claim("role", role)
                .claim("type", "reset")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 15 * 60 * 1000)) // 15 minutes
                .signWith(key)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
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
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }
}
