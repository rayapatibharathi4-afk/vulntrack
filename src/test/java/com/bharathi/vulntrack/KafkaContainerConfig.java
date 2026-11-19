package com.bharathi.vulntrack;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Starts a throwaway Kafka broker (the same version as docker-compose) for a test.
 * @ServiceConnection sets spring.kafka.bootstrap-servers to the container.
 */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaContainerConfig {

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.1.0");
    }
}
