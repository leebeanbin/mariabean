package com.mariabean.reservation.search.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AISummary {
    private String summary;
    private List<Citation> citations;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Citation {
        private int number;
        private String title;
        private String url;
    }
}
