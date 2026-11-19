package com.bharathi.vulntrack.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
        try {
            kafka.send(KafkaTopics.SCAN_SUBMITTED, key, value).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Could not publish scan {} to Kafka; it stays RECEIVED", event.scanId(), ex);
                } else {
                    log.info("Published scan {} to {} partition {} offset {}", event.scanId(),
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (RuntimeException ex) {
            log.error("Could not publish scan {} to Kafka; it stays RECEIVED", event.scanId(), ex);
        }
    }
}