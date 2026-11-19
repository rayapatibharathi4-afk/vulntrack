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
 * Writes need an API key; reads stay open for now.
 * The key comes from VULNTRACK_API_KEY. If it is not set, every write is refused.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   @Value("${vulntrack.security.api-key:}") String apiKey,
                                                   ObjectMapper json) throws Exception {
        http
                // Stateless API with no browser cookies, so CSRF protection does not apply
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new ApiKeyAuthFilter(apiKey), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/**").hasRole("INGEST")
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                        (request, response, ex) -> writeUnauthorized(response, json)));
        return http.build();
    }

    /** 401 as RFC 9457 Problem Details, the same shape as every other error in this API. */
    private static void writeUnauthorized(HttpServletResponse response, ObjectMapper json) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "A valid API key is required in the " + ApiKeyAuthFilter.HEADER + " header");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }
}
