package edu.jmarkuz.training.flink.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.jmarkuz.training.model.WikimediaData;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.serialization.SerializationSchema;

@Slf4j
public class WikimediaDataSerializationSchema implements SerializationSchema<WikimediaData> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public byte[] serialize(WikimediaData wikimediaData) {
        try {
            String json = objectMapper.writeValueAsString(wikimediaData);
            return json.getBytes();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON", e);
        }
        return new byte[0];
    }
}
