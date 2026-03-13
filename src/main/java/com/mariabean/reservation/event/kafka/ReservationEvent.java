package com.mariabean.reservation.event.kafka;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 예약 상태 변경 Kafka 이벤트.
 * Outbox를 통해 reservation-events 토픽으로 발행됩니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationEvent {

    public static final String TOPIC = "reservation-events";
    public static final String EVENT_CREATED = "CREATED";
    public static final String EVENT_CANCELLED = "CANCELLED";
    public static final String EVENT_CONFIRMED = "CONFIRMED";

    private Long reservationId;
    private Long memberId;
    private String resourceItemId;
    private String facilityId;
    private String seatLabel;
    private String eventType; // CREATED, CANCELLED
    private LocalDateTime occurredAt;
}
