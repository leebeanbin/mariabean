package com.mariabean.reservation.search.application.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
@Builder
public class SearchQueryAnalysis {
    private String originalQuery;
    private List<String> keywords;
    private String intentType;      // hospital | facility | nearby | general
    private String locationHint;    // 위치 힌트 (예: "강남", "서울")
    private boolean needsWebSearch;

    /** LLM 분석 타임아웃 또는 실패 시 raw query로 구성한 fallback. */
    public static SearchQueryAnalysis fallback(String query) {
        return SearchQueryAnalysis.builder()
                .originalQuery(query)
                .keywords(Arrays.asList(query.split("\\s+")))
                .intentType("general")
                .locationHint("")
                .needsWebSearch(false)
                .build();
    }
}
