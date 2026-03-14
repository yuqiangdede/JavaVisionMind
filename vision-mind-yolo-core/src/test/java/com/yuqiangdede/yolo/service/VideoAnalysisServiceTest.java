package com.yuqiangdede.yolo.service;

import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.yolo.dto.input.VideoInput;
import com.yuqiangdede.yolo.dto.output.VideoFrameDetectionResult;
import org.junit.jupiter.api.Test;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoAnalysisServiceTest {

    @Test
    void detect_usesConfiguredFrameIntervalAndReturnsSampledFrames() {
        ImgAnalysisService imgAnalysisService = mock(ImgAnalysisService.class);
        VideoCaptureFactory videoCaptureFactory = mock(VideoCaptureFactory.class);
        VideoCapture videoCapture = mock(VideoCapture.class);
        Mat frame = mock(Mat.class);
        VideoAnalysisService service = new TestableVideoAnalysisService(imgAnalysisService, videoCaptureFactory, frame);

        VideoInput input = new VideoInput();
        input.setRtspUrl("rtsp://example.com/live");
        input.setFrameNum(4);
        input.setFrameInterval(2);

        when(videoCaptureFactory.create()).thenReturn(videoCapture);
        when(videoCapture.open(input.getRtspUrl())).thenReturn(true);
        when(videoCapture.read(frame)).thenReturn(true, true, true, true);
        when(videoCapture.get(Videoio.CAP_PROP_POS_MSEC)).thenReturn(200D, 400D);
        when(frame.empty()).thenReturn(false);

        List<Box> detectedBoxes = List.of(new Box(1, 2, 3, 4));
        when(imgAnalysisService.detectMat(eq(frame), eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(detectedBoxes);

        List<VideoFrameDetectionResult> result = service.detect(input);

        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getFrameIndex());
        assertEquals(200L, result.get(0).getTimestampMs());
        assertEquals(detectedBoxes, result.get(0).getBoxes());
        assertEquals(4, result.get(1).getFrameIndex());
        assertEquals(400L, result.get(1).getTimestampMs());
        verify(videoCapture).release();
        verify(frame).release();
    }

    @Test
    void detect_usesDefaultFrameIntervalWhenRequestOmitsValue() {
        ImgAnalysisService imgAnalysisService = mock(ImgAnalysisService.class);
        VideoCaptureFactory videoCaptureFactory = mock(VideoCaptureFactory.class);
        VideoCapture videoCapture = mock(VideoCapture.class);
        Mat frame = mock(Mat.class);
        VideoAnalysisService service = new TestableVideoAnalysisService(imgAnalysisService, videoCaptureFactory, frame);

        VideoInput input = new VideoInput();
        input.setRtspUrl("rtsp://example.com/live");

        when(videoCaptureFactory.create()).thenReturn(videoCapture);
        when(videoCapture.open(input.getRtspUrl())).thenReturn(true);
        when(videoCapture.read(frame)).thenReturn(true, true, true, true, true, false);
        when(videoCapture.get(Videoio.CAP_PROP_POS_MSEC)).thenReturn(Double.NaN);
        when(frame.empty()).thenReturn(false);
        when(imgAnalysisService.detectMat(eq(frame), eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(List.of());

        List<VideoFrameDetectionResult> result = service.detect(input);

        assertEquals(1, result.size());
        assertEquals(5, result.get(0).getFrameIndex());
        assertEquals(0L, result.get(0).getTimestampMs());
        verify(videoCapture).release();
        verify(frame).release();
    }

    @Test
    void detect_rejectsMissingRtspUrl() {
        VideoAnalysisService service = new TestableVideoAnalysisService(
                mock(ImgAnalysisService.class),
                mock(VideoCaptureFactory.class),
                mock(Mat.class)
        );

        VideoInput input = new VideoInput();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.detect(input));

        assertEquals("rtspUrl is null or empty", exception.getMessage());
    }

    @Test
    void detect_whenVideoOpenFails_throwsAndReleasesCapture() {
        ImgAnalysisService imgAnalysisService = mock(ImgAnalysisService.class);
        VideoCaptureFactory videoCaptureFactory = mock(VideoCaptureFactory.class);
        VideoCapture videoCapture = mock(VideoCapture.class);
        Mat frame = mock(Mat.class);
        VideoAnalysisService service = new TestableVideoAnalysisService(imgAnalysisService, videoCaptureFactory, frame);

        VideoInput input = new VideoInput();
        input.setRtspUrl("rtsp://example.com/live");

        when(videoCaptureFactory.create()).thenReturn(videoCapture);
        when(videoCapture.open(input.getRtspUrl())).thenReturn(false);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.detect(input));

        assertEquals("Failed to open video: rtsp://example.com/live", exception.getMessage());
        verify(videoCapture).release();
    }

    private static final class TestableVideoAnalysisService extends VideoAnalysisService {

        private final Mat frame;

        private TestableVideoAnalysisService(ImgAnalysisService imgAnalysisService,
                                             VideoCaptureFactory videoCaptureFactory,
                                             Mat frame) {
            super(imgAnalysisService, videoCaptureFactory);
            this.frame = frame;
        }

        @Override
        Mat createFrameBuffer() {
            return frame;
        }
    }
}
