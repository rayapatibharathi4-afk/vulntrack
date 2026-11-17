package com.bharathi.vulntrack.nvd;

import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import com.github.benmanes.caffeine.cache.Cache;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;

/** NvdClient + cache + retry + circuit breaker. */
public class ResilientNvdClient {

    private final NvdClient client;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final Cache<String, CveDetails> cache;

    public ResilientNvdClient(NvdClient client, Retry retry, CircuitBreaker circuitBreaker,
                              Cache<String, CveDetails> cache) {
        this.client = client;
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
        this.cache = cache;
    }

    /**
     * @return the CVE's details, or empty if NVD does not know it
     * @throws NvdUnavailableException if NVD cannot be reached right now
     */
    public Optional<CveDetails> fetch(String cveId) {
        CveDetails cached = cache.getIfPresent(cveId);
        if (cached != null) {
            return Optional.of(cached);
        }

        Supplier<Optional<CveDetails>> call = () -> client.fetch(cveId);
        Supplier<Optional<CveDetails>> protectedCall =
                Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, call));

        try {
            Optional<CveDetails> result = protectedCall.get();
            result.ifPresent(details -> cache.put(cveId, details));
            return result;
        } catch (CallNotPermittedException e) {
            throw new NvdUnavailableException("NVD circuit breaker is open", e);
        } catch (RestClientException e) {
            throw new NvdUnavailableException("NVD call failed for " + cveId + ": " + e.getMessage(), e);
        }
    }

    /** Worth retrying and counting as an outage: timeouts, 5xx, and 429 Too Many Requests. */
    static boolean isTransient(Throwable t) {
        return t instanceof ResourceAccessException
                || t instanceof HttpServerErrorException
                || t instanceof HttpClientErrorException.TooManyRequests;
    }
}