package com.mariabean.reservation.search.api;

import com.mariabean.reservation.search.application.AIResearchOrchestrator;
import com.mariabean.reservation.search.application.VisionLocationAnalyzerService;
import com.mariabean.reservation.search.application.dto.AIResearchResult;
import com.mariabean.reservation.search.application.dto.VisionSearchResult;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/search/vision")
@RequiredArgsConstructor
public class VisionSearchController {

    private final VisionLocationAnalyzerService visionAnalyzer;
    private final AIResearchOrchestrator orchestrator;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VisionResearchResponse> analyzeFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "37.5665") double lat,
            @RequestParam(defaultValue = "126.9780") double lng) {
        try {
            VisionSearchResult vision = visionAnalyzer.analyzeImage(
                    file.getBytes(), file.getContentType());

            String query = vision.getSuggestedQuery();
            AIResearchResult research = query != null && !query.isBlank()
                    ? orchestrator.research(query, lat, lng, null)
                    : null;

            return ResponseEntity.ok(new VisionResearchResponse(vision, research));
        } catch (Exception e) {
            log.warn("[Vision] 파일 분석 오류: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/url")
    public ResponseEntity<VisionResearchResponse> analyzeUrl(
            @RequestBody Map<String, String> body,
            @RequestParam(defaultValue = "37.5665") double lat,
            @RequestParam(defaultValue = "126.9780") double lng) {
        String imageUrl = body.get("imageUrl");
        if (imageUrl == null || imageUrl.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            VisionSearchResult vision = visionAnalyzer.analyzeImageUrl(imageUrl);

            String query = vision.getSuggestedQuery();
            AIResearchResult research = query != null && !query.isBlank()
                    ? orchestrator.research(query, lat, lng, null)
                    : null;

            return ResponseEntity.ok(new VisionResearchResponse(vision, research));
        } catch (Exception e) {
            log.warn("[Vision] URL 분석 오류: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @Getter
    @NoArgsConstructor
    public static class VisionResearchResponse {
        private VisionSearchResult vision;
        private AIResearchResult results;

        public VisionResearchResponse(VisionSearchResult vision, AIResearchResult results) {
            this.vision = vision;
            this.results = results;
        }
    }
}
