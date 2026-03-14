package com.mariabean.reservation.facility.application;

import com.mariabean.reservation.facility.application.dto.FacilityCreateRequest;
import com.mariabean.reservation.facility.domain.FacilityRepository;
import com.mariabean.reservation.search.application.ElasticsearchSyncService;
import com.mariabean.reservation.facility.domain.Facility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FacilityServiceTest {

    @InjectMocks
    private FacilityService facilityService;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private MapService mapService;

    @Mock
    private ElasticsearchSyncService elasticsearchSyncService;

    @Test
    @DisplayName("Facility Domain Mapping logic succeeds")
    void registerFacility_success() {
        // given
        FacilityCreateRequest request = FacilityCreateRequest.builder()
                .name("Sinchon Severance")
                .category("HOSPITAL")
                .placeId("ChIJ123456789")
                .build();

        Facility mockSaved = Facility.builder()
                .id("f-123")
                .name(request.getName())
                .category(request.getCategory())
                .placeId(request.getPlaceId())
                .ownerMemberId(1L)
                .build();

        given(facilityRepository.save(any(Facility.class))).willReturn(mockSaved);

        // when
        Facility result = facilityService.registerFacility(request, 1L);

        // then
        assertThat(result.getId()).isEqualTo("f-123");
        assertThat(result.getName()).isEqualTo("Sinchon Severance");
        verify(facilityRepository).save(any(Facility.class));
    }
}
