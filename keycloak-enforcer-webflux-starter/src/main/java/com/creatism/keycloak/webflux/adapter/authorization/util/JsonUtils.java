package com.creatism.keycloak.webflux.adapter.authorization.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.representations.AccessToken;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods to manipulate JSON data
 *
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
public class JsonUtils {
    private static ObjectMapper objectMapper = new ObjectMapper();

    public static JsonNode readTree(String json) throws JsonProcessingException {
        return objectMapper.readTree(json);
    }

    public static List<String> getValues(JsonNode jsonNode, String path) {
        return getValues(jsonNode.at(path));
    }

    public static List<String> getValues(JsonNode jsonNode) {
        List<String> values = new ArrayList<>();

        if (jsonNode.isArray()) {
            for (JsonNode node : jsonNode) {
                String value;

                if (node.isObject()) {
                    try {
                        value = JsonSerialization.writeValueAsString(node);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    value = node.asText();
                }

                if (value != null) {
                    values.add(value);
                }
            }
        } else {
            String value = jsonNode.asText();

            if (value != null) {
                values.add(value);
            }
        }

        return values;
    }

    public static AccessToken asAccessToken(String rawToken) {
        try {
            return new JWSInput(rawToken).readJsonContent(AccessToken.class);
        } catch (Exception cause) {
            throw new RuntimeException("Failed to decode token", cause);
        }
    }
}
