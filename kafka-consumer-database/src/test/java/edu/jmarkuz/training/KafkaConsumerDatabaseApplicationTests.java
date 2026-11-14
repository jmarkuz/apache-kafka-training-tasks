package edu.jmarkuz.training;

import edu.jmarkuz.training.entity.WikimediaData;
import edu.jmarkuz.training.repository.WikimediaDataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end test of the consumer: a message published to Kafka must be persisted to PostgreSQL.
 */
@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create")
class KafkaConsumerDatabaseApplicationTests {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:14");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private WikimediaDataRepository repository;

    @Value("${spring.kafka.topic.name}")
    private String topic;

    @Test
    void persistsConsumedEventsToDatabase() {
        List<String> events = List.of(
                "{\"meta\":{\"id\":\"1\"},\"title\":\"first\"}",
                "{\"meta\":{\"id\":\"2\"},\"title\":\"second\"}",
                "{\"meta\":{\"id\":\"3\"},\"title\":\"third\"}");

        events.forEach(event -> kafkaTemplate.send(topic, event));
        kafkaTemplate.flush();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(repository.findAll())
                        .extracting(WikimediaData::getWikimediaData)
                        .containsExactlyInAnyOrderElementsOf(events));
    }
}
