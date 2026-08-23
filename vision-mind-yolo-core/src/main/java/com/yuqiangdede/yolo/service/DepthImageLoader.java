package com.yuqiangdede.yolo.service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 有界读取深度估计图片，避免任意文件读取、SSRF、慢连接和压缩图片占满服务资源。
 */
final class DepthImageLoader {

    private static final String GENERIC_ERROR = "depth image could not be loaded";
    private static final Set<String> SUPPORTED_FORMATS = Set.of("JPEG", "JPG", "PNG", "BMP", "GIF");
    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);
    private static final AtomicInteger DNS_THREAD_SEQUENCE = new AtomicInteger();
    private static final ThreadPoolExecutor DNS_EXECUTOR = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(32), task -> {
                Thread thread = new Thread(task,
                        "depth-dns-resolver-" + DNS_THREAD_SEQUENCE.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    private final long maxPixels;
    private final int maxDownloadBytes;
    private final long totalTimeoutMs;
    private final int maxRedirects;
    private final boolean allowLocalFiles;
    private final boolean allowDataUri;
    private final List<Path> localRoots;
    private final Set<String> allowedRemoteHosts;
    private final HttpClient httpClient;
    private final HostnameResolver hostnameResolver;

    DepthImageLoader(long maxPixels, int maxDownloadBytes, int connectTimeoutMs, int totalTimeoutMs) {
        this(maxPixels, maxDownloadBytes, connectTimeoutMs, totalTimeoutMs, 3,
                false, List.of(), true, Set.of());
    }

    DepthImageLoader(long maxPixels, int maxDownloadBytes, int connectTimeoutMs, long totalTimeoutMs,
                     int maxRedirects, boolean allowLocalFiles, List<Path> localRoots,
                     boolean allowDataUri, Set<String> allowedRemoteHosts) {
        this(maxPixels, maxDownloadBytes, connectTimeoutMs, totalTimeoutMs, maxRedirects,
                allowLocalFiles, localRoots, allowDataUri, allowedRemoteHosts, InetAddress::getAllByName);
    }

    DepthImageLoader(long maxPixels, int maxDownloadBytes, int connectTimeoutMs, long totalTimeoutMs,
                     int maxRedirects, boolean allowLocalFiles, List<Path> localRoots,
                     boolean allowDataUri, Set<String> allowedRemoteHosts, HostnameResolver hostnameResolver) {
        if (maxPixels <= 0 || maxDownloadBytes <= 0 || maxDownloadBytes == Integer.MAX_VALUE
                || connectTimeoutMs <= 0 || totalTimeoutMs <= 0 || maxRedirects < 0) {
            throw new IllegalArgumentException("depth image limits and timeouts must be positive");
        }
        if (allowLocalFiles && localRoots.isEmpty()) {
            throw new IllegalArgumentException("depth local roots are required when local files are enabled");
        }
        this.maxPixels = maxPixels;
        this.maxDownloadBytes = maxDownloadBytes;
        this.totalTimeoutMs = totalTimeoutMs;
        this.maxRedirects = maxRedirects;
        this.allowLocalFiles = allowLocalFiles;
        this.allowDataUri = allowDataUri;
        this.localRoots = normalizeLocalRoots(localRoots);
        this.allowedRemoteHosts = normalizeHosts(allowedRemoteHosts);
        this.hostnameResolver = hostnameResolver;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    BufferedImage load(String source) throws IOException {
        try {
            byte[] encoded = readEncoded(source);
            return decode(encoded);
        } catch (IOException | RuntimeException ex) {
            // 不携带原 URL、查询参数或 data URI，避免敏感输入进入响应和异常日志。
            throw new IOException(GENERIC_ERROR);
        }
    }

    private byte[] readEncoded(String source) throws IOException {
        if (source == null || source.isBlank() || !source.equals(source.trim())) {
            throw new IOException(GENERIC_ERROR);
        }
        String lower = source.toLowerCase(Locale.ROOT);
        if (lower.startsWith("data:image/")) {
            if (!allowDataUri) {
                throw new IOException(GENERIC_ERROR);
            }
            return readDataUri(source);
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return readRemote(URI.create(source));
        }
        if (!allowLocalFiles || isUncOrDevicePath(source)) {
            throw new IOException(GENERIC_ERROR);
        }
        if (lower.startsWith("file:")) {
            URI uri = URI.create(source);
            if (uri.getRawAuthority() != null || lower.startsWith("file:////")) {
                throw new IOException(GENERIC_ERROR);
            }
            Path path = Path.of(uri);
            if (isUnsafeLocalPath(path)) {
                throw new IOException(GENERIC_ERROR);
            }
            return readFile(path);
        }
        Path path = Path.of(source);
        if (isUnsafeLocalPath(path)) {
            throw new IOException(GENERIC_ERROR);
        }
        return readFile(path);
    }

    private byte[] readDataUri(String source) throws IOException {
        int comma = source.indexOf(',');
        if (comma < 0 || !source.substring(0, comma).toLowerCase(Locale.ROOT).endsWith(";base64")) {
            throw new IOException(GENERIC_ERROR);
        }
        String payload = source.substring(comma + 1);
        long maximumBase64Length = ((long) maxDownloadBytes + 2L) / 3L * 4L + 4L;
        if (payload.length() > maximumBase64Length) {
            throw new IOException(GENERIC_ERROR);
        }
        byte[] decoded = Base64.getDecoder().decode(payload);
        if (decoded.length > maxDownloadBytes) {
            throw new IOException(GENERIC_ERROR);
        }
        return decoded;
    }

    private byte[] readRemote(URI initialUri) throws IOException {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(totalTimeoutMs);
        URI current = initialUri;
        String initialScheme = current.getScheme();
        for (int redirects = 0; ; redirects++) {
            validateRemoteUri(current, deadlineNanos);
            if ("https".equalsIgnoreCase(initialScheme) && !"https".equalsIgnoreCase(current.getScheme())) {
                throw new IOException(GENERIC_ERROR);
            }
            long remainingMs = remainingMillis(deadlineNanos);
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofMillis(remainingMs))
                    .header("Accept", "image/*")
                    .GET()
                    .build();
            CompletableFuture<HttpResponse<byte[]>> future = httpClient.sendAsync(request,
                    responseInfo -> limitedSubscriber(responseInfo.headers().firstValueAsLong("Content-Length")));
            HttpResponse<byte[]> response;
            try {
                response = future.get(remainingMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                throw new IOException(GENERIC_ERROR);
            } catch (ExecutionException | TimeoutException ex) {
                future.cancel(true);
                throw new IOException(GENERIC_ERROR);
            }
            if (response.statusCode() == 200) {
                return response.body();
            }
            if (!REDIRECT_STATUSES.contains(response.statusCode()) || redirects >= maxRedirects) {
                throw new IOException(GENERIC_ERROR);
            }
            String location = response.headers().firstValue("Location")
                    .orElseThrow(() -> new IOException(GENERIC_ERROR));
            current = current.resolve(location);
        }
    }

    private HttpResponse.BodySubscriber<byte[]> limitedSubscriber(OptionalLong contentLength) {
        if (contentLength.isPresent() && contentLength.getAsLong() > maxDownloadBytes) {
            return new RejectedBodySubscriber();
        }
        return new LimitedBodySubscriber(maxDownloadBytes);
    }

    private byte[] readFile(Path path) throws IOException {
        Path realPath = path.toAbsolutePath().normalize().toRealPath();
        boolean insideAllowedRoot = localRoots.stream().anyMatch(realPath::startsWith);
        if (!insideAllowedRoot || !Files.isRegularFile(realPath) || Files.size(realPath) > maxDownloadBytes) {
            throw new IOException(GENERIC_ERROR);
        }
        try (var input = Files.newInputStream(realPath)) {
            byte[] bytes = input.readNBytes(maxDownloadBytes + 1);
            if (bytes.length > maxDownloadBytes) {
                throw new IOException(GENERIC_ERROR);
            }
            return bytes;
        }
    }

    private BufferedImage decode(byte[] encoded) throws IOException {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(encoded))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException(GENERIC_ERROR);
            }
            ImageReader reader = readers.next();
            try {
                if (!SUPPORTED_FORMATS.contains(reader.getFormatName().toUpperCase(Locale.ROOT))) {
                    throw new IOException(GENERIC_ERROR);
                }
                reader.setInput(input, true, true);
                validateDimensions(reader.getWidth(0), reader.getHeight(0));
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IOException(GENERIC_ERROR);
                }
                return toBgr(image);
            } finally {
                reader.dispose();
            }
        }
    }

    private BufferedImage toBgr(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_3BYTE_BGR) {
            return source;
        }
        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return converted;
    }

    private void validateDimensions(int width, int height) throws IOException {
        if (width <= 0 || height <= 0 || Math.multiplyExact((long) width, height) > maxPixels) {
            throw new IOException(GENERIC_ERROR);
        }
    }

    private void validateRemoteUri(URI uri, long deadlineNanos) throws IOException {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IOException(GENERIC_ERROR);
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (allowedRemoteHosts.contains(normalizedHost)) {
            return;
        }
        InetAddress[] addresses = resolveHost(host, deadlineNanos);
        if (addresses.length == 0) {
            throw new IOException(GENERIC_ERROR);
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new IOException(GENERIC_ERROR);
            }
        }
    }

    private InetAddress[] resolveHost(String host, long deadlineNanos) throws IOException {
        Future<InetAddress[]> resolution;
        try {
            resolution = DNS_EXECUTOR.submit(() -> hostnameResolver.resolve(host));
        } catch (RejectedExecutionException ex) {
            throw new IOException(GENERIC_ERROR);
        }
        try {
            return resolution.get(remainingMillis(deadlineNanos), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            resolution.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException(GENERIC_ERROR);
        } catch (ExecutionException | TimeoutException ex) {
            resolution.cancel(true);
            throw new IOException(GENERIC_ERROR);
        }
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            return isPublicIpv4(bytes);
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            if ((first & 0xfe) == 0xfc || (first == 0x20 && second == 0x01
                    && Byte.toUnsignedInt(bytes[2]) == 0x0d && Byte.toUnsignedInt(bytes[3]) == 0xb8)) {
                return false;
            }
            if (isIpv4Mapped(bytes)) {
                return isPublicIpv4(new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]});
            }
        }
        return true;
    }

    private boolean isPublicIpv4(byte[] bytes) {
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        int third = Byte.toUnsignedInt(bytes[2]);
        return first != 0 && first != 10 && first != 127 && first < 224
                && !(first == 100 && second >= 64 && second <= 127)
                && !(first == 169 && second == 254)
                && !(first == 172 && second >= 16 && second <= 31)
                && !(first == 192 && second == 168)
                && !(first == 192 && second == 0 && third == 0)
                && !(first == 192 && second == 0 && third == 2)
                && !(first == 198 && (second == 18 || second == 19))
                && !(first == 198 && second == 51 && third == 100)
                && !(first == 203 && second == 0 && third == 113);
    }

    private boolean isIpv4Mapped(byte[] bytes) {
        for (int index = 0; index < 10; index++) {
            if (bytes[index] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private long remainingMillis(long deadlineNanos) throws IOException {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new IOException(GENERIC_ERROR);
        }
        return Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
    }

    private boolean isUncOrDevicePath(String source) {
        String normalized = source.replace('/', '\\');
        return normalized.startsWith("\\\\") || normalized.startsWith("\\\\?\\")
                || normalized.startsWith("\\\\.\\");
    }

    private boolean isUnsafeLocalPath(Path path) {
        String value = path.toString();
        if (isUncOrDevicePath(value)) {
            return true;
        }
        Path root = path.getRoot();
        return root != null && isUncOrDevicePath(root.toString());
    }

    private List<Path> normalizeLocalRoots(List<Path> roots) {
        try {
            return roots.stream().map(root -> {
                try {
                    return root.toAbsolutePath().normalize().toRealPath();
                } catch (IOException ex) {
                    throw new IllegalArgumentException("depth local root does not exist", ex);
                }
            }).toList();
        } catch (RuntimeException ex) {
            throw ex;
        }
    }

    private Set<String> normalizeHosts(Set<String> hosts) {
        Set<String> normalized = new HashSet<>();
        for (String host : hosts) {
            if (host != null && !host.isBlank()) {
                normalized.add(host.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(normalized);
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream output;
        private final int limit;
        private Flow.Subscription subscription;

        private LimitedBodySubscriber(int limit) {
            this.limit = limit;
            this.output = new ByteArrayOutputStream(Math.min(limit, 8192));
        }

        @Override
        public CompletableFuture<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if ((long) output.size() + buffer.remaining() > limit) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException(GENERIC_ERROR));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                output.writeBytes(chunk);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(output.toByteArray());
        }
    }

    private static final class RejectedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final CompletableFuture<byte[]> body = new CompletableFuture<>();

        private RejectedBodySubscriber() {
            body.completeExceptionally(new IOException(GENERIC_ERROR));
        }

        @Override
        public CompletableFuture<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.cancel();
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            // 已在响应头阶段拒绝。
        }

        @Override
        public void onError(Throwable throwable) {
            // Future 已携带统一错误。
        }

        @Override
        public void onComplete() {
            // Future 已携带统一错误。
        }
    }

    @FunctionalInterface
    interface HostnameResolver {

        InetAddress[] resolve(String host) throws IOException;
    }
}
