package org.intelehealth.ezazi.automation.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.intelehealth.ezazi.automation.exceptions.FrameworkException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * JSON helpers backed by a single, thread-safe Jackson {@link ObjectMapper}.
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private JsonUtils() {
    }

    /**
     * Reads a JSON file into any type.
     *
     * @param location file path or classpath resource
     * @param type     target type
     * @param <T>      type
     * @return parsed object
     */
    public static <T> T read(String location, Class<T> type) {
        try (InputStream in = ResourceUtils.open(location)) {
            return MAPPER.readValue(in, type);
        } catch (IOException e) {
            throw new FrameworkException("Cannot parse JSON " + location + " as " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Reads a JSON file into a generic type, e.g. {@code new TypeReference<List<LoginTestCase>>() {}}.
     */
    public static <T> T read(String location, TypeReference<T> type) {
        try (InputStream in = ResourceUtils.open(location)) {
            return MAPPER.readValue(in, type);
        } catch (IOException e) {
            throw new FrameworkException("Cannot parse JSON " + location + ": " + e.getMessage(), e);
        }
    }

    /**
     * Reads a JSON array of objects as rows of strings (same shape as Excel rows). Arrays/objects
     * inside a row are kept as JSON text.
     *
     * @param location file path or classpath resource
     * @return list of rows
     */
    public static List<Map<String, String>> readRows(String location) {
        JsonNode root = read(location, JsonNode.class);
        List<JsonNode> nodes = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(nodes::add);
        } else {
            nodes.add(root);
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (JsonNode node : nodes) {
            Map<String, String> row = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> e : node.properties()) {
                row.put(e.getKey(), e.getValue().isValueNode() ? e.getValue().asText() : e.getValue().toString());
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * Converts a map (e.g. an Excel row) into a POJO.
     *
     * @param source map
     * @param type   POJO class
     * @param <T>    type
     * @return POJO
     */
    public static <T> T convert(Map<String, ?> source, Class<T> type) {
        Map<String, Object> normalised = new LinkedHashMap<>();
        source.forEach((k, v) -> normalised.put(k, parseIfJson(v)));
        return MAPPER.convertValue(normalised, type);
    }

    /** Serialises any object to pretty JSON. */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new FrameworkException("Cannot serialise " + value, e);
        }
    }

    /** @return the shared mapper (for advanced use) */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    private static Object parseIfJson(Object value) {
        if (value instanceof String s) {
            String t = s.trim();
            if ((t.startsWith("[") && t.endsWith("]")) || (t.startsWith("{") && t.endsWith("}"))) {
                try {
                    return MAPPER.readTree(t);
                } catch (JsonProcessingException ignored) {
                    return value;
                }
            }
        }
        return value;
    }
}
