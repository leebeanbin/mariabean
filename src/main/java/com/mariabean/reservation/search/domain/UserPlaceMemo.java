package com.mariabean.reservation.search.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_place_memos",
       indexes = {
           @Index(name = "idx_upm_member_place", columnList = "memberId, placeId")
       })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserPlaceMemo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 200)
    private String placeId;

    @Column(length = 200)
    private String placeName;

    @Column(length = 1000)
    private String content;

    @Column(nullable = false)
    @Builder.Default
    private int boostScore = 0;   // 0~5

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    public void update(String content, int boostScore) {
        this.content = content;
        this.boostScore = Math.max(0, Math.min(5, boostScore));
        this.updatedAt = LocalDateTime.now();
    }
}
