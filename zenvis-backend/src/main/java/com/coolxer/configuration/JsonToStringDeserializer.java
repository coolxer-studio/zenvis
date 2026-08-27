package com.coolxer.configuration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Json转String
 */
public class JsonToStringDeserializer extends JsonDeserializer<String> {
    @Override
    public String deserialize(JsonParser jsonParser,
                              DeserializationContext deserializationContext) throws IOException {
        if (jsonParser == null) {
            return null;
        }
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        if (node == null) {
            return null;
        }
        return node.toString();
    }
}

