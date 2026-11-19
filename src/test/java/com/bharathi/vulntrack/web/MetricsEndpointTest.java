package com.bharathi.vulntrack.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.bharathi.vulntrack.KafkaContainerConfig;
import com.bharathi.vulntrack.PostgresContainerConfig;

/**
 * The Prometheus endpoint must show the three things we want to watch: the NVD circuit breaker,
 * scans processed, and Kafka consumer metrics (the lag is among them).
 */
@SpringBootTest(properties = {
        "vulntrack.enrichment.enabled=false",
        "vulntrack.scan-consumer.enabled=true",
        "vulntrack.outbox.enabled=false",
        "vulntrack.risk-expiry.enabled=false",
        "vulntrack.outbox-cleanup.enabled=false",
        "vulntrack.security.read-api-key=test-only-read-key" // dummy, test only
})
@AutoConfigureMockMvc
@AutoConfigureMetrics // Boot turns metrics export off in tests unless this is present
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class})
class MetricsEndpointTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void prometheusEndpointExposesCircuitBreakerScanAndKafkaMetrics() throws Exception {
        String body = mockMvc.perform(get("/actuator/prometheus").header("X-API-Key", "test-only-read-key"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains("resilience4j_circuitbreaker_state")
                .contains("vulntrack_scans_processed_total")
                .contains("kafka_consumer_fetch_manager_records_lag");
    }
}
