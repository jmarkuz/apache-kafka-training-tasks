package edu.jmarkuz.training.flink.connector;

import edu.jmarkuz.training.flink.schema.WikimediaDataDeserializationSchema;
import edu.jmarkuz.training.model.WikimediaData;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;

import java.util.Properties;

public class KafkaConsumer {

    public static FlinkKafkaConsumer<WikimediaData> createWikimediaDataConsumer(String topic, String kafkaAddress, String kafkaGroup) {
        var properties = new Properties();
        properties.setProperty("bootstrap.servers", kafkaAddress);
        properties.setProperty("group.id", kafkaGroup);

        return new FlinkKafkaConsumer<>(topic, new WikimediaDataDeserializationSchema(), properties);
    }

}
