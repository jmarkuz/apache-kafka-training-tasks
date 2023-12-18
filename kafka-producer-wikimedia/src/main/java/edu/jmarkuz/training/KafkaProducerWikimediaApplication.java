package edu.jmarkuz.training;

import edu.jmarkuz.training.producer.KafkaChangesProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KafkaProducerWikimediaApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(KafkaProducerWikimediaApplication.class, args);
    }

    @Autowired
    private KafkaChangesProducer producer;

    @Override
    public void run(String... args) throws Exception {
        producer.sendMessage();
    }
}
