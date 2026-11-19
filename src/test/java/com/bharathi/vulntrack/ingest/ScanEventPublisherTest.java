package com.bharathi.vulntrack.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The publisher must send to Kafka only after the transaction commits.
 * These tests write no rows, so they need no cleanup.
 */
@SpringBootTest(properties = {
        "vulntrack.enrichment.enabled=false",
        "vulntrack.scan-consumer.enabled=false",
        "spring.kafka.admin.auto-create=false"
})
class ScanEventPublisherTest {

    @MockitoBean
    KafkaTemplate<String, String> kafka;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate tx;

    @Test
    void sendsNothingUntilTheTransactionCommits() {
        when(kafka.send(eq(KafkaTopics.SCAN_SUBMITTED), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(successResult()));

        tx.executeWithoutResult(status -> {
            events.publishEvent(new ScanSubmittedEvent(7L, 3L));
            verify(kafka, never()).send(any(), any(), any());
        });

        verify(kafka).send(KafkaTopics.SCAN_SUBMITTED, "3", "7");
    }

    @Test
    void sendsNothingWhenTheTransactionRollsBack() {
        tx.executeWithoutResult(status -> {
            events.publishEvent(new ScanSubmittedEvent(8L, 3L));
            status.setRollbackOnly();
        });

        verify(kafka, never()).send(any(), any(), any());
    }

    @Test
    void aFailedSendIsLoggedAndDoesNotFailTheRequest() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafka.send(eq(KafkaTopics.SCAN_SUBMITTED), any(), any())).thenReturn(failed);

        // Must not throw: the scan stays RECEIVED and the request has already been answered
        tx.executeWithoutResult(status -> events.publishEvent(new ScanSubmittedEvent(9L, 3L)));

        assertThat(failed).isCompletedExceptionally();
    }

    private static SendResult<String, String> successResult() {
        RecordMetadata metadata = mock(RecordMetadata.class);
        return new SendResult<>(null, metadata);
    }
}
