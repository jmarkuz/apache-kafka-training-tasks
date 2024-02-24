package edu.jmarkuz.training.flink.pipeline;

import edu.jmarkuz.training.flink.connector.KafkaConsumer;
import edu.jmarkuz.training.flink.connector.KafkaProducer;
import edu.jmarkuz.training.model.WikimediaData;
import lombok.RequiredArgsConstructor;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

@RequiredArgsConstructor
public class FlinkWikimediaDataPipeline {

    private final String inputTopic;
    private final String outputTopic;
    private final String kafkaAddress;
    private final String kafkaGroup;

    public void botsVsUsersNamesToUpperCase() throws Exception {

        var flinkKafkaConsumer = KafkaConsumer.createWikimediaDataConsumer(inputTopic, kafkaAddress, kafkaGroup);
        flinkKafkaConsumer.setStartFromEarliest();

        var environment = StreamExecutionEnvironment.getExecutionEnvironment();
        DataStream<WikimediaData> stringInputStream = environment.addSource(flinkKafkaConsumer);

        var flinkKafkaProducer = KafkaProducer.createWikimediaDataProducer(outputTopic, kafkaAddress);

        stringInputStream
                .map(new ChangeNameMapper())
                .addSink(flinkKafkaProducer);

        environment.execute();
    }

}
