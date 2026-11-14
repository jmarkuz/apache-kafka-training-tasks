package edu.jmarkuz.training;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Runs the real topology (all three statistics builders) against a Kafka broker:
 * events go into {@code wikimedia.recentchange}, statistics are read from the output topics.
 */
@Testcontainers
class KafkaStreamsWikimediaTopologyTest {

    private static final String INPUT_TOPIC = "wikimedia.recentchange";

    // not static: every test gets a fresh broker, so topics and counts never leak between tests
    @Container
    final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.0");

    @TempDir
    Path stateDir;

    private KafkaStreams streams;

    @BeforeEach
    void startStreams() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(INPUT_TOPIC, 1, (short) 1))).all().get();
        }

        Properties props = new Properties();
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.STATE_DIR_CONFIG, stateDir.toString());
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 100);
        props.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);
        props.put(StreamsConfig.consumerPrefix(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG), "earliest");

        streams = new KafkaStreams(KafkaStreamsWikimediaApplication.buildTopology(INPUT_TOPIC), props);
        streams.start();
    }

    @AfterEach
    void stopStreams() {
        streams.close(Duration.ofSeconds(10));
    }

    @Test
    void countsBotAndNonBotChanges() {
        send(change(true, "en.wikipedia.org"));
        send(change(true, "en.wikipedia.org"));
        send(change(false, "de.wikipedia.org"));

        List<String> botStats = consumeUntil("wikimedia.stats.bots",
                values -> values.contains("{\"bot\":2}") && values.contains("{\"non-bot\":1}"));

        assertThat(botStats).contains("{\"bot\":2}", "{\"non-bot\":1}");
    }

    @Test
    void countsChangesPerWebsite() {
        send(change(false, "en.wikipedia.org"));
        send(change(false, "en.wikipedia.org"));
        send(change(true, "de.wikipedia.org"));

        // the exact count depends on window boundaries, so only the websites are asserted
        List<String> websiteStats = consumeUntil("wikimedia.stats.website",
                values -> values.stream().anyMatch(v -> v.contains("\"website\":\"en.wikipedia.org\""))
                        && values.stream().anyMatch(v -> v.contains("\"website\":\"de.wikipedia.org\"")));

        assertThat(websiteStats).isNotEmpty();
    }

    @Test
    void producesEventCountTimeseries() {
        send(change(false, "en.wikipedia.org"));

        List<String> timeseries = consumeUntil("wikimedia.stats.timeseries",
                values -> values.stream().anyMatch(v -> v.contains("\"event_count\":")));

        assertThat(timeseries).isNotEmpty();
    }

    private static String change(boolean bot, String server) {
        return "{\"bot\":" + bot + ",\"server_name\":\"" + server + "\"}";
    }

    private void send(String value) {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.<String, Object>of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()))) {
            producer.send(new ProducerRecord<>(INPUT_TOPIC, value)).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Reads the topic until the collected values satisfy the condition (or the wait times out). */
    private List<String> consumeUntil(String topic, java.util.function.Predicate<List<String>> condition) {
        List<String> values = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()))) {
            consumer.subscribe(List.of(topic));
            await().atMost(Duration.ofSeconds(60)).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    values.add(record.value());
                }
                return condition.test(values);
            });
        }
        return values;
    }
}
