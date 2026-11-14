package edu.jmarkuz.training.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.MessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WikimediaChangesHandler implements EventHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String topic;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public WikimediaChangesHandler(@Value("${spring.kafka.topic.name}") final String topic,
                                   final KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void onOpen() {
        log.info("Stream connection has been opened");
    }

    @Override
    public void onClosed() {
        log.info("Stream connection has been closed");
    }

    @Override
    public void onMessage(String event, MessageEvent messageEvent) throws Exception {
        log.info("Received message event: -> {}", messageEvent.getData());

        kafkaTemplate.send(topic, extractKey(messageEvent.getData()), messageEvent.getData());
    }

    @Override
    public void onComment(String comment) {
        log.info("Received a comment line from the stream");
    }

    @Override
    public void onError(Throwable t) {
        log.error("Exception occur on the socket connection:", t);
    }

    /**
     * The message key is the website the change was made on ({@code server_name}, e.g. "en.wikipedia.org").
     * All changes of one website therefore go to the same partition and keep their order.
     * Returns {@code null} (no key, partitions are then chosen by the partitioner) if the event cannot be parsed.
     */
    static String extractKey(String eventJson) {
        try {
            JsonNode serverName = OBJECT_MAPPER.readTree(eventJson).get("server_name");
            return serverName == null || serverName.isNull() ? null : serverName.asText();
        } catch (Exception e) {
            log.warn("Cannot extract message key, sending without key: {}", e.getMessage());
            return null;
        }
    }
}
