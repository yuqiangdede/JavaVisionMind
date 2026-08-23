package com.yuqiangdede.yolo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;

/**
 * 在 Jackson 反序列化 data URI 前限制深度接口 JSON 请求体，避免超大 Base64 占满堆内存。
 */
final class DepthRequestBodyLimitFilter extends OncePerRequestFilter {

    private final int maxRequestBytes;
    private final Semaphore activeRequests;

    DepthRequestBodyLimitFilter(int maxRequestBytes, int maxActiveRequests) {
        if (maxRequestBytes <= 0 || maxRequestBytes == Integer.MAX_VALUE || maxActiveRequests <= 0) {
            throw new IllegalArgumentException("depth request limits must be positive");
        }
        this.maxRequestBytes = maxRequestBytes;
        this.activeRequests = new Semaphore(maxActiveRequests, true);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!activeRequests.tryAcquire()) {
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "depth service is busy");
            return;
        }
        try {
            long declaredLength = request.getContentLengthLong();
            if (declaredLength > maxRequestBytes) {
                reject(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "request body too large");
                return;
            }
            byte[] body = request.getInputStream().readNBytes(maxRequestBytes + 1);
            if (body.length > maxRequestBytes) {
                reject(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "request body too large");
                return;
            }
            filterChain.doFilter(new CachedBodyRequest(request, body), response);
        } finally {
            activeRequests.release();
        }
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"-1\",\"msg\":\"" + message + "\",\"data\":null}");
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new ByteArrayServletInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    private static final class ByteArrayServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream input;

        private ByteArrayServletInputStream(byte[] body) {
            this.input = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            return input.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("asynchronous reads are not supported");
        }

        @Override
        public int read() {
            return input.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            return input.read(bytes, offset, length);
        }
    }
}
