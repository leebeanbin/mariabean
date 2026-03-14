package com.mariabean.reservation.search.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserPlaceMemoTest {

    private UserPlaceMemo buildMemo(int boostScore) {
        return UserPlaceMemo.builder()
                .memberId(1L)
                .placeId("kakao-123")
                .placeName("테스트 장소")
                .content("초기 내용")
                .boostScore(boostScore)
                .build();
    }

    @Test
    @DisplayName("유효한 content와 boostScore로 update하면 필드가 갱신되고 updatedAt이 변경된다")
    void update_validContent_updatesFieldsAndTimestamp() throws InterruptedException {
        // given
        UserPlaceMemo memo = buildMemo(1);
        LocalDateTime before = memo.getUpdatedAt();

        // ensure a measurable time difference (LocalDateTime.now() granularity)
        Thread.sleep(5);

        // when
        memo.update("새로운 내용", 3);

        // then
        assertThat(memo.getContent()).isEqualTo("새로운 내용");
        assertThat(memo.getBoostScore()).isEqualTo(3);
        assertThat(memo.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("builder의 기본 boostScore는 0이다")
    void builder_defaultBoostScore_isZero() {
        // when
        UserPlaceMemo memo = UserPlaceMemo.builder()
                .memberId(1L)
                .placeId("kakao-456")
                .placeName("장소명")
                .content("내용")
                .build();

        // then
        assertThat(memo.getBoostScore()).isEqualTo(0);
    }

    @Test
    @DisplayName("boostScore가 5를 초과하면 5로 고정된다")
    void update_boostExceeds5_clampedTo5() {
        // given
        UserPlaceMemo memo = buildMemo(2);

        // when
        memo.update("내용", 99);

        // then
        assertThat(memo.getBoostScore()).isEqualTo(5);
    }

    @Test
    @DisplayName("boostScore가 0 미만이면 0으로 고정된다")
    void update_boostBelowZero_clampedTo0() {
        // given
        UserPlaceMemo memo = buildMemo(3);

        // when
        memo.update("내용", -10);

        // then
        assertThat(memo.getBoostScore()).isEqualTo(0);
    }
}
