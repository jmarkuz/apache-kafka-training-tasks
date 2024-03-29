package edu.jmarkuz.training.flink.connector;

import edu.jmarkuz.training.flink.schema.WikimediaDataSerializationSchema;
import edu.jmarkuz.training.model.WikimediaData;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;

public class KafkaProducer {
    public static FlinkKafkaProducer<WikimediaData> createWikimediaDataProducer(String kafkaAddress, String outputTopic) {
        return new FlinkKafkaProducer<>(kafkaAddress, outputTopic, new WikimediaDataSerializationSchema());
    }

    public static FlinkKafkaProducer<String> createWikimediaStringDataProducer(String kafkaAddress, String outputTopic) {
        return new FlinkKafkaProducer<>(kafkaAddress, outputTopic, new SimpleStringSchema());
    }
}
