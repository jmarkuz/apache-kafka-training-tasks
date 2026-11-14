package edu.jmarkuz.training;

import com.launchdarkly.eventsource.MessageEvent;
import edu.jmarkuz.training.handler.WikimediaChangesHandler;
import edu.jmarkuz.training.producer.KafkaChangesProducer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Runs the producer against a real Kafka broker. The Wikimedia stream itself is not used:
 * {@link KafkaChangesProducer} is mocked (it would read the live stream for 5 minutes)
 * and events are pushed through {@link WikimediaChangesHandler} directly.
 */
@Testcontainers
@SpringBootTest
class KafkaProducerWikimediaApplicationTests {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.producer.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @MockitoBean
    private KafkaChangesProducer changesProducer;

    @Autowired
    private WikimediaChangesHandler handler;

    @Value("${spring.kafka.topic.name}")
    private String topic;

    @Test
    void createsWikimediaTopicOnStartup() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            assertThat(admin.listTopics().names().get()).contains(topic);
        }
    }

    @Test
    void topicHasConfiguredPartitionsReplicasAndRetention() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            TopicDescription description = admin.describeTopics(List.of(topic)).allTopicNames().get().get(topic);
            assertThat(description.partitions()).hasSize(3);
            assertThat(description.partitions()).allSatisfy(partition -> assertThat(partition.replicas()).hasSize(1));

            ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topic);
            Config config = admin.describeConfigs(List.of(resource)).all().get().get(resource);
            assertThat(config.get("retention.ms").value()).isEqualTo("604800000");
            assertThat(config.get("cleanup.policy").value()).isEqualTo("delete");
        }
    }

    @Test
    void publishesReceivedEventToTopicWithServerNameAsKey() throws Exception {
        String event = new ClassPathResource("response_example.json")
                .getContentAsString(StandardCharsets.UTF_8);

        handler.onMessage("message", new MessageEvent(event));

        ConsumerRecord<String, String> record = readAll(1).stream()
                .filter(r -> event.equals(r.value()))
                .findFirst().orElseThrow();
        assertThat(record.key()).isEqualTo("commons.wikimedia.org");
    }

    @Test
    void eventsWithSameKeyGoToOnePartitionInOrder() throws Exception {
        List<String> keys = List.of("en.wikipedia.org", "de.wikipedia.org", "fr.wikipedia.org", "www.wikidata.org");
        int perKey = 10;
        // interleave the keys so that ordering is only guaranteed by the partitioning, not by sending order
        for (int seq = 0; seq < perKey; seq++) {
            for (String key : keys) {
                handler.onMessage("message", new MessageEvent(
                        "{\"server_name\":\"" + key + "\",\"seq\":" + seq + ",\"run\":\"ordering\"}"));
            }
        }

        List<ConsumerRecord<String, String>> records = readAll(keys.size() * perKey).stream()
                .filter(r -> r.value().contains("\"run\":\"ordering\""))
                .toList();

        Map<String, List<ConsumerRecord<String, String>>> byKey = records.stream()
                .collect(Collectors.groupingBy(ConsumerRecord::key));
        assertThat(byKey.keySet()).containsExactlyInAnyOrderElementsOf(keys);
        byKey.forEach((key, keyRecords) -> {
            // all events of one key are in a single partition ...
            assertThat(keyRecords.stream().map(ConsumerRecord::partition).distinct()).hasSize(1);
            // ... and are read in the order they were sent
            assertThat(keyRecords.stream().map(r -> seqOf(r.value())).toList())
                    .containsExactlyElementsOf(IntStream.range(0, perKey).boxed().toList());
        });

        // custom partitioner: the hot key is alone on partition 0, the others are never on it
        assertThat(byKey.get("www.wikidata.org").get(0).partition()).isZero();
        keys.stream().filter(key -> !key.equals("www.wikidata.org"))
                .forEach(key -> assertThat(byKey.get(key).get(0).partition()).isNotZero());
    }

    /** Reads the topic from the beginning until at least {@code minRecords} records were received. */
    private List<ConsumerRecord<String, String>> readAll(int minRecords) {
        List<ConsumerRecord<String, String>> all = new ArrayList<>();
        try (Consumer<String, String> consumer = newConsumer()) {
            consumer.subscribe(List.of(topic));
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(all::add);
                return all.size() >= minRecords;
            });
        }
        return all;
    }

    private static int seqOf(String json) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"seq\":(\\d+)").matcher(json);
        assertThat(matcher.find()).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    private static Consumer<String, String> newConsumer() {
        return new DefaultKafkaConsumerFactory<>(Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer()).createConsumer();
    }
}
