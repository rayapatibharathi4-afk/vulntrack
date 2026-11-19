package com.bharathi.vulntrack.ingest;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

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
        log.info("Received scan {} (app {}, partition {}, offset {})",
                record.value(), record.key(), record.partition(), record.offset());
        Long scanId = Long.valueOf(record.value());
        service.process(scanId);
    }
}