package com.yuqiangdede.yolo.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DepthImageLoaderTest {

    @Test
    void load_acceptsBoundedDataUri() throws Exception {
        String dataUri = pngDataUri(2, 2);
        DepthImageLoader loader = new DepthImageLoader(4, 1024, 1000, 1000);

        BufferedImage image = loader.load(dataUri);

        assertEquals(2, image.getWidth());
        assertEquals(2, image.getHeight());
    }

    @Test
    void load_rejectsImageBeforeDecodingWhenPixelLimitIsExceeded() throws Exception {
        String dataUri = pngDataUri(2, 2);
        DepthImageLoader loader = new DepthImageLoader(3, 1024, 1000, 1000);

        IOException error = assertThrows(IOException.class, () -> loader.load(dataUri));

        assertEquals("depth image could not be loaded", error.getMessage());
    }

    @Test
    void load_enforcesEncodedByteLimitAtExactBoundary() throws Exception {
        byte[] png = pngBytes(2, 2);
        String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);

        BufferedImage image = new DepthImageLoader(4, png.length, 1000, 1000).load(dataUri);
        IOException error = assertThrows(IOException.class,
                () -> new DepthImageLoader(4, png.length - 1, 1000, 1000).load(dataUri));

        assertEquals(2, image.getWidth());
        assertEquals(BufferedImage.TYPE_3BYTE_BGR, image.getType());
        assertEquals("depth image could not be loaded", error.getMessage());
    }

    @Test
    void load_rejectsLocalFilesByDefault(@TempDir Path imageRoot) throws Exception {
        Path image = imageRoot.resolve("image.png");
        Files.write(image, pngBytes(2, 2));

        IOException error = assertThrows(IOException.class,
                () -> new DepthImageLoader(4, 1024, 1000, 1000).load(image.toString()));

        assertEquals("depth image could not be loaded", error.getMessage());
    }

    @Test
    void load_allowsOnlyConfiguredLocalRootAndRejectsUnc(@TempDir Path allowedRoot,
                                                          @TempDir Path outsideRoot) throws Exception {
        Path allowed = allowedRoot.resolve("allowed.png");
        Path outside = outsideRoot.resolve("outside.png");
        Files.write(allowed, pngBytes(2, 2));
        Files.write(outside, pngBytes(2, 2));
        DepthImageLoader loader = new DepthImageLoader(4, 1024, 1000, 1000,
                0, true, List.of(allowedRoot), true, Set.of());

        BufferedImage image = loader.load(allowed.toString());
        IOException outsideError = assertThrows(IOException.class, () -> loader.load(outside.toString()));
        IOException uncError = assertThrows(IOException.class,
                () -> loader.load("\\\\attacker.invalid\\share\\image.png"));
        IOException encodedUncError = assertThrows(IOException.class,
                () -> loader.load("file:///%5C%5Cattacker.invalid%5Cshare%5Cimage.png"));

        assertEquals(2, image.getWidth());
        assertEquals("depth image could not be loaded", outsideError.getMessage());
        assertEquals("depth image could not be loaded", uncError.getMessage());
        assertEquals("depth image could not be loaded", encodedUncError.getMessage());
    }

    @Test
    void load_rejectsPrivateRemoteAddressByDefault() {
        DepthImageLoader loader = new DepthImageLoader(4, 1024, 1000, 1000);

        IOException error = assertThrows(IOException.class,
                () -> loader.load("http://127.0.0.1:9/image.png"));

        assertEquals("depth image could not be loaded", error.getMessage());
    }

    @Test
    void load_enforcesByteLimitAfterRedirect() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/oversize");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/oversize", exchange -> {
            byte[] body = new byte[65];
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            DepthImageLoader loader = new DepthImageLoader(4, 64, 1000, 1000,
                    1, false, List.of(), true, Set.of("127.0.0.1"));

            IOException error = assertThrows(IOException.class,
                    () -> loader.load("http://127.0.0.1:" + server.getAddress().getPort() + "/redirect"));

            assertEquals("depth image could not be loaded", error.getMessage());
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    void load_appliesTotalRemoteDeadline() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(250);
                byte[] body = pngBytes(2, 2);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            DepthImageLoader loader = new DepthImageLoader(4, 1024, 1000, 50,
                    0, false, List.of(), true, Set.of("127.0.0.1"));

            IOException error = assertThrows(IOException.class,
                    () -> loader.load("http://127.0.0.1:" + server.getAddress().getPort() + "/slow"));

            assertEquals("depth image could not be loaded", error.getMessage());
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    void load_appliesDeadlineToDnsResolution() throws Exception {
        DepthImageLoader loader = new DepthImageLoader(4, 1024, 1000, 50,
                0, false, List.of(), true, Set.of(), host -> {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IOException("interrupted", ex);
                    }
                    return new InetAddress[]{InetAddress.getByName("8.8.8.8")};
                });

        IOException error = assertThrows(IOException.class,
                () -> loader.load("http://slow-dns.invalid/image.png"));

        assertEquals("depth image could not be loaded", error.getMessage());
    }

    private String pngDataUri(int width, int height) throws IOException {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes(width, height));
    }

    private byte[] pngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
