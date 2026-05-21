package com.att.tdp.issueflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

/** Utility for generating, parsing, and validating HMAC-signed JWT tokens. */
@Component
public class JwtUtil {

    private static final long EXPIRATION_MS = 24 * 60 * 60 * 1000L; // 24 hours

    @Value("${jwt.secret:issueflow-secret-key-must-be-at-least-32-chars-long}")
    private String secret;

    // ---------------------------------------------------------------
    // Token generation
    // ---------------------------------------------------------------

    /** Generates a signed JWT for the given username, valid for 24 hours. */
    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(getSigningKey())
                .compact();
    }

    // ---------------------------------------------------------------
    // Token extraction
    // ---------------------------------------------------------------

    /** Extracts the username (subject claim) from the given JWT token. */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /** Extracts the expiration date from the given JWT token. */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /** Extracts an arbitrary claim from the token by applying the given resolver function. */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    // ---------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------

    /** Returns true if the token belongs to the given user and has not expired. */
    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** Returns true if the token's expiration date is before now. */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /** Parses and verifies the token signature, then returns all claims from its payload. */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Derives the HMAC-SHA signing key from the configured secret string. */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** Returns the token lifetime in seconds - used when building the login response. */
    public long getExpirationSeconds() {
        return EXPIRATION_MS / 1000;
    }
}
