package com.bharathi.vulntrack.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Who may do what:
 * <ul>
 *   <li>POST /api/findings/{id}/accept-risk: RISK role (the risk key only)</li>
 *   <li>POST /api/**: INGEST role (the ingest key)</li>
 *   <li>GET /api/** and the metrics endpoints: VIEWER role (the read key, or the ingest key)</li>
 *   <li>health, info and the error page: open, so probes and error responses keep working</li>
 * </ul>
 * Keys come from VULNTRACK_API_KEY and VULNTRACK_READ_API_KEY. An unset key matches nothing.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   @Value("${vulntrack.security.api-key:}") String ingestKey,
                                                   @Value("${vulntrack.security.read-api-key:}") String readKey,
                                                   @Value("${vulntrack.security.risk-api-key:}") String riskKey,
                                                   ObjectMapper json) throws Exception {
        http
                // Stateless API with no browser cookies, so CSRF protection does not apply
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new ApiKeyAuthFilter(ingestKey, readKey, riskKey), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Listed before the general POST rule: the first matching rule wins
                        .requestMatchers(HttpMethod.POST, "/api/findings/*/accept-risk").hasRole("RISK")
                        .requestMatchers(HttpMethod.POST, "/api/**").hasRole("INGEST")
                        .requestMatchers(HttpMethod.GET, "/api/**").hasRole("VIEWER")
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        // No valid key: 401
                        .authenticationEntryPoint((request, response, ex) ->
                                writeProblem(response, json, HttpStatus.UNAUTHORIZED,
                                        "A valid API key is required in the " + ApiKeyAuthFilter.HEADER + " header"))
                        // Valid key, but not allowed to do this: 403
                        .accessDeniedHandler((request, response, ex) ->
                                writeProblem(response, json, HttpStatus.FORBIDDEN,
                                        "This API key is not allowed to perform this request")));
        return http.build();
    }

    /** Errors as RFC 9457 Problem Details, the same shape as every other error in this API. */
    private static void writeProblem(HttpServletResponse response, ObjectMapper json,
                                     HttpStatus status, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }
}
