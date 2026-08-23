package com.yuqiangdede.yolo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuqiangdede.yolo.YoloApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = YoloApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = YoloLocalModelIntegrationTest.NativeRuntimeInitializer.class)
@AutoConfigureMockMvc
class YoloLocalModelIntegrationTest {

    private static final String INTEGRATION_TEST = "vision-mind.integration-test";
    private static final String ALLOW_LOCAL_FILES = "vision-mind.yolo.depth.allow-local-files";
    private static final String LOCAL_ROOTS = "vision-mind.yolo.depth.local-roots";

    static {
        System.setProperty(INTEGRATION_TEST, "true");
        System.setProperty(ALLOW_LOCAL_FILES, "true");
        System.setProperty(LOCAL_ROOTS, findAsset("car-electric.jpg").getParent().toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterAll
    static void disableNativeRuntime() {
        System.clearProperty(INTEGRATION_TEST);
        System.clearProperty(ALLOW_LOCAL_FILES);
        System.clearProperty(LOCAL_ROOTS);
    }

    @Test
    void detectPoseAndSegmentationUseLocalModels() throws Exception {
        String image = asset("car-electric.jpg").toUri().toString();
        String request = objectMapper.writeValueAsString(Map.of("imgUrl", image));

        mockMvc.perform(post("/api/v1/img/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(post("/api/v1/img/pose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        mockMvc.perform(post("/api/v1/img/detectI")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void depthEndpointsUseLocalYolo26Model() throws Exception {
        String image = asset("car-electric.jpg").toUri().toString();
        String request = objectMapper.writeValueAsString(Map.of("imgUrl", image));

        mockMvc.perform(post("/api/v1/vision/depth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.width").value(1301))
                .andExpect(jsonPath("$.data.height").value(834))
                .andExpect(jsonPath("$.data.unit").value("m"))
                .andExpect(jsonPath("$.data.validPixelCount").value(1301 * 834))
                .andExpect(jsonPath("$.data.minDepth").value(closeTo(2.4603, 0.03)))
                .andExpect(jsonPath("$.data.maxDepth").value(closeTo(7.4296, 0.03)))
                .andExpect(jsonPath("$.data.depthMap").doesNotExist());

        mockMvc.perform(post("/api/v1/vision/depth/map")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("X-Depth-Width", "1301"))
                .andExpect(header().string("X-Depth-Height", "834"))
                .andExpect(header().string("X-Depth-Unit", "m"))
                .andExpect(header().string("X-Depth-Dtype", "float32-le"))
                .andExpect(result -> {
                    byte[] body = result.getResponse().getContentAsByteArray();
                    assertEquals(1301 * 834 * Float.BYTES, body.length);
                    ByteBuffer depth = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
                    assertDepthPixel(depth, 1301, 0, 0, 4.1225f);
                    assertDepthPixel(depth, 1301, 650, 417, 3.0767f);
                    assertDepthPixel(depth, 1301, 1300, 833, 2.5130f);
                });

        mockMvc.perform(post("/api/v1/vision/depth/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    private Path asset(String name) {
        return findAsset(name);
    }

    private static Path findAsset(String name) {
        Path current = Paths.get("test", "assets", name).toAbsolutePath().normalize();
        Path asset = Files.isRegularFile(current)
                ? current
                : Paths.get("..", "test", "assets", name).toAbsolutePath().normalize();
        if (!Files.isRegularFile(asset)) {
            throw new IllegalStateException("integration test asset not found");
        }
        return asset;
    }

    private void assertDepthPixel(ByteBuffer depth, int width, int x, int y, float pythonReference) {
        float actual = depth.getFloat((y * width + x) * Float.BYTES);
        assertEquals(pythonReference, actual, 0.1f);
    }

    static class NativeRuntimeInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            System.setProperty(INTEGRATION_TEST, "true");
        }
    }
}
