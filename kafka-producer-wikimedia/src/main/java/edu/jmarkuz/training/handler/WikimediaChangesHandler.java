package edu.jmarkuz.training.handler;

import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.MessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WikimediaChangesHandler implements EventHandler {

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

        kafkaTemplate.send(topic, messageEvent.getData());
    }

    @Override
    public void onComment(String comment) {
        log.info("Received a comment line from the stream");
    }

    @Override
    public void onError(Throwable t) {
        log.error("Exception occur on the socket connection:", t);
    }
}
