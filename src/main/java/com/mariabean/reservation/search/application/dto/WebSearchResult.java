package com.mariabean.reservation.search.application.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WebSearchResult {
    private List<WebSnippet> snippets;
    private List<String> imageUrls;

    @Getter
    @Builder
    public static class WebSnippet {
        private String title;
        private String url;
        private String content;
        private double score;
    }

    public static WebSearchResult empty() {
        return WebSearchResult.builder()
                .snippets(List.of())
                .imageUrls(List.of())
                .build();
    }
}
