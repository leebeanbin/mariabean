package com.mariabean.reservation.search.application.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class VisionSearchResult {
    private String locationDescription;
    private List<String> landmarks;
    private String suggestedQuery;
    private double confidence;
}
