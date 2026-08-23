package com.yuqiangdede.common.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YamlConfigTest {

    @Test
    void readsNestedScalarsAndListsFromApplicationYaml() {
        YamlConfig config = YamlConfig.load(YamlConfigTest.class);

        assertEquals(false, config.getBoolean("vision-mind.native.use-gpu", true));
        assertEquals(42L, config.getLong("vision-mind.yolo.depth.max-pixels", 0L));
        assertEquals(true, config.getBoolean("vision-mind.yolo.depth.allow-local-files", false));
        assertEquals(List.of(0, 2), config.getIntegerList("vision-mind.yolo.default-types"));
        assertEquals(List.of("D:/images", "D:/snapshots"),
                config.getStringList("vision-mind.yolo.depth.local-roots"));
    }

    @Test
    void returnsDefaultForOptionalValue() {
        YamlConfig config = YamlConfig.load(YamlConfigTest.class);

        assertEquals("fallback", config.get("vision-mind.missing.value", "fallback"));
        assertEquals(7, config.getInt("vision-mind.missing.number", 7));
    }

    @Test
    void requiresMissingValueWithFullKeyInError() {
        YamlConfig config = YamlConfig.load(YamlConfigTest.class);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> config.require("vision-mind.missing.required"));

        assertEquals(true, exception.getMessage().contains("vision-mind.missing.required"));
    }
}
