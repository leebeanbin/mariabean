package com.mariabean.reservation.search.api;

import com.mariabean.reservation.auth.domain.UserPrincipal;
import com.mariabean.reservation.search.application.UserPlaceMemoService;
import com.mariabean.reservation.search.domain.UserPlaceMemo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search/memo")
@RequiredArgsConstructor
public class UserPlaceMemoController {

    private final UserPlaceMemoService memoService;

    @PostMapping
    public ResponseEntity<UserPlaceMemo> create(
            @RequestBody MemoRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPlaceMemo memo = memoService.saveMemo(
                principal.getMemberId(),
                request.getPlaceId(),
                request.getPlaceName(),
                request.getContent(),
                request.getBoost());
        return ResponseEntity.ok(memo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserPlaceMemo> update(
            @PathVariable Long id,
            @RequestBody MemoRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPlaceMemo memo = memoService.updateMemo(
                id, principal.getMemberId(), request.getContent(), request.getBoost());
        return ResponseEntity.ok(memo);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        memoService.deleteMemo(id, principal.getMemberId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<UserPlaceMemo> get(
            @RequestParam String placeId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserPlaceMemo memo = memoService.getMemo(principal.getMemberId(), placeId);
        if (memo == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(memo);
    }

    @Getter
    @NoArgsConstructor
    public static class MemoRequest {
        private String placeId;
        private String placeName;
        private String content;
        private int boost;
    }
}
