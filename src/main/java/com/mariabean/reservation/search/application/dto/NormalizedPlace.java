package com.mariabean.reservation.search.application.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NormalizedPlace {
    private String id;
    private String source;         // "internal" | "kakao" | "google" | "tavily"
    private String name;
    private String category;
    private String address;
    private double latitude;
    private double longitude;
    private List<String> photoUrls;
    private Double rating;
    private Integer reviewCount;
    private List<String> tags;
    private Boolean openNow;
    private String webSnippet;     // 200자 이내 truncate
    private String citationUrl;
}
