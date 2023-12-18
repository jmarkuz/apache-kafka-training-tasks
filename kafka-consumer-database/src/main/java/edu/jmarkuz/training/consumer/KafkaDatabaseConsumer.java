package edu.jmarkuz.training.consumer;

import edu.jmarkuz.training.entity.WikimediaData;
import edu.jmarkuz.training.repository.WikimediaDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaDatabaseConsumer {

    private final WikimediaDataRepository repository;

    @KafkaListener(topics = "${spring.kafka.topic.name}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String eventMessage) {
        log.info("Event message received: -> {}", eventMessage);

        WikimediaData data = new WikimediaData();
        data.setWikimediaData(eventMessage);

        WikimediaData saved = repository.save(data);

        log.info("Persisted data to PostgresDB: {}",saved);
    }

}
