package com.mariabean.reservation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ReactiveElasticsearchClientAutoConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
    ReactiveElasticsearchRepositoriesAutoConfiguration.class,
    ReactiveElasticsearchClientAutoConfiguration.class
})
@EnableScheduling
@EnableJpaAuditing
@EnableMongoAuditing
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
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
public class ReservationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReservationApplication.class, args);
    }

}
