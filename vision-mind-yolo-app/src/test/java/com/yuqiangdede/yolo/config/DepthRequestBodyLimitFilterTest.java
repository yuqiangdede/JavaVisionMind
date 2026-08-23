package com.yuqiangdede.yolo.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepthRequestBodyLimitFilterTest {

    @Test
    void filter_rejectsUnknownLengthBodyAfterReadingLimitPlusOne() throws Exception {
        DepthRequestBodyLimitFilter filter = new DepthRequestBodyLimitFilter(64, 1);
        MockHttpServletRequest request = unknownLengthRequest(new byte[65]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> servletResponse.setContentType("unexpected");

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());
        assertTrue(response.getContentAsString().contains("request body too large"));
    }

    @Test
    void filter_boundsConcurrentDepthRequestsBeforeReadingBodies() throws Exception {
        DepthRequestBodyLimitFilter filter = new DepthRequestBodyLimitFilter(64, 1);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            MockHttpServletResponse firstResponse = new MockHttpServletResponse();
            executor.submit(() -> {
                try {
                    filter.doFilter(unknownLengthRequest(new byte[1]), firstResponse,
                            (request, response) -> {
                                firstEntered.countDown();
                                try {
                                    releaseFirst.await(5, TimeUnit.SECONDS);
                                } catch (InterruptedException ex) {
                                    Thread.currentThread().interrupt();
                                }
                            });
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            });
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
            MockHttpServletResponse secondResponse = new MockHttpServletResponse();

            filter.doFilter(unknownLengthRequest(new byte[1]), secondResponse,
                    (request, response) -> response.setContentType("unexpected"));

            assertEquals(503, secondResponse.getStatus());
            assertTrue(secondResponse.getContentAsString().contains("depth service is busy"));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private MockHttpServletRequest unknownLengthRequest(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1L;
            }
        };
        request.setMethod("POST");
        request.setContentType("application/json");
        request.setContent(body);
        return request;
    }
}
