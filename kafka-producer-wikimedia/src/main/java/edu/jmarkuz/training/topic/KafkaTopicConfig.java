package edu.jmarkuz.training.topic;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Slf4j
@Configuration
public class KafkaTopicConfig {

    @Value("${spring.kafka.topic.name}")
    private String wikimediaTopic; // "wikimedia.recentchange"

    @Bean
    public NewTopic wikimediaTopic() {
        return TopicBuilder.name(wikimediaTopic).build();
    }
}
