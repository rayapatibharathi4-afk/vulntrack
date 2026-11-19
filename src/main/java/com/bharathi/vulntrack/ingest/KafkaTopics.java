package com.bharathi.vulntrack.ingest;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {

    public static final String SCAN_SUBMITTED = "scan-submitted";
    public static final String SCAN_SUBMITTED_DLT = "scan-submitted.DLT";

    @Bean
    public NewTopic scanSubmittedTopic() {
        return TopicBuilder.name(SCAN_SUBMITTED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic scanSubmittedDeadLetterTopic() {
        return TopicBuilder.name(SCAN_SUBMITTED_DLT).partitions(3).replicas(1).build();
    }
}