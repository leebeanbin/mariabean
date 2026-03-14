package com.mariabean.reservation.facility.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariabean.reservation.facility.application.analytics.MapAnalyticsQueryService;
import com.mariabean.reservation.facility.application.analytics.MapSearchEventPublisher;
import com.mariabean.reservation.facility.application.dto.PlaceSearchResult;
import com.mariabean.reservation.facility.domain.Facility;
import com.mariabean.reservation.facility.domain.FacilityRepository;
import com.mariabean.reservation.facility.infrastructure.external.map.GooglePlacesClient;
import com.mariabean.reservation.facility.infrastructure.external.map.KakaoLocalSearchClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MapServiceTest {

    @Mock
    private FacilityRepository facilityRepository;
    @Mock
    private KakaoLocalSearchClient kakaoLocalSearchClient;
    @Mock
    private GooglePlacesClient googlePlacesClient;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private MapSearchEventPublisher mapSearchEventPublisher;
    @Mock
    private MapAnalyticsQueryService mapAnalyticsQueryService;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private MapService mapService;

    private void stubNoCacheReadOnly() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
    }

    private void stubNoCacheWithLock() {
        stubNoCacheReadOnly();
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("상세조회는 내부 시설(placeId) 매칭을 우선한다")
    void getPlaceDetails_prefersInternalFacility() {
        stubNoCacheReadOnly();
        Facility facility = Facility.builder()
                .id("f-1")
                .name("내부 시설")
                .placeId("place-1")
                .address("서울시 중구")
                .latitude(37.0)
                .longitude(127.0)
                .build();
        when(facilityRepository.findByPlaceId("place-1")).thenReturn(Optional.of(facility));

        PlaceSearchResult result = mapService.getPlaceDetails("place-1");

        assertThat(result.getProvider()).isEqualTo("INTERNAL");
        assertThat(result.getSourceFacilityId()).isEqualTo("f-1");
        assertThat(result.getMatchType()).isEqualTo("INTERNAL");
        assertThat(result.getName()).isEqualTo("내부 시설");
    }

    @Test
    @DisplayName("상세조회는 내부가 없으면 외부(Google)로 fallback 한다")
    void getPlaceDetails_fallbacksToGoogleWhenInternalMissing() {
        stubNoCacheReadOnly();
        PlaceSearchResult google = PlaceSearchResult.builder()
                .placeId("g-1")
                .name("외부 장소")
                .address("경기도")
                .latitude(37.1)
                .longitude(127.1)
                .provider("GOOGLE")
                .build();
        when(facilityRepository.findByPlaceId("g-1")).thenReturn(Optional.empty());
        when(facilityRepository.findById("g-1")).thenReturn(Optional.empty());
        when(googlePlacesClient.getPlaceDetails("g-1")).thenReturn(google);

        PlaceSearchResult result = mapService.getPlaceDetails("g-1");

        assertThat(result.getProvider()).isEqualTo("GOOGLE");
        assertThat(result.getName()).isEqualTo("외부 장소");
    }

    @Test
    @DisplayName("검색 결과는 중복 제거되어 반환된다")
    void searchPlaces_deduplicatesResults() {
        stubNoCacheWithLock();
        PlaceSearchResult kakao = PlaceSearchResult.builder()
                .placeId("k-1")
                .name("테스트 장소")
                .address("서울 중구 세종대로")
                .latitude(37.0)
                .longitude(127.0)
                .provider("KAKAO")
                .build();
        PlaceSearchResult google = PlaceSearchResult.builder()
                .placeId("g-1")
                .name("테스트 장소")
                .address("서울 중구 세종대로")
                .latitude(37.0)
                .longitude(127.0)
                .provider("GOOGLE")
                .build();
        when(facilityRepository.findByPlaceId("세종대로")).thenReturn(Optional.empty());
        when(kakaoLocalSearchClient.searchPlaces("세종대로")).thenReturn(List.of(kakao));
        when(googlePlacesClient.searchPlaces("세종대로")).thenReturn(List.of(google));

        List<PlaceSearchResult> results = mapService.searchPlaces("세종대로");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("테스트 장소");
    }

    @Test
    @DisplayName("주소형 결과가 장소형 결과보다 우선 반환된다")
    void searchPlaces_prioritizesAddressMatchType() {
        stubNoCacheWithLock();
        PlaceSearchResult placeLike = PlaceSearchResult.builder()
                .placeId("k-place")
                .name("화성시청")
                .address("화성 시청 광장")
                .latitude(37.2)
                .longitude(126.8)
                .provider("KAKAO")
                .matchType("PLACE")
                .build();
        PlaceSearchResult addressLike = PlaceSearchResult.builder()
                .placeId("g-address")
                .name("경기도 화성시 효행로 123")
                .address("경기도 화성시 효행로 123")
                .latitude(37.21)
                .longitude(126.81)
                .provider("GOOGLE")
                .matchType("ADDRESS")
                .build();
        when(facilityRepository.findByPlaceId("경기도 화성시")).thenReturn(Optional.empty());
        when(kakaoLocalSearchClient.searchPlaces("경기도 화성시")).thenReturn(List.of(placeLike));
        when(googlePlacesClient.searchPlaces("경기도 화성시")).thenReturn(List.of(addressLike));

        List<PlaceSearchResult> results = mapService.searchPlaces("경기도 화성시");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getMatchType()).isEqualTo("ADDRESS");
        assertThat(results.get(1).getMatchType()).isEqualTo("PLACE");
    }
}
