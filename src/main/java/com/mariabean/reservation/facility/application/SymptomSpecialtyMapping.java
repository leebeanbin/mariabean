package com.mariabean.reservation.facility.application;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 증상 ID → HIRA 진료과 코드 매핑.
 * 변경 시 재배포 필요 (DB 저장 불필요).
 */
@Component
public class SymptomSpecialtyMapping {

    private static final Map<String, List<String>> MAPPING = Map.ofEntries(
            Map.entry("headache",    List.of("02", "01")),
            Map.entry("fever",       List.of("01", "11")),
            Map.entry("cough",       List.of("01", "13")),
            Map.entry("stomachache", List.of("01")),
            Map.entry("toothache",   List.of("26")),
            Map.entry("skin",        List.of("14")),
            Map.entry("eyes",        List.of("12", "13")),
            Map.entry("bone",        List.of("05", "20")),
            Map.entry("mental",      List.of("03")),
            Map.entry("womens",      List.of("10")),
            Map.entry("kids",        List.of("11")),
            Map.entry("heart",       List.of("23", "01"))
    );

    public List<String> getCodes(String symptomId) {
        return MAPPING.getOrDefault(symptomId, List.of());
    }

    public boolean isKnownSymptom(String symptomId) {
        return MAPPING.containsKey(symptomId);
    }
}
