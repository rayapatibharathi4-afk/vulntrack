package com.bharathi.vulntrack.ingest;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * What happens when processing a scan message fails:
 * retry a few times with growing pauses; bad input is not retried; anything that is
 * still failing goes to scan-submitted.DLT and the scan is marked FAILED.
 */
@Configuration
public class ScanErrorHandlingConfig {

    private static final Logger log = LoggerFactory.getLogger(ScanErrorHandlingConfig.class);

    @Bean
    public CommonErrorHandler scanErrorHandler(ScanIngestionService service,
                                               KafkaTemplate<String, String> kafka) {
        // Spring's default name is "<topic>-dlt", which is not our topic. Route explicitly.
        DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(kafka,
                (record, ex) -> new TopicPartition(KafkaTopics.SCAN_SUBMITTED_DLT, record.partition()));
        ConsumerRecordRecoverer recoverer = (ConsumerRecord<?, ?> record, Exception ex) -> {
            markScanFailed(service, record);
            dlt.accept(record, ex);
        };

        // 1s, 2s, 4s between attempts, then give up: 4 attempts in total
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // Retrying cannot fix these: the message or the report is simply wrong
        handler.addNotRetryableExceptions(NumberFormatException.class, InvalidReportException.class);
        return handler;
    }

    private static void markScanFailed(ScanIngestionService service, ConsumerRecord<?, ?> record) {
        try {
            service.markFailed(Long.valueOf(String.valueOf(record.value())));
        } catch (NumberFormatException e) {
            log.error("Dead-lettered message has no valid scan id: '{}'", record.value());
        }
    }
}
