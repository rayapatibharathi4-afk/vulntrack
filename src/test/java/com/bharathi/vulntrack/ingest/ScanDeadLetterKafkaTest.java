package com.bharathi.vulntrack.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/**
 * End to end, against an in-process Kafka broker: a message that can never be processed
 * must end up on scan-submitted.DLT instead of blocking the topic.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "vulntrack.enrichment.enabled=false",
        "vulntrack.scan-consumer.enabled=true"
})
@EmbeddedKafka(partitions = 3, topics = {KafkaTopics.SCAN_SUBMITTED, KafkaTopics.SCAN_SUBMITTED_DLT})
class ScanDeadLetterKafkaTest {

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    EmbeddedKafkaBroker broker;

    @Autowired
    KafkaListenerEndpointRegistry listeners;

    @Test
    void unparseableMessageIsDeadLetteredWithoutRetry() throws Exception {
        awaitPartitionAssignment();
        kafka.send(KafkaTopics.SCAN_SUBMITTED, "app-1", "not-a-number").get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, String> dead = readOne(KafkaTopics.SCAN_SUBMITTED_DLT);

        assertThat(dead.value()).isEqualTo("not-a-number");
        // The listener wraps the error; the root cause header shows why it was dead-lettered
        String exception = new String(dead.headers().lastHeader("kafka_dlt-exception-cause-fqcn").value(),
                StandardCharsets.UTF_8);
        assertThat(exception).isEqualTo(NumberFormatException.class.getName());
    }

    /**
     * The embedded broker is slow to finish its group setup. Producing before a listener owns a
     * partition can leave the message unread for a long time, so wait for an assignment first.
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
        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-test-" + System.nanoTime(), "true", broker);
        // The helper defaults the key to Integer; our keys are application ids as strings
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (Consumer<String, String> consumer =
                     new DefaultKafkaConsumerFactory<String, String>(props).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, topic);
            return KafkaTestUtils.getSingleRecord(consumer, topic, Duration.ofSeconds(30));
        }
    }
}
