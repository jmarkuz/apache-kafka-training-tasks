package edu.jmarkuz.training.producer;

import com.launchdarkly.eventsource.EventSource;
import edu.jmarkuz.training.handler.WikimediaChangesHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaChangesProducer {

    private static final String WIKIMEDIA_URL = "https://stream.wikimedia.org/v2/stream/recentchange";

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${spring.kafka.topic.name}")
    private String topic;

    public void sendMessage() {
        log.info("Kafka changes producer message(s) sending...");
        var eventHandler = new WikimediaChangesHandler(topic, kafkaTemplate);
        try (EventSource eventSource = new EventSource.Builder(eventHandler, URI.create(WIKIMEDIA_URL)).build()) {
            eventSource.start();

            // wait 5 minutes for testing
            try {
                TimeUnit.MINUTES.sleep(5);
            } catch (InterruptedException e) {
                log.error("Interrupted with an exception: ", e);
                throw new RuntimeException(e);
            }
        }
    }
}
