package com.yuqiangdede.rfdetr.service;

import com.yuqiangdede.common.dto.output.Box;
import com.yuqiangdede.rfdetr.config.RfDetrProperties;
import com.yuqiangdede.rfdetr.dto.input.VideoInput;
import com.yuqiangdede.rfdetr.dto.output.VideoFrameDetectionResult;
import org.junit.jupiter.api.Test;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoAnalysisServiceTest {

    @Test
    void detect_samplesConfiguredFramesAndReleasesResources() {
        RfDetrImageAnalysisService imageService = mock(RfDetrImageAnalysisService.class);
        VideoCaptureFactory factory = mock(VideoCaptureFactory.class);
        VideoCapture capture = mock(VideoCapture.class);
        Mat frame = mock(Mat.class);
        VideoAnalysisService service = new TestableVideoService(imageService, factory, properties(), frame);
        VideoInput input = new VideoInput();
        input.setRtspUrl("rtsp://example.com/live");
        input.setFrameNum(4);
        input.setFrameInterval(2);
        when(factory.create()).thenReturn(capture);
        when(capture.open(input.getRtspUrl())).thenReturn(true);
        when(capture.read(frame)).thenReturn(true, true, true, true);
        when(capture.get(Videoio.CAP_PROP_POS_MSEC)).thenReturn(200D, 400D);
        when(frame.empty()).thenReturn(false);
        when(imageService.detectMat(eq(frame), eq(null), eq(null), any(), any())).thenReturn(List.of(new Box(1, 2, 3, 4)));

        List<VideoFrameDetectionResult> result = service.detect(input);

        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getFrameIndex());
        assertEquals(400L, result.get(1).getTimestampMs());
        verify(capture).release();
        verify(frame).release();
    }

    @Test
    void detect_rejectsMissingSource() {
        VideoAnalysisService service = new TestableVideoService(mock(RfDetrImageAnalysisService.class),
                mock(VideoCaptureFactory.class), properties(), mock(Mat.class));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.detect(new VideoInput()));

        assertEquals("rtspUrl is null or empty", exception.getMessage());
    }

    private RfDetrProperties properties() {
        RfDetrProperties properties = new RfDetrProperties();
        properties.setFrameInterval(5);
        return properties;
    }

    private static final class TestableVideoService extends VideoAnalysisService {

        private final Mat frame;

        private TestableVideoService(RfDetrImageAnalysisService imageService, VideoCaptureFactory factory,
                                     RfDetrProperties properties, Mat frame) {
            super(imageService, factory, properties);
            this.frame = frame;
        }

        @Override
        Mat createFrameBuffer() {
            return frame;
        }
    }
}
