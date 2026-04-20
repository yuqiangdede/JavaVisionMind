package com.yuqiangdede.common.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonUtilsTest {

    @Test
    void object2Json_returnsEmptyStringForNull() throws Exception {
        assertEquals("", JsonUtils.object2Json(null));
    }

    @Test
    void object2Json_serializesObject() throws Exception {
        assertEquals("{\"name\":\"vision\"}", JsonUtils.object2Json(Map.of("name", "vision")));
    }

    @Test
    void map2Json_returnsEmptyObjectForNullOrEmpty() {
        assertEquals("{}", JsonUtils.map2Json(null));
        assertEquals("{}", JsonUtils.map2Json(Map.of()));
    }

    @Test
    void json2Map_parsesJson() {
        Map<String, String> result = JsonUtils.json2Map("{\"hello\":\"world\"}");
        assertEquals("world", result.get("hello"));
    }

    @Test
    void json2Map_rejectsInvalidJson() {
        assertThrows(RuntimeException.class, () -> JsonUtils.json2Map("{bad-json"));
    }
}
