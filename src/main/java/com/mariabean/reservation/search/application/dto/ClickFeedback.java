package com.mariabean.reservation.search.application.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ClickFeedback {
    private String placeId;
    private String query;
    private int rank;
}
