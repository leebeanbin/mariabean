package com.mariabean.reservation.search.api;

import com.mariabean.reservation.search.application.FacilityEmbeddingBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/search")
@RequiredArgsConstructor
public class AdminSearchController {

    private final FacilityEmbeddingBatchService embeddingBatchService;

    @GetMapping("/reindex")
    public ResponseEntity<Map<String, Object>> reindex() {
        int count = embeddingBatchService.reindexAll();
        return ResponseEntity.ok(Map.of("message", "임베딩 생성 완료", "count", count));
    }
}
