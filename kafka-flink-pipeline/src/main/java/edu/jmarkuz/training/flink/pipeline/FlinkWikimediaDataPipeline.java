package edu.jmarkuz.training.flink.pipeline;

import edu.jmarkuz.training.flink.connector.KafkaConsumer;
import edu.jmarkuz.training.flink.connector.KafkaProducer;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
@AllArgsConstructor
@PropertySource("classpath:/application.yaml")
public class FlinkWikimediaDataPipeline {

    @Value("${kafka.input.topic}")
    private String inputTopic;
    @Value("${kafka.output.topic}")
    private String outputTopic;
    @Value("${kafka.address}")
    private String kafkaAddress;
    @Value("${kafka.consumer.group}")
    private String kafkaGroup;

    public void run() throws Exception {

        var flinkKafkaConsumer = KafkaConsumer.createWikimediaStringDataConsumer(inputTopic, kafkaAddress, kafkaGroup);
        flinkKafkaConsumer.setStartFromEarliest();

        var environment = StreamExecutionEnvironment.getExecutionEnvironment();
        DataStream<String> stringInputStream = environment.addSource(flinkKafkaConsumer);

        var flinkKafkaProducer = KafkaProducer.createWikimediaStringDataProducer(outputTopic, kafkaAddress);

        stringInputStream
                .map(new UserNameStringMapper())
                .addSink(flinkKafkaProducer);

        environment.execute();
    }

}
