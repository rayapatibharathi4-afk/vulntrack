package com.bharathi.vulntrack.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads the X-API-Key header and, when it matches the configured key, marks the request
 * as authenticated with the INGEST role. A request without a valid key is simply not
 * authenticated; the authorization rules decide what happens next.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    public static final String ROLE_INGEST = "ROLE_INGEST";

    /** SHA-256 of the configured key, or null when no key is configured (then nothing matches). */
    private final byte[] expectedHash;

    public ApiKeyAuthFilter(String configuredKey) {
        this.expectedHash = (configuredKey == null || configuredKey.isBlank()) ? null : sha256(configuredKey);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented != null && matches(presented)) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "api-key", null, List.of(new SimpleGrantedAuthority(ROLE_INGEST))));
        }
        chain.doFilter(request, response);
    }

    /**
     * Compares hashes, not the raw keys, so the comparison takes the same time whatever the
     * presented key looks like (and its length does not leak).
     */
    private boolean matches(String presented) {
        if (expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedHash, sha256(presented));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
    }
}
