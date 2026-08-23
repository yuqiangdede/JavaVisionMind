package com.yuqiangdede.yolo.service;

import com.yuqiangdede.platform.common.config.VisionMindProperties;
import com.yuqiangdede.platform.common.resource.ResourcePathResolver;
import com.yuqiangdede.yolo.dto.input.DepthEstimationRequest;
import com.yuqiangdede.yolo.dto.output.DepthEstimationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DepthEstimationServiceTest {

    @Test
    void validateJsonDepthMap_allowsLimitAndRejectsLimitPlusOne(@TempDir Path resourceRoot) throws Exception {
        try (ServiceHandle handle = service(resourceRoot, 4)) {
            DepthEstimationResult atLimit = result(2, 2);
            DepthEstimationResult overLimit = result(5, 1);

            assertDoesNotThrow(() -> handle.service().validateJsonDepthMap(atLimit));
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> handle.service().validateJsonDepthMap(overLimit));

            assertEquals("depth map is too large for JSON; use /depth/map", error.getMessage());
        }
    }

    @Test
    void estimate_releasesPermitAfterFailure(@TempDir Path resourceRoot) throws Exception {
        try (ServiceHandle handle = service(resourceRoot, 4)) {
            DepthEstimationRequest request = new DepthEstimationRequest();
            request.setImgUrl("file:///not-allowed.png");

            IOException first = assertThrows(IOException.class, () -> handle.service().estimate(request));
            IOException second = assertThrows(IOException.class, () -> handle.service().estimate(request));

            assertEquals("depth image could not be loaded", first.getMessage());
            assertEquals("depth image could not be loaded", second.getMessage());
        }
    }

    private ServiceHandle service(Path resourceRoot, long maxJsonPixels) {
        VisionMindProperties properties = new VisionMindProperties();
        properties.getResource().setRoot(resourceRoot.toString());
        ResourcePathResolver resolver = new ResourcePathResolver(properties);
        DepthEstimationService service = new DepthEstimationService(
                resolver, "missing.onnx", 16, 1024, 1000, 1000,
                0, false, "", true, "", 1, 0, maxJsonPixels);
        return new ServiceHandle(service);
    }

    private DepthEstimationResult result(int width, int height) {
        int pixels = width * height;
        return new DepthEstimationResult(width, height, "m", "row-major", pixels,
                1f, 1f, 1f, 1f, new float[pixels]);
    }

    private record ServiceHandle(DepthEstimationService service) implements AutoCloseable {

        @Override
        public void close() {
            service.shutdown();
        }
    }
}
