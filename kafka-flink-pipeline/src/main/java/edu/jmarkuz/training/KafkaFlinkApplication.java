package edu.jmarkuz.training;

import edu.jmarkuz.training.flink.pipeline.FlinkWikimediaDataPipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

@Slf4j
@SpringBootApplication
public class KafkaFlinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaFlinkApplication.class, args);
    }

    @Autowired
    FlinkWikimediaDataPipeline dataPipeline;

    @EventListener(value = ContextRefreshedEvent.class)
    void runFlinkWikimediaDataPipeline() throws Exception {
        log.info("Running Wikimedia -> Flink data pipeline");
        dataPipeline.run();
    }
}
