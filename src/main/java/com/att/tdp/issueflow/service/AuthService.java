package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.auth.LoginRequest;
import com.att.tdp.issueflow.dto.auth.LoginResponse;
import com.att.tdp.issueflow.dto.user.UserResponse;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.UserRepository;
import com.att.tdp.issueflow.security.JwtUtil;
import com.att.tdp.issueflow.security.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository        userRepository;
    private final JwtUtil               jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    // ---------------------------------------------------------------
    // POST /auth/login
    // ---------------------------------------------------------------

    /**
     * Authenticates the user via Spring Security's AuthenticationManager.
     * On success, generates and returns a signed JWT token.
     * Throws AuthenticationException (→ 401) if credentials are invalid.
     */
    public LoginResponse login(LoginRequest request) {
        // Delegates to CustomUserDetailsService + PasswordEncoder internally
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()));

        String token = jwtUtil.generateToken(request.getUsername());

        return LoginResponse.of(token, jwtUtil.getExpirationSeconds());
    }

    // ---------------------------------------------------------------
    // POST /auth/logout
    // ---------------------------------------------------------------

    /**
     * Blacklists the provided JWT token so it is rejected on future requests.
     * The token is extracted by the controller from the Authorization header.
     */
    public void logout(String token) {
        tokenBlacklistService.blacklist(token);
    }

    // ---------------------------------------------------------------
    // GET /auth/me  - returns the currently authenticated user
    // ---------------------------------------------------------------

    /**
     * Resolves the logged-in user from the JWT-extracted username.
     * Called by the controller after the JwtAuthFilter sets the SecurityContext.
     */
    public UserResponse getMe(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user not found: " + username));
        return UserResponse.from(user);
    }
}
