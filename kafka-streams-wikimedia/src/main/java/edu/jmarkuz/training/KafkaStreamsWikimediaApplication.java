package edu.jmarkuz.training;

import edu.jmarkuz.training.processor.BotCountStreamBuilder;
import edu.jmarkuz.training.processor.EventCountTimeseriesBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Properties;

@Slf4j
@SpringBootApplication
public class KafkaStreamsWikimediaApplication implements CommandLineRunner {

    @Value("${spring.kafka.topic.name}")
    private String topicName;

    public static void main(String[] args) {
        SpringApplication.run(KafkaStreamsWikimediaApplication.class, args);
    }

    private KafkaStreams setupKafkaStreams() {
        final Topology appTopology = buildTopology();

        log.info("Topology: {}", appTopology.describe());

        Properties properties = new Properties();
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092");
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "wikimedia-stats-application");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        properties.put(StreamsConfig.consumerPrefix(ConsumerConfig.METADATA_MAX_AGE_CONFIG), 60000);
        properties.put(StreamsConfig.producerPrefix(ProducerConfig.METADATA_MAX_AGE_CONFIG), 60000);

        return new KafkaStreams(appTopology, new StreamsConfig(properties));
    }

    private Topology buildTopology() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> kStream = builder.stream(topicName);

        var botCountStreamBuilder = new BotCountStreamBuilder(kStream);
        botCountStreamBuilder.setup();

        var eventCountTimeseriesBuilder = new EventCountTimeseriesBuilder(kStream);
        eventCountTimeseriesBuilder.setup();

        return builder.build();
    }

    @Override
    public void run(String... args) {
        try (KafkaStreams kafkaStreams = setupKafkaStreams()) {
            kafkaStreams.start();
        }
    }
}
