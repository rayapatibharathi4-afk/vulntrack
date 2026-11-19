package com.bharathi.vulntrack.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.bharathi.vulntrack.config.CorrelationId;
import com.bharathi.vulntrack.domain.OutboxEvent;
import com.bharathi.vulntrack.repository.OutboxEventRepository;

/** The relay with mocks: no database and no broker. The end-to-end path is tested separately. */
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final OutboxRelay relay = new OutboxRelay(outbox, kafka, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void sendsPendingRowsOldestFirstAndMarksThemSent() {
        OutboxEvent first = event("7", "req-1");
        OutboxEvent second = event("8", "req-1");
        when(outbox.findTop100BySentAtIsNullOrderByIdAsc()).thenReturn(List.of(first, second));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(acknowledged());

        int sent = relay.relay();

        assertThat(sent).isEqualTo(2);
        assertThat(first.getSentAt()).isEqualTo(NOW);
        assertThat(second.getSentAt()).isEqualTo(NOW);

        ArgumentCaptor<ProducerRecord<String, String>> records = sentRecords();
        verify(kafka, times(2)).send(records.capture());
        assertThat(records.getAllValues()).extracting(ProducerRecord::value).containsExactly("7", "8");
    }

    @Test
    void stopsAtTheFirstFailureSoLaterRowsStayUnsentAndInOrder() {
        OutboxEvent first = event("7", "req-1");
        OutboxEvent second = event("8", "req-1");
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unavailable"));
        when(outbox.findTop100BySentAtIsNullOrderByIdAsc()).thenReturn(List.of(first, second));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(failed);

        int sent = relay.relay();

        assertThat(sent).isZero();
        assertThat(first.getSentAt()).isNull();
        assertThat(second.getSentAt()).isNull();
        // The second row was never attempted, so it cannot overtake the first
        verify(kafka, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    void sendsTheCorrelationIdAsAHeader() {
        when(outbox.findTop100BySentAtIsNullOrderByIdAsc()).thenReturn(List.of(event("7", "req-42")));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(acknowledged());

        relay.relay();

        ArgumentCaptor<ProducerRecord<String, String>> records = sentRecords();
        verify(kafka).send(records.capture());
        byte[] header = records.getValue().headers().lastHeader(CorrelationId.MDC_KEY).value();
        assertThat(new String(header, StandardCharsets.UTF_8)).isEqualTo("req-42");
    }

    @Test
    void nothingPendingMeansNothingSent() {
        when(outbox.findTop100BySentAtIsNullOrderByIdAsc()).thenReturn(List.of());

        assertThat(relay.relay()).isZero();
        verify(kafka, times(0)).send(any(ProducerRecord.class));
    }

    private static OutboxEvent event(String payload, String correlationId) {
        return new OutboxEvent(KafkaTopics.SCAN_SUBMITTED, "3", payload, correlationId, NOW);
    }

    private static CompletableFuture<SendResult<String, String>> acknowledged() {
        return CompletableFuture.completedFuture(new SendResult<>(null, mock(RecordMetadata.class)));
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<ProducerRecord<String, String>> sentRecords() {
        return ArgumentCaptor.forClass(ProducerRecord.class);
    }
}
