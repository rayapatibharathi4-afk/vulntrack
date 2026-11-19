package com.bharathi.vulntrack.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import com.bharathi.vulntrack.KafkaContainerConfig;
import com.bharathi.vulntrack.PostgresContainerConfig;

/**
 * End to end, against a real Kafka broker in a container: a message that can never be processed
 * must end up on scan-submitted.DLT instead of blocking the topic.
 */
@SpringBootTest(properties = {
        "vulntrack.enrichment.enabled=false",
        "vulntrack.scan-consumer.enabled=true"
})
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class})
class ScanDeadLetterKafkaTest {

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    KafkaListenerEndpointRegistry listeners;

    /**
     * The address @ServiceConnection gives the producer and listeners (the container). Reading the
     * raw property or KafkaProperties would give the local dev broker from application.properties.
     */
    @Autowired
    KafkaConnectionDetails connection;

    @Test
    void unparseableMessageIsDeadLetteredWithoutRetry() throws Exception {
        awaitPartitionAssignment();
        ProducerRecord<String, String> poison =
                new ProducerRecord<>(KafkaTopics.SCAN_SUBMITTED, "app-1", "not-a-number");
        poison.headers().add("correlationId", "req-123".getBytes(StandardCharsets.UTF_8));
        kafka.send(poison).get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, String> dead = readOne(KafkaTopics.SCAN_SUBMITTED_DLT);

        assertThat(dead.value()).isEqualTo("not-a-number");
        // The original headers survive the dead-letter hop, so the failure can be traced to its request
        assertThat(new String(dead.headers().lastHeader("correlationId").value(), StandardCharsets.UTF_8))
                .isEqualTo("req-123");
        // The listener wraps the error; the root cause header shows why it was dead-lettered
        String exception = new String(dead.headers().lastHeader("kafka_dlt-exception-cause-fqcn").value(),
                StandardCharsets.UTF_8);
        assertThat(exception).isEqualTo(NumberFormatException.class.getName());
    }

    /**
     * Producing before a listener owns a partition can leave the message unread for a while,
     * so wait for an assignment first.
     */
    private void awaitPartitionAssignment() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (listeners.getListenerContainers().stream()
                .noneMatch(c -> !c.getAssignedPartitions().isEmpty())) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("No listener got a partition assignment");
            }
            Thread.sleep(100);
        }
    }

    private ConsumerRecord<String, String> readOne(String topic) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", connection.getBootstrapServers()),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + System.nanoTime(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (Consumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            return KafkaTestUtils.getSingleRecord(consumer, topic, Duration.ofSeconds(60));
        }
    }
}
