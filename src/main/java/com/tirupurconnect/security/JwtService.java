package com.tirupurconnect.security;

import com.tirupurconnect.config.AppProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private final AppProperties props;

    private SecretKey signingKey() {
        byte[] keyBytes = props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        // Ensure key is at least 256 bits for HS256
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 characters");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(UUID userId, String phone, String role, String tenantId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(userId.toString())
            .claims(Map.of(
                "phone",    phone,
                "role",     role,
                "tenantId", tenantId
            ))
            .issuedAt(new Date(now))
            .expiration(new Date(now + props.getJwt().getExpiryMs()))
            .signWith(signingKey())
            .compact();
    }

    public Claims validateAndExtract(String token) {
        try {
            return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: {}", e.getMessage());
            throw e;
        } catch (JwtException e) {
            log.warn("JWT invalid: {}", e.getMessage());
            throw e;
        }
    }

    public boolean isValid(String token) {
        try {
            validateAndExtract(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
