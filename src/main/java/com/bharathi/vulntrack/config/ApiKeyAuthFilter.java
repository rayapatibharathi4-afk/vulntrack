package com.bharathi.vulntrack.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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
 * Reads the X-API-Key header and grants roles by key:
 * <ul>
 *   <li>the ingest key: INGEST (write) and VIEWER (read)</li>
 *   <li>the read key: VIEWER (read only)</li>
 *   <li>the risk key: RISK (accept risks), nothing else</li>
 * </ul>
 * A missing or unknown key grants nothing; the authorization rules decide what happens next.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    public static final String ROLE_INGEST = "ROLE_INGEST";
    public static final String ROLE_VIEWER = "ROLE_VIEWER";
    public static final String ROLE_RISK = "ROLE_RISK";

    /** SHA-256 of a configured key, or null when it is not configured (then it matches nothing). */
    private final byte[] ingestHash;
    private final byte[] readHash;
    private final byte[] riskHash;

    public ApiKeyAuthFilter(String ingestKey, String readKey, String riskKey) {
        this.ingestHash = hashIfConfigured(ingestKey);
        this.readHash = hashIfConfigured(readKey);
        this.riskHash = hashIfConfigured(riskKey);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented != null) {
            // Hash once; compare against each configured key in constant time
            byte[] presentedHash = sha256(presented);
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            if (matches(ingestHash, presentedHash)) {
                authorities.add(new SimpleGrantedAuthority(ROLE_INGEST));
                authorities.add(new SimpleGrantedAuthority(ROLE_VIEWER));
            } else if (matches(readHash, presentedHash)) {
                authorities.add(new SimpleGrantedAuthority(ROLE_VIEWER));
            } else if (matches(riskHash, presentedHash)) {
                // Only this role may accept risks: separate keys keep the creator and the accepter apart
                authorities.add(new SimpleGrantedAuthority(ROLE_RISK));
            }
            if (!authorities.isEmpty()) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken("api-key", null, authorities));
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Compares hashes, not the raw keys, so the comparison takes the same time whatever the
     * presented key looks like (and its length does not leak).
     */
    private static boolean matches(byte[] expectedHash, byte[] presentedHash) {
        return expectedHash != null && MessageDigest.isEqual(expectedHash, presentedHash);
    }

    private static byte[] hashIfConfigured(String key) {
        return (key == null || key.isBlank()) ? null : sha256(key);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
    }
}
