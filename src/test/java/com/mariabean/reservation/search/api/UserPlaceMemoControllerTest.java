package com.mariabean.reservation.search.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariabean.reservation.auth.infrastructure.config.SecurityConfig;
import com.mariabean.reservation.auth.infrastructure.security.JwtAuthenticationFilter;
import com.mariabean.reservation.global.config.DataConfig;
import com.mariabean.reservation.global.config.JpaAuditingConfig;
import com.mariabean.reservation.global.config.MongoAuditingConfig;
import com.mariabean.reservation.search.application.UserPlaceMemoService;
import com.mariabean.reservation.search.domain.UserPlaceMemo;
import com.mariabean.reservation.support.TestSecurityConfig;
import com.mariabean.reservation.support.WithMockUserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = UserPlaceMemoController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JpaAuditingConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = MongoAuditingConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = DataConfig.class)
        })
@Import(TestSecurityConfig.class)
class UserPlaceMemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserPlaceMemoService memoService;

    // ──────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────

    private UserPlaceMemo memo(Long id, String placeId, String content, int boost) {
        return UserPlaceMemo.builder()
                .id(id)
                .memberId(1L)
                .placeId(placeId)
                .placeName("테스트 장소")
                .content(content)
                .boostScore(boost)
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────
    // POST /api/v1/search/memo
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUserPrincipal(memberId = 1L)
    @DisplayName("POST /api/v1/search/memo — 인증된 사용자가 메모를 생성하면 200과 메모를 반환한다")
    void createMemo_authenticated_returns200() throws Exception {
        String body = """
                {"placeId":"kakao-123","placeName":"강남 정형외과","content":"좋은 병원","boost":3}
                """;
        UserPlaceMemo saved = memo(1L, "kakao-123", "좋은 병원", 3);

        given(memoService.saveMemo(eq(1L), eq("kakao-123"), eq("강남 정형외과"), eq("좋은 병원"), eq(3)))
                .willReturn(saved);

        mockMvc.perform(post("/api/v1/search/memo")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value("kakao-123"))
                .andExpect(jsonPath("$.content").value("좋은 병원"));
    }

    // ──────────────────────────────────────────────────────────────────────
    // PUT /api/v1/search/memo/{id}
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUserPrincipal(memberId = 1L)
    @DisplayName("PUT /api/v1/search/memo/1 — 인증된 사용자가 메모를 수정하면 200과 수정된 메모를 반환한다")
    void updateMemo_authenticated_returns200() throws Exception {
        String body = """
                {"placeId":"kakao-123","placeName":"강남 정형외과","content":"수정된 내용","boost":4}
                """;
        UserPlaceMemo updated = memo(1L, "kakao-123", "수정된 내용", 4);

        given(memoService.updateMemo(eq(1L), eq(1L), eq("수정된 내용"), eq(4)))
                .willReturn(updated);

        mockMvc.perform(put("/api/v1/search/memo/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("수정된 내용"))
                .andExpect(jsonPath("$.boostScore").value(4));
    }

    // ──────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/search/memo/{id}
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUserPrincipal(memberId = 1L)
    @DisplayName("DELETE /api/v1/search/memo/1 — 인증된 사용자가 메모를 삭제하면 204를 반환한다")
    void deleteMemo_authenticated_returns204() throws Exception {
        doNothing().when(memoService).deleteMemo(1L, 1L);

        mockMvc.perform(delete("/api/v1/search/memo/1")
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    // ──────────────────────────────────────────────────────────────────────
    // GET /api/v1/search/memo?placeId=...
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUserPrincipal(memberId = 1L)
    @DisplayName("GET /api/v1/search/memo?placeId=kakao-123 — 메모가 존재하면 200과 메모를 반환한다")
    void getMemo_exists_returns200() throws Exception {
        UserPlaceMemo m = memo(5L, "kakao-123", "메모 내용", 2);
        given(memoService.getMemo(1L, "kakao-123")).willReturn(m);

        mockMvc.perform(get("/api/v1/search/memo")
                        .param("placeId", "kakao-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value("kakao-123"))
                .andExpect(jsonPath("$.content").value("메모 내용"));
    }

    @Test
    @WithMockUserPrincipal(memberId = 1L)
    @DisplayName("GET /api/v1/search/memo?placeId=kakao-999 — 메모가 없으면 404를 반환한다")
    void getMemo_notFound_returns404() throws Exception {
        given(memoService.getMemo(1L, "kakao-999")).willReturn(null);

        mockMvc.perform(get("/api/v1/search/memo")
                        .param("placeId", "kakao-999"))
                .andExpect(status().isNotFound());
    }
}
