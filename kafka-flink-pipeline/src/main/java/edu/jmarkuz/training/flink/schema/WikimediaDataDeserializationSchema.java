package edu.jmarkuz.training.flink.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.jmarkuz.training.model.WikimediaData;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.io.IOException;

public class WikimediaDataDeserializationSchema implements DeserializationSchema<WikimediaData> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public WikimediaData deserialize(byte[] bytes) throws IOException {
        return mapper.readValue(bytes, WikimediaData.class);
    }

    @Override
    public boolean isEndOfStream(WikimediaData wikimediaData) {
        return false;
    }

    @Override
    public TypeInformation<WikimediaData> getProducedType() {
        return TypeInformation.of(WikimediaData.class);
    }
}
