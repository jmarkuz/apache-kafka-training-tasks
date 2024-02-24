package edu.jmarkuz.training;

import edu.jmarkuz.training.flink.pipeline.FlinkWikimediaDataPipeline;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KafkaFlinkApplication implements CommandLineRunner {
    public static void main(String[] args) {
        SpringApplication.run(KafkaFlinkApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        final String inputTopic = "wikimedia.recentchange";
        final String outputTopic = "wikimedia.recentchange.flink.output";
        final String address = "localhost:9092";
        final String consumerGroup = "wikimedia_username_to_upper";

        var dataPipeline = new FlinkWikimediaDataPipeline(inputTopic, outputTopic, address, consumerGroup);
        dataPipeline.botsVsUsersNamesToUpperCase();
    }
}
