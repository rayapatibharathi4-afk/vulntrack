package com.bharathi.vulntrack.nvd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ResilientNvdClientTest {

    private static final String FOUND = """
            {"totalResults": 1, "vulnerabilities": [{"cve": {
              "id": "CVE-2021-44228", "published": "2021-12-10T10:15:09.143",
              "descriptions": [{"lang": "en", "value": "Log4Shell"}],
              "metrics": {"cvssMetricV31": [{"type": "Primary",
                "cvssData": {"baseScore": 10.0, "baseSeverity": "CRITICAL"}}]}}}]}
            """;

    private MockRestServiceServer server;

    /** A fast setup for tests: tiny waits, small circuit window. */
    private ResilientNvdClient client(int maxAttempts, CircuitBreaker circuitBreaker) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://nvd.test/cves/2.0");
        server = MockRestServiceServer.bindTo(builder).build();
        return new ResilientNvdClient(
                new NvdClient(builder.build()),
                NvdConfig.retry(maxAttempts, Duration.ofMillis(10)),
                circuitBreaker,
                Caffeine.newBuilder().build());
    }

    @Test
    void retriesAServerErrorThenSucceeds() {
        ResilientNvdClient nvd = client(3, NvdConfig.circuitBreaker(10, Duration.ofMinutes(1)));
        server.expect(once(), anything()).andRespond(withServerError());
        server.expect(once(), anything()).andRespond(withSuccess(FOUND, MediaType.APPLICATION_JSON));

        assertThat(nvd.fetch("CVE-2021-44228")).isPresent();
        server.verify(); // exactly 2 requests: one failure, one retry
    }

    @Test
    void doesNotRetryABadRequest() {
        ResilientNvdClient nvd = client(3, NvdConfig.circuitBreaker(10, Duration.ofMinutes(1)));
        server.expect(once(), anything()).andRespond(withBadRequest());

        assertThatThrownBy(() -> nvd.fetch("CVE-2021-44228"))
                .isInstanceOf(NvdUnavailableException.class);
        server.verify(); // exactly 1 request: 400 is not retried
    }

    @Test
    void circuitOpensAfterRepeatedFailuresAndStopsCalling() {
        CircuitBreaker circuitBreaker = NvdConfig.circuitBreaker(4, Duration.ofMinutes(1));
        ResilientNvdClient nvd = client(1, circuitBreaker);
        server.expect(times(4), anything()).andRespond(withServerError());

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> nvd.fetch("CVE-2021-44228"))
                    .isInstanceOf(NvdUnavailableException.class);
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> nvd.fetch("CVE-2021-44228"))
                .isInstanceOf(NvdUnavailableException.class)
                .hasCauseInstanceOf(CallNotPermittedException.class);
        server.verify(); // still exactly 4 requests: the 5th never reached NVD
    }

    @Test
    void cachesSuccessfulLookups() {
        ResilientNvdClient nvd = client(3, NvdConfig.circuitBreaker(10, Duration.ofMinutes(1)));
        server.expect(once(), anything()).andRespond(withSuccess(FOUND, MediaType.APPLICATION_JSON));

        assertThat(nvd.fetch("CVE-2021-44228")).isPresent();
        assertThat(nvd.fetch("CVE-2021-44228")).isPresent();
        server.verify(); // exactly 1 request: the second came from the cache
    }
}