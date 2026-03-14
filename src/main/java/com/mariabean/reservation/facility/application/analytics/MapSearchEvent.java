package com.mariabean.reservation.facility.application.analytics;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MapSearchEvent {
    public static final String TOPIC = "map-search-events";

    private String query;
    private String providerOrder;
    private String providerUsed;
    private String viewportHash;
    private String hitStatus; // HIT or MISS
    private String queryType; // address_like or place_like
    private String topMatchType; // INTERNAL, ADDRESS, PLACE
    private boolean suggestionClicked;
    private int resultCount;
    private boolean cacheHit;
    private boolean fallbackUsed;
    private long elapsedMs;
    private long timestamp;

    // AI 분석 필드
    private List<String> aiKeywords;      // qwen2.5:7b가 추출한 키워드
    private String intentType;            // hospital | facility | nearby | general
    private boolean hybridSearchUsed;     // kNN + BM25 사용 여부
    private boolean tavilyUsed;           // Tavily 웹 검색 사용 여부
}

