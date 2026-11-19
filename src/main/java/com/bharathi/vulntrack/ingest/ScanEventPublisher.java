package com.bharathi.vulntrack.ingest;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.bharathi.vulntrack.config.CorrelationId;

@Component
public class ScanEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ScanEventPublisher.class);

    private final KafkaTemplate<String, String> kafka;

    public ScanEventPublisher(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    /** Runs only after the scan is committed, so the consumer can always find it. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ScanSubmittedEvent event) {
        String key = String.valueOf(event.applicationId());
        String value = String.valueOf(event.scanId());
        // Read on the request thread: the Kafka callback below runs on another thread
        String correlationId = MDC.get(CorrelationId.MDC_KEY);

        ProducerRecord<String, String> record = new ProducerRecord<>(KafkaTopics.SCAN_SUBMITTED, key, value);
        if (correlationId != null) {
            record.headers().add(CorrelationId.MDC_KEY, correlationId.getBytes(StandardCharsets.UTF_8));
        }
        try {
            kafka.send(record).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("[{}] Could not publish scan {} to Kafka; it stays RECEIVED",
                            correlationId, event.scanId(), ex);
                } else {
                    log.info("[{}] Published scan {} to {} partition {} offset {}", correlationId, event.scanId(),
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (RuntimeException ex) {
            log.error("[{}] Could not publish scan {} to Kafka; it stays RECEIVED",
                    correlationId, event.scanId(), ex);
        }
    }
}