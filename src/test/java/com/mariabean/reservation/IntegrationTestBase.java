package com.mariabean.reservation;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests using Testcontainers.
 * Provides PostgreSQL, MongoDB, Redis, Kafka, and Elasticsearch containers
 * that are shared across all integration test classes.
 */
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("reservation_test_db")
                    .withUsername("test")
                    .withPassword("test");

    static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    // Standard Elasticsearch image (without nori plugin).
    // ResourceItemSearchDocument uses createIndex=false so index creation is skipped,
    // preventing startup failure on missing nori analyzer.
    static final ElasticsearchContainer ELASTICSEARCH =
            new ElasticsearchContainer(
                    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.13.4"))
                    .withEnv("xpack.security.enabled", "false");

    static {
        POSTGRES.start();
        MONGO.start();
        REDIS.start();
        KAFKA.start();
        ELASTICSEARCH.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // MongoDB
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);

        // Redis
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        // Elasticsearch
        registry.add("spring.elasticsearch.uris",
                () -> "http://" + ELASTICSEARCH.getHttpHostAddress());

        // JPA
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // Required placeholders for application.yml in test context
        registry.add("jwtSecret", () -> "test-jwt-secret-key-at-least-32-bytes-long");
        registry.add("googleClientId", () -> "test-google-client-id");
        registry.add("googleClientSecret", () -> "test-google-client-secret");
        registry.add("kakaoClientId", () -> "test-kakao-client-id");
        registry.add("kakaoClientSecret", () -> "test-kakao-client-secret");
        registry.add("kafkaBootstrapServers", KAFKA::getBootstrapServers);
        registry.add("elasticsearchUri", () -> "http://" + ELASTICSEARCH.getHttpHostAddress());

        // AI 검색 — 통합 테스트에서는 Ollama를 Mock으로 대체
        // (실제 Ollama 컨테이너 기동 없이 빠른 테스트 실행)
        registry.add("spring.ai.ollama.base-url", () -> "http://localhost:11434");
        registry.add("ollamaBaseUrl", () -> "http://localhost:11434");
        registry.add("spring.ai.tavily.api-key", () -> "");
        registry.add("TAVILY_API_KEY", () -> "");
        registry.add("app.search.ollama-timeout-ms", () -> "1000");
        registry.add("app.search.tavily-timeout-ms", () -> "1000");
        registry.add("app.search.photo-max-count", () -> "4");
        registry.add("EMAIL_ADMIN_MEMBER_ID", () -> "0");
        registry.add("NOTIFICATION_CHANNEL", () -> "log");
    }
}
