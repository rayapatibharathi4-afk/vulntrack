package com.bharathi.vulntrack.ingest;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.bharathi.vulntrack.config.CorrelationId;

@Component
public class ScanConsumer {

    private static final Logger log = LoggerFactory.getLogger(ScanConsumer.class);

    private final ScanIngestionService service;

    public ScanConsumer(ScanIngestionService service) {
        this.service = service;
    }

    @KafkaListener(topics = KafkaTopics.SCAN_SUBMITTED,
                   autoStartup = "${vulntrack.scan-consumer.enabled:true}")
    public void onScanSubmitted(ConsumerRecord<String, String> record) {
        // Restore the id the HTTP request had, so these log lines match the original POST
        String correlationId = correlationIdOf(record);
        MDC.put(CorrelationId.MDC_KEY, correlationId);
        try {
            log.info("Received scan {} (app {}, partition {}, offset {})",
                    record.value(), record.key(), record.partition(), record.offset());
            Long scanId = Long.valueOf(record.value());
            service.process(scanId);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }

    /** Uses the id from the message header. Messages without one (or with an unsafe one) get a new id. */
    private static String correlationIdOf(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(CorrelationId.MDC_KEY);
        String value = header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
        return CorrelationId.fromHeaderOrNew(value);
    }
}