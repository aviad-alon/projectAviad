package com.att.tdp.issueflow.security;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * In-memory token blacklist for logout support.
 * Once a token is blacklisted, the JwtAuthFilter will reject it on every subsequent request.
 * Note: this list resets on application restart (acceptable for a stateless JWT API).
 */
@Service
public class TokenBlacklistService {

    private final Set<String> blacklist = Collections.synchronizedSet(new HashSet<>());

    /** Adds the given token to the blacklist so future requests bearing it are rejected. */
    public void blacklist(String token) {
        blacklist.add(token);
    }

    /** Returns true if the given token has been blacklisted via logout. */
    public boolean isBlacklisted(String token) {
        return blacklist.contains(token);
    }
}
