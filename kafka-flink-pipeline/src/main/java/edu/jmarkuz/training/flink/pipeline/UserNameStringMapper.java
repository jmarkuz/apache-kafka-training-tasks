package edu.jmarkuz.training.flink.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.functions.MapFunction;

@Slf4j
public class UserNameStringMapper implements MapFunction<String, String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String map(String data) {
        /*try {
            JsonNode jsonNode = mapper.readTree(data);
            var bodyData = jsonNode.get("data");
            var userName = bodyData.get("user").asText();

            log.info("Original user name: " + userName);

            return userName;

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Can't parse Json body: " + e);
        }*/
        return data; // for testing
    }
}
