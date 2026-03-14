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
public class AIResearchResult {
    private String query;
    private AISummary aiSummary;
    private List<AISearchResult> results;
}
