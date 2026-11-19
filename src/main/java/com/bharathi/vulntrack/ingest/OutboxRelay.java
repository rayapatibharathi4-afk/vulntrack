package com.bharathi.vulntrack.ingest;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.bharathi.vulntrack.config.CorrelationId;
import com.bharathi.vulntrack.domain.OutboxEvent;
import com.bharathi.vulntrack.repository.OutboxEventRepository;

/**
 * Sends outbox rows to Kafka, oldest first. A row is marked sent only after the broker acknowledges it.
 * If the process crashes after the send but before the mark, the row is sent again. The consumer skips
 * scans it has already finished, so that duplicate is harmless.
 */
@Component
@ConditionalOnProperty(name = "vulntrack.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;

    public OutboxRelay(OutboxEventRepository outbox, KafkaTemplate<String, String> kafka, Clock clock) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.clock = clock;
    }

    /**
     * Sends one batch. Returns how many messages were acknowledged. Stops at the first failure, so the
     * rows after it are not sent out of order; the next run retries from there.
     *
     * @Transactional sits on this method, not on a helper it calls: a call from one method to another
     * inside the same class bypasses the proxy, so the transaction would never start.
     */
    @Scheduled(fixedDelayString = "${vulntrack.outbox.delay:1s}")
    @Transactional
    public int relay() {
        List<OutboxEvent> batch = outbox.findTop100BySentAtIsNullOrderByIdAsc();
        int sent = 0;
        for (OutboxEvent event : batch) {
            try {
                kafka.send(toRecord(event)).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                event.markSent(clock.instant());
                sent++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("[{}] Could not send outbox event {} to {}; will retry", event.getCorrelationId(),
                        event.getId(), event.getTopic(), e);
                break;
            }
        }
        return sent;
    }

    private static ProducerRecord<String, String> toRecord(OutboxEvent event) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(event.getTopic(), event.getMessageKey(), event.getPayload());
        if (event.getCorrelationId() != null) {
            record.headers().add(CorrelationId.MDC_KEY,
                    event.getCorrelationId().getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }
}
