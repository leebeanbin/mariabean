package com.mariabean.reservation.search.application.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SearchQueryAnalysis {
    private String originalQuery;
    private List<String> keywords;
    private String intentType;      // hospital | facility | nearby | general
    private String locationHint;    // 위치 힌트 (예: "강남", "서울")
    private boolean needsWebSearch;
}
