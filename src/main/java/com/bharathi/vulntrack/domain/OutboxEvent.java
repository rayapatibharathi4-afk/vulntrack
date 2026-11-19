package com.bharathi.vulntrack.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A message waiting to be sent to Kafka. Written in the same transaction as the data it describes. */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "message_key", nullable = false, length = 100)
    private String messageKey;

    @Column(name = "payload", nullable = false, length = 200)
    private String payload;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected OutboxEvent() {
        // required by JPA
    }

    public OutboxEvent(String topic, String messageKey, String payload, String correlationId, Instant createdAt) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.correlationId = correlationId;
        this.createdAt = createdAt;
    }

    /** The broker has acknowledged the message. */
    public void markSent(Instant at) {
        this.sentAt = at;
    }

    public Long getId() { return id; }
    public String getTopic() { return topic; }
    public String getMessageKey() { return messageKey; }
    public String getPayload() { return payload; }
    public String getCorrelationId() { return correlationId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
}
