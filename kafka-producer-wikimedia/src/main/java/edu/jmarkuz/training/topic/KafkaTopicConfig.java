package edu.jmarkuz.training.topic;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Slf4j
@Configuration
public class KafkaTopicConfig {

    @Value("${spring.kafka.topic.name}")
    private String wikimediaTopic; // "wikimedia.recentchange"

    @Value("${spring.kafka.topic.partitions}")
    private int partitions;

    @Value("${spring.kafka.topic.replicas}")
    private int replicas;

    @Value("${spring.kafka.topic.retention-ms}")
    private long retentionMs;

    @Bean
    public NewTopic wikimediaTopic() {
        log.info("Declaring topic '{}': partitions={}, replicas={}, retention.ms={}",
                wikimediaTopic, partitions, replicas, retentionMs);
        return TopicBuilder.name(wikimediaTopic)
                .partitions(partitions)
                .replicas(replicas)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(retentionMs))
                .build();
    }
}
