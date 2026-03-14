package com.mariabean.reservation.search.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FacilityEmbeddingPayload {
    private String facilityId;
    private String text;  // name + category + description + specialties 합친 텍스트
}
