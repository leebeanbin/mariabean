package com.mariabean.reservation.reservation.infrastructure.persistence;

import com.mariabean.reservation.reservation.domain.ReservationStatus;
import com.mariabean.reservation.global.persistence.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservations", indexes = {
        @Index(name = "idx_resource_time", columnList = "resourceItemId, startTime, endTime"),
        @Index(name = "idx_member", columnList = "memberId"),
        @Index(name = "idx_status_created", columnList = "status, createdAt"),
        @Index(name = "idx_resource_status", columnList = "resourceItemId, status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Setter
    private Long version;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private String resourceItemId;

    @Column(nullable = false)
    private String facilityId;

    @Column(length = 120)
    private String seatLabel;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Builder
    public ReservationJpaEntity(Long id, Long version, Long memberId, String resourceItemId, String facilityId,
            String seatLabel,
            LocalDateTime startTime, LocalDateTime endTime,
            ReservationStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.version = version;
        this.memberId = memberId;
        this.resourceItemId = resourceItemId;
        this.facilityId = facilityId;
        this.seatLabel = seatLabel;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }
}
