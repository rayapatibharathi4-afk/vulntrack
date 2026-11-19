package com.bharathi.vulntrack.nvd;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

@Configuration
@EnableConfigurationProperties(NvdProperties.class)
public class NvdConfig {

    private static final Logger log = LoggerFactory.getLogger(NvdConfig.class);

    @Bean
    public NvdClient nvdClient(NvdProperties props) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(props.connectTimeout());
        requestFactory.setReadTimeout(props.readTimeout());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(requestFactory);

        if (props.apiKey() != null && !props.apiKey().isBlank()) {
            builder.defaultHeader("apiKey", props.apiKey());
        }
        return new NvdClient(builder.build());
    }

    @Bean
    public ResilientNvdClient resilientNvdClient(NvdClient nvdClient, MeterRegistry meters) {
        // The breaker is created through a registry so Micrometer can see it
        CircuitBreakerRegistry breakers = CircuitBreakerRegistry.ofDefaults();
        CircuitBreaker circuitBreaker = breakers.circuitBreaker("nvd", circuitBreakerConfig(10, Duration.ofSeconds(60)));
        circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.warn("NVD circuit breaker: {}", event.getStateTransition()));
        // Exposes state (open/closed/half-open) and call outcomes as resilience4j_circuitbreaker_* metrics
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(breakers).bindTo(meters);

        Cache<String, CveDetails> cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofHours(24))
                .build();

        return new ResilientNvdClient(nvdClient, retry(3, Duration.ofSeconds(1)), circuitBreaker, cache);
    }

    /** Exponential backoff with jitter: about 1s, then 2s, each +/- 50% random. */
    static Retry retry(int maxAttempts, Duration initialWait) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(initialWait, 2.0, 0.5))
                .retryOnException(ResilientNvdClient::isTransient)
                .build();
        return Retry.of("nvd", config);
    }

    /** Opens when at least half of the last N calls failed; tries again after openWait. */
    static CircuitBreaker circuitBreaker(int windowSize, Duration openWait) {
        return CircuitBreaker.of("nvd", circuitBreakerConfig(windowSize, openWait));
    }

    static CircuitBreakerConfig circuitBreakerConfig(int windowSize, Duration openWait) {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(windowSize)
                .minimumNumberOfCalls(windowSize)
                .failureRateThreshold(50)
                .waitDurationInOpenState(openWait)
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordException(ResilientNvdClient::isTransient)
                .build();
    }
}