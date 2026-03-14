package com.mariabean.reservation.search.application;

import com.mariabean.reservation.search.domain.UserPlaceMemo;
import com.mariabean.reservation.search.domain.UserPlaceMemoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserPlaceMemoServiceTest {

    @InjectMocks
    private UserPlaceMemoService userPlaceMemoService;

    @Mock
    private UserPlaceMemoRepository memoRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    // ──────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────

    private UserPlaceMemo buildMemo(Long id, Long memberId, String placeId, String content, int boost) {
        return UserPlaceMemo.builder()
                .id(id)
                .memberId(memberId)
                .placeId(placeId)
                .placeName("테스트 장소")
                .content(content)
                .boostScore(boost)
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────
    // saveMemo()
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("신규 메모 저장 시 빌더로 생성된 메모가 repository에 저장된다")
    void saveMemo_newMemo_savesToRepository() {
        // given
        Long memberId = 1L;
        String placeId = "kakao-123";

        given(memoRepository.findByMemberIdAndPlaceId(memberId, placeId)).willReturn(Optional.empty());
        UserPlaceMemo saved = buildMemo(10L, memberId, placeId, "좋은 병원", 3);
        given(memoRepository.save(any(UserPlaceMemo.class))).willReturn(saved);
        given(redisTemplate.delete(anyString())).willReturn(Boolean.TRUE);

        // when
        UserPlaceMemo result = userPlaceMemoService.saveMemo(memberId, placeId, "테스트 장소", "좋은 병원", 3);

        // then
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getMemberId()).isEqualTo(memberId);
        verify(memoRepository).save(any(UserPlaceMemo.class));
    }

    @Test
    @DisplayName("기존 메모가 있으면 content와 boostScore가 업데이트된다")
    void saveMemo_existingMemo_updatesContent() {
        // given
        Long memberId = 1L;
        String placeId = "kakao-456";
        UserPlaceMemo existing = buildMemo(5L, memberId, placeId, "이전 내용", 1);

        given(memoRepository.findByMemberIdAndPlaceId(memberId, placeId)).willReturn(Optional.of(existing));
        given(memoRepository.save(any(UserPlaceMemo.class))).willReturn(existing);
        given(redisTemplate.delete(anyString())).willReturn(Boolean.TRUE);

        // when
        userPlaceMemoService.saveMemo(memberId, placeId, "테스트 장소", "새 내용", 4);

        // then
        assertThat(existing.getContent()).isEqualTo("새 내용");
        assertThat(existing.getBoostScore()).isEqualTo(4);
    }

    // ──────────────────────────────────────────────────────────────────────
    // updateMemo()
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("메모 소유자가 아닌 멤버가 수정하려 하면 IllegalArgumentException이 발생한다")
    void updateMemo_notOwner_throwsException() {
        // given – memo belongs to memberId=2, caller is memberId=1
        UserPlaceMemo memo = buildMemo(7L, 2L, "kakao-789", "남의 메모", 2);
        given(memoRepository.findById(7L)).willReturn(Optional.of(memo));

        // when / then
        assertThatThrownBy(() -> userPlaceMemoService.updateMemo(7L, 1L, "내용 변경", 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ──────────────────────────────────────────────────────────────────────
    // deleteMemo()
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("소유자가 삭제 요청하면 메모가 성공적으로 삭제된다")
    void deleteMemo_owner_deletesSuccessfully() {
        // given
        Long memberId = 1L;
        UserPlaceMemo memo = buildMemo(3L, memberId, "kakao-111", "삭제할 메모", 0);
        given(memoRepository.findById(3L)).willReturn(Optional.of(memo));
        given(redisTemplate.delete(anyString())).willReturn(Boolean.TRUE);

        // when
        userPlaceMemoService.deleteMemo(3L, memberId);

        // then
        verify(memoRepository).delete(memo);
    }

    // ──────────────────────────────────────────────────────────────────────
    // loadMemos()
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("여러 placeId로 배치 조회하면 placeId를 키로 하는 맵이 반환된다")
    void loadMemos_multipleIds_returnsMapByPlaceId() {
        // given
        Long memberId = 1L;
        List<String> placeIds = List.of("p-1", "p-2");
        UserPlaceMemo m1 = buildMemo(1L, memberId, "p-1", "메모1", 2);
        UserPlaceMemo m2 = buildMemo(2L, memberId, "p-2", "메모2", 4);

        given(memoRepository.findByMemberIdAndPlaceIdIn(memberId, placeIds))
                .willReturn(List.of(m1, m2));

        // when
        Map<String, UserPlaceMemo> result = userPlaceMemoService.loadMemos(memberId, placeIds);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get("p-1").getContent()).isEqualTo("메모1");
        assertThat(result.get("p-2").getBoostScore()).isEqualTo(4);
    }

    @Test
    @DisplayName("memberId가 null이면 loadMemos는 빈 맵을 반환한다")
    void loadMemos_nullMemberId_returnsEmptyMap() {
        // when
        Map<String, UserPlaceMemo> result = userPlaceMemoService.loadMemos(null, List.of("p-1"));

        // then
        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────
    // getMemo()
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("메모가 존재하면 getMemo는 해당 메모를 반환한다")
    void getMemo_exists_returnsMemo() {
        // given
        Long memberId = 1L;
        String placeId = "kakao-999";
        UserPlaceMemo memo = buildMemo(9L, memberId, placeId, "메모 내용", 1);
        given(memoRepository.findByMemberIdAndPlaceId(memberId, placeId)).willReturn(Optional.of(memo));

        // when
        UserPlaceMemo result = userPlaceMemoService.getMemo(memberId, placeId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("메모 내용");
    }

    @Test
    @DisplayName("메모가 존재하지 않으면 getMemo는 null을 반환한다")
    void getMemo_notExists_returnsNull() {
        // given
        given(memoRepository.findByMemberIdAndPlaceId(1L, "kakao-000")).willReturn(Optional.empty());

        // when
        UserPlaceMemo result = userPlaceMemoService.getMemo(1L, "kakao-000");

        // then
        assertThat(result).isNull();
    }
}
