package com.mariabean.reservation.search.application;

import com.mariabean.reservation.search.application.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AISummaryService {

    private final ChatClient chatClient;

    public AISummary summarize(String query,
                               List<AISearchResult> internalResults,
                               WebSearchResult webResults) {
        if (internalResults.isEmpty()) {
            return AISummary.builder()
                    .summary(query + "에 대한 검색 결과가 없습니다.")
                    .citations(List.of())
                    .build();
        }

        try {
            String prompt = """
                    다음 검색 결과들을 종합해 사용자 질문에 답하는 간결한 요약을 작성하세요.
                    각 문장 끝에 [출처 번호]를 붙이세요.
                    JSON 형식으로만 응답하세요: {"summary":"...","citations":[{"number":1,"title":"...","url":"..."}]}

                    질문: %s
                    내부 결과: %s
                    웹 결과: %s
                    """.formatted(query, formatInternal(internalResults), formatWeb(webResults));

            String json = chatClient.prompt().user(prompt).call().content();
            return parseAISummary(json, internalResults, webResults);
        } catch (Exception e) {
            log.warn("[AISummary] 요약 생성 실패: {}", e.getMessage());
            return buildFallbackSummary(query, internalResults, webResults);
        }
    }

    private String formatInternal(List<AISearchResult> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(results.size(), 5); i++) {
            AISearchResult r = results.get(i);
            sb.append("[%d] %s (%s) 평점: %s".formatted(
                    i + 1, r.getName(), r.getAddress(),
                    r.getRating() != null ? r.getRating().toString() : "정보없음"));
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatWeb(WebSearchResult web) {
        if (web == null || web.getSnippets() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (WebSearchResult.WebSnippet s : web.getSnippets()) {
            sb.append("[웹] %s: %s\n".formatted(s.getTitle(), s.getContent()));
        }
        return sb.toString();
    }

    private AISummary parseAISummary(String json,
                                     List<AISearchResult> internalResults,
                                     WebSearchResult webResults) {
        try {
            // JSON에서 summary 추출
            int summaryStart = json.indexOf("\"summary\":\"") + 11;
            int summaryEnd = json.indexOf("\",\"citations\"");
            if (summaryStart < 11 || summaryEnd < 0) {
                return buildFallbackSummary("", internalResults, webResults);
            }
            String summary = json.substring(summaryStart, summaryEnd).replace("\\n", "\n");

            List<AISummary.Citation> citations = new ArrayList<>();
            Pattern citationPattern = Pattern.compile(
                    "\\{\"number\":(\\d+),\"title\":\"([^\"]*)\",\"url\":\"([^\"]*)\"\\}");
            Matcher m = citationPattern.matcher(json);
            while (m.find()) {
                citations.add(AISummary.Citation.builder()
                        .number(Integer.parseInt(m.group(1)))
                        .title(m.group(2))
                        .url(m.group(3))
                        .build());
            }

            return AISummary.builder().summary(summary).citations(citations).build();
        } catch (Exception e) {
            return buildFallbackSummary("", internalResults, webResults);
        }
    }

    private AISummary buildFallbackSummary(String query,
                                           List<AISearchResult> results,
                                           WebSearchResult web) {
        List<AISummary.Citation> citations = new ArrayList<>();
        for (int i = 0; i < Math.min(results.size(), 5); i++) {
            citations.add(AISummary.Citation.builder()
                    .number(i + 1)
                    .title(results.get(i).getName())
                    .url("")
                    .build());
        }
        if (web != null && web.getSnippets() != null) {
            for (WebSearchResult.WebSnippet s : web.getSnippets()) {
                citations.add(AISummary.Citation.builder()
                        .number(citations.size() + 1)
                        .title(s.getTitle())
                        .url(s.getUrl())
                        .build());
            }
        }
        String summary = "총 %d개의 검색 결과가 있습니다.".formatted(results.size());
        return AISummary.builder().summary(summary).citations(citations).build();
    }
}
