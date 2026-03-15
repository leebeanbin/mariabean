package com.mariabean.reservation.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

@Configuration
@EnableJpaRepositories(basePackages = {
    "com.mariabean.reservation.event.outbox.infrastructure.persistence",
    "com.mariabean.reservation.member.infrastructure.persistence",
    "com.mariabean.reservation.reservation.infrastructure.persistence",
    "com.mariabean.reservation.payment.infrastructure.persistence",
    "com.mariabean.reservation.email.infrastructure.persistence"
})
@EnableMongoRepositories(basePackages = {
    "com.mariabean.reservation.facility.infrastructure.persistence"
})
@EnableRedisRepositories(basePackages = {
    "com.mariabean.reservation.auth.infrastructure.persistence"
})
@EnableElasticsearchRepositories(basePackages = {
    "com.mariabean.reservation.search.infrastructure.persistence"
})
public class DataConfig {
}
