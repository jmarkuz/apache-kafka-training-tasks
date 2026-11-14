package edu.jmarkuz.training.consumer;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.action.get.GetRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.core.CountRequest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Runs {@link OpenSearchConsumer} against real Kafka and OpenSearch containers.
 * The OpenSearch image version matches the high-level REST client used by the module.
 */
@Testcontainers
class OpenSearchConsumerTest {

    private static final String TOPIC = "wikimedia.recentchange";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    @Container
    static final GenericContainer<?> OPENSEARCH = new GenericContainer<>("opensearchproject/opensearch:1.2.4")
            .withEnv("discovery.type", "single-node")
            .withEnv("plugins.security.disabled", "true")
            .withEnv("compatibility.override_main_response_version", "true")
            .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withExposedPorts(9200)
            .waitingFor(Wait.forHttp("/").forPort(9200).withStartupTimeout(Duration.ofMinutes(3)));

    private OpenSearchConsumer consumer;
    private Thread consumerThread;
    private RestHighLevelClient client;

    @BeforeEach
    void startConsumer() {
        String url = "http://" + OPENSEARCH.getHost() + ":" + OPENSEARCH.getMappedPort(9200);
        client = OpenSearchConsumer.createOpenSearchClient(url);
        consumer = new OpenSearchConsumer(KAFKA.getBootstrapServers(), url, TOPIC,
                "test-" + UUID.randomUUID(), "earliest");
        // a dedicated thread: the consumer's shutdown hook joins the thread that called run()
        consumerThread = new Thread(() -> {
            try {
                consumer.run();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }, "opensearch-consumer-test");
        consumerThread.start();
    }

    @AfterEach
    void stopConsumer() throws Exception {
        consumer.stop();
        consumerThread.join(30_000);
        client.close();
    }

    @Test
    void indexesEventsUsingMetaIdAsDocumentId() throws Exception {
        // the same meta.id is sent twice: it must end up as a single document
        send(event("id-1", "first"));
        send(event("id-2", "second"));
        send(event("id-1", "first"));

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertThat(client.exists(new GetRequest("wikimedia", "id-1"), RequestOptions.DEFAULT)).isTrue();
            assertThat(client.exists(new GetRequest("wikimedia", "id-2"), RequestOptions.DEFAULT)).isTrue();
        });

        client.indices().refresh(new org.opensearch.action.admin.indices.refresh.RefreshRequest("wikimedia"),
                RequestOptions.DEFAULT);
        long count = client.count(new CountRequest("wikimedia"), RequestOptions.DEFAULT).getCount();
        assertThat(count).isEqualTo(2);
    }

    private static String event(String id, String title) {
        return "{\"meta\":{\"id\":\"" + id + "\"},\"title\":\"" + title + "\"}";
    }

    private static void send(String value) throws Exception {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.<String, Object>of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()))) {
            producer.send(new ProducerRecord<>(TOPIC, value)).get();
        }
    }
}
