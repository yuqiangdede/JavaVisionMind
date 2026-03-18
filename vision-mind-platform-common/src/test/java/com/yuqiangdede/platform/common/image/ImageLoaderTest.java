package com.yuqiangdede.platform.common.image;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ImageLoaderTest {

    @Test
    void loadFromBase64Works() throws Exception {
        byte[] tinyPng = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+i6WQAAAAASUVORK5CYII=");
        String base64 = Base64.getEncoder().encodeToString(tinyPng);
        ImageLoader loader = new ImageLoader();
        BufferedImage image = loader.loadFromBase64(base64);
        assertNotNull(image);
    }
}
