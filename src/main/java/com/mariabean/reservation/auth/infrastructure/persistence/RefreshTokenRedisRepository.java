package com.mariabean.reservation.auth.infrastructure.persistence;

import org.springframework.data.repository.CrudRepository;

public interface RefreshTokenRedisRepository extends CrudRepository<RefreshTokenRedisEntity, String> {
}
