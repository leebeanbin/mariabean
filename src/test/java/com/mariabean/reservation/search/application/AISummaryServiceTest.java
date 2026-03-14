package com.mariabean.reservation.search.application;

import com.mariabean.reservation.search.application.dto.AISearchResult;
import com.mariabean.reservation.search.application.dto.AISummary;
import com.mariabean.reservation.search.application.dto.WebSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AISummaryServiceTest {

    @InjectMocks
    private AISummaryService aiSummaryService;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec promptSpec;

    @Mock
    private ChatClient.CallResponseSpec callSpec;

    // ──────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────

    private AISearchResult result(String name, String address, Double rating) {
        return AISearchResult.builder()
                .id("id-" + name)
                .placeId("place-" + name)
                .name(name)
                .address(address)
                .rating(rating)
                .score(0.7)
                .build();
    }

    private WebSearchResult emptyWeb() {
        return WebSearchResult.empty();
    }

    // ──────────────────────────────────────────────────────────────────────
    // tests
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("LLM이 유효한 JSON을 반환하면 summary와 citations가 올바르게 파싱된다")
    void summarize_llmReturnsValidJson_parsesSummaryAndCitations() {
        // given
        List<AISearchResult> results = List.of(result("강남 정형외과", "서울 강남구", 4.5));
        String llmJson = """
                {"summary":"강남에 위치한 정형외과입니다 [1].","citations":[{"number":1,"title":"강남 정형외과","url":""}]}
                """;

        given(chatClient.prompt()).willReturn(promptSpec);
        given(promptSpec.user(anyString())).willReturn(promptSpec);
        given(promptSpec.call()).willReturn(callSpec);
        given(callSpec.content()).willReturn(llmJson);

        // when
        AISummary summary = aiSummaryService.summarize("강남 정형외과", results, emptyWeb());

        // then
        assertThat(summary.getSummary()).contains("강남에 위치한 정형외과입니다");
        assertThat(summary.getCitations()).hasSize(1);
        assertThat(summary.getCitations().get(0).getNumber()).isEqualTo(1);
        assertThat(summary.getCitations().get(0).getTitle()).isEqualTo("강남 정형외과");
    }

    @Test
    @DisplayName("검색 결과가 비어 있으면 LLM 호출 없이 '검색 결과가 없습니다' 메시지를 반환한다")
    void summarize_emptyResults_returnsNoResultMessage() {
        // when
        AISummary summary = aiSummaryService.summarize("존재하지않는장소", List.of(), emptyWeb());

        // then
        assertThat(summary.getSummary()).contains("검색 결과가 없습니다");
        assertThat(summary.getCitations()).isEmpty();
    }

    @Test
    @DisplayName("LLM이 예외를 던지면 fallback 요약을 반환한다")
    void summarize_llmThrows_returnsFallbackSummary() {
        // given
        List<AISearchResult> results = List.of(result("서울 내과", "서울 중구", 4.0));

        given(chatClient.prompt()).willReturn(promptSpec);
        given(promptSpec.user(anyString())).willReturn(promptSpec);
        given(promptSpec.call()).willReturn(callSpec);
        given(callSpec.content()).willThrow(new RuntimeException("LLM 타임아웃"));

        // when
        AISummary summary = aiSummaryService.summarize("내과", results, emptyWeb());

        // then – fallback: "총 N개의 검색 결과가 있습니다"
        assertThat(summary.getSummary()).contains("1개의 검색 결과");
        assertThat(summary.getCitations()).hasSize(1);
    }

    @Test
    @DisplayName("내부 결과 3개가 있으면 citations 번호가 1, 2, 3으로 올바르게 생성된다")
    void summarize_citationsExtracted_correctNumbers() {
        // given
        List<AISearchResult> results = List.of(
                result("병원A", "서울 강남구", 4.8),
                result("병원B", "서울 서초구", 4.5),
                result("병원C", "서울 송파구", 4.2)
        );

        String llmJson = """
                {"summary":"세 개의 병원이 있습니다 [1][2][3].","citations":[{"number":1,"title":"병원A","url":""},{"number":2,"title":"병원B","url":""},{"number":3,"title":"병원C","url":""}]}
                """;

        given(chatClient.prompt()).willReturn(promptSpec);
        given(promptSpec.user(anyString())).willReturn(promptSpec);
        given(promptSpec.call()).willReturn(callSpec);
        given(callSpec.content()).willReturn(llmJson);

        // when
        AISummary summary = aiSummaryService.summarize("병원 추천", results, emptyWeb());

        // then
        assertThat(summary.getCitations()).hasSize(3);
        assertThat(summary.getCitations()).extracting(AISummary.Citation::getNumber)
                .containsExactly(1, 2, 3);
    }
}
