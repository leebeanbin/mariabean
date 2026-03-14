package com.mariabean.reservation.search.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPlaceMemoRepository extends JpaRepository<UserPlaceMemo, Long> {

    List<UserPlaceMemo> findByMemberIdAndPlaceIdIn(Long memberId, List<String> placeIds);

    Optional<UserPlaceMemo> findByMemberIdAndPlaceId(Long memberId, String placeId);
}
