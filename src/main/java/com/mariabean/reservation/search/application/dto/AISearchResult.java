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
public class AISearchResult {
    private String id;
    private String placeId;
    private String name;
    private String category;
    private String address;
    private Double latitude;
    private Double longitude;
    private List<String> photos;
    private Double rating;
    private Integer reviewCount;
    private List<String> tags;
    private String webSnippet;
    private String webUrl;
    private String userMemo;
    private boolean memoHighlighted;
    private double score;
    private boolean highlighted;
    private Integer distanceMeters;
    private Boolean openNow;
}
