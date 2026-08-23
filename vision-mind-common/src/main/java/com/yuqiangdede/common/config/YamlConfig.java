package com.yuqiangdede.common.config;

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the active module's classpath application.yml for legacy static configuration holders.
 */
public final class YamlConfig {

    private static final String APPLICATION_YAML = "application.yml";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^{}]+)}");

    private final Map<String, Object> values;

    private YamlConfig(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static YamlConfig load(Class<?> anchor) {
        Objects.requireNonNull(anchor, "anchor");
        Resource resource = anchor.getClassLoader().getResource(APPLICATION_YAML) == null
                ? null
                : new org.springframework.core.io.ClassPathResource(APPLICATION_YAML, anchor.getClassLoader());
        if (resource == null || !resource.exists()) {
            throw new IllegalStateException("Required YAML configuration not found: " + APPLICATION_YAML);
        }

        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(APPLICATION_YAML, resource);
            Map<String, Object> flattened = new LinkedHashMap<>();
            for (PropertySource<?> source : sources) {
                Object sourceValue = source.getSource();
                if (sourceValue instanceof Map<?, ?> sourceMap) {
                    for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                        flattened.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
            }
            return new YamlConfig(flattened);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read YAML configuration: " + APPLICATION_YAML, e);
        }
    }

    public String get(String key, String defaultValue) {
        String value = resolveValue(key, new ArrayList<>());
        return value == null ? defaultValue : value;
    }

    public String require(String key) {
        String value = get(key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required YAML configuration is missing: " + key);
        }
        return value;
    }

    public int getInt(String key, int defaultValue) {
        return parseNumber(key, get(key, null), defaultValue, Integer::parseInt);
    }

    public long getLong(String key, long defaultValue) {
        return parseNumber(key, get(key, null), defaultValue, Long::parseLong);
    }

    public float getFloat(String key, float defaultValue) {
        return parseNumber(key, get(key, null), defaultValue, Float::parseFloat);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key, null);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalStateException("Invalid boolean YAML configuration for " + key + ": " + value);
        }
        return Boolean.parseBoolean(value);
    }

    public List<String> getStringList(String key) {
        List<String> result = indexedValues(key);
        if (!result.isEmpty()) {
            return result;
        }
        String scalar = get(key, null);
        if (scalar == null || scalar.isBlank()) {
            return List.of();
        }
        return List.of(scalar.split(","));
    }

    public List<Integer> getIntegerList(String key) {
        List<String> values = getStringList(key);
        List<Integer> result = new ArrayList<>(values.size());
        for (String value : values) {
            try {
                result.add(Integer.parseInt(value.trim()));
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Invalid integer YAML configuration for " + key + ": " + value, e);
            }
        }
        return List.copyOf(result);
    }

    private List<String> indexedValues(String key) {
        List<String> result = new ArrayList<>();
        for (int index = 0; ; index++) {
            String value = resolveValue(key + "[" + index + "]", new ArrayList<>());
            if (value == null) {
                break;
            }
            result.add(value);
        }
        return result;
    }

    private String resolveValue(String key, List<String> resolvingKeys) {
        if (resolvingKeys.contains(key)) {
            throw new IllegalStateException("Circular YAML configuration placeholder: " + key);
        }
        Object raw = values.get(key);
        if (raw == null) {
            raw = systemValue(key);
        }
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw);
        Matcher matcher = PLACEHOLDER.matcher(value);
        if (!matcher.find()) {
            return value;
        }

        resolvingKeys.add(key);
        StringBuffer resolved = new StringBuffer();
        do {
            String expression = matcher.group(1);
            int separator = expression.indexOf(':');
            String reference = separator < 0 ? expression : expression.substring(0, separator);
            String fallback = separator < 0 ? null : expression.substring(separator + 1);
            String replacement = resolveValue(reference, resolvingKeys);
            if (replacement == null) {
                replacement = fallback == null ? "" : resolveText(fallback, resolvingKeys);
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        } while (matcher.find());
        matcher.appendTail(resolved);
        resolvingKeys.remove(key);
        return resolved.toString();
    }

    private String resolveText(String text, List<String> resolvingKeys) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            String expression = matcher.group(1);
            int separator = expression.indexOf(':');
            String reference = separator < 0 ? expression : expression.substring(0, separator);
            String fallback = separator < 0 ? null : expression.substring(separator + 1);
            String replacement = resolveValue(reference, resolvingKeys);
            matcher.appendReplacement(resolved,
                    Matcher.quoteReplacement(replacement == null ? (fallback == null ? "" : fallback) : replacement));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private String systemValue(String key) {
        String systemProperty = System.getProperty(key);
        if (systemProperty != null) {
            return systemProperty;
        }
        String environmentValue = System.getenv(key);
        if (environmentValue != null) {
            return environmentValue;
        }
        String environmentKey = key.toUpperCase().replace('.', '_').replace('-', '_');
        return System.getenv(environmentKey);
    }

    private <T> T parseNumber(String key, String value, T defaultValue, NumberParser<T> parser) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return parser.parse(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid numeric YAML configuration for " + key + ": " + value, e);
        }
    }

    @FunctionalInterface
    private interface NumberParser<T> {
        T parse(String value) throws NumberFormatException;
    }
}
