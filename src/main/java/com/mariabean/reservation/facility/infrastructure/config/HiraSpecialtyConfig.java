package com.mariabean.reservation.facility.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "hira")
public class HiraSpecialtyConfig {

    private List<SpecialtyEntry> specialties = new ArrayList<>();

    @Getter
    @Setter
    public static class SpecialtyEntry {
        private String code;
        private String name;
        private String kakaoKeyword;
    }

    public String getNameByCode(String code) {
        return specialties.stream()
                .filter(s -> s.getCode().equals(code))
                .map(SpecialtyEntry::getName)
                .findFirst()
                .orElse(code);
    }

    public String getKakaoKeyword(String code) {
        return specialties.stream()
                .filter(s -> s.getCode().equals(code))
                .map(SpecialtyEntry::getKakaoKeyword)
                .findFirst()
                .orElse(null);
    }

    public boolean isValidCode(String code) {
        return specialties.stream().anyMatch(s -> s.getCode().equals(code));
    }

    public List<SpecialtyEntry> getAllSpecialties() {
        return specialties;
    }
}
