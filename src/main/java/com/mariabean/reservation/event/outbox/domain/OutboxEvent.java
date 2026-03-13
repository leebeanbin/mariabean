package com.mariabean.reservation.event.outbox.domain;

import com.mariabean.reservation.global.persistence.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "outbox_events")
public class OutboxEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String aggregateType; // e.g., "PAYMENT", "RESERVATION"

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String eventType; // e.g., "PaymentApprovedEvent"

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload; // JSON representation of the event

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Builder
    public OutboxEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
    }

    public void markAsPublished() {
        this.status = OutboxStatus.PUBLISHED;
    }

    public void markAsFailed() {
        this.status = OutboxStatus.FAILED;
    }

    public enum OutboxStatus {
        PENDING, PUBLISHED, FAILED
    }
}
