package com.yuqiangdede.asr.service;

import com.yuqiangdede.asr.dto.output.AsrAudioInfo;
import com.yuqiangdede.asr.dto.output.AsrDecodeAudio;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

@Service
public class AudioDecodeService {

    private static final int TARGET_SAMPLE_RATE = 16000;

    public AsrDecodeAudio decode(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传音频不能为空");
        }

        List<float[]> chunks = new ArrayList<>();
        int totalSamples = 0;
        long durationMs = 0L;

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(file.getInputStream())) {
            grabber.setSampleRate(TARGET_SAMPLE_RATE);
            grabber.setAudioChannels(1);
            grabber.start();
            durationMs = Math.max(0L, grabber.getLengthInTime() / 1000L);

            Frame frame;
            while ((frame = grabber.grabSamples()) != null) {
                float[] samples = extractSamples(frame);
                if (samples.length > 0) {
                    chunks.add(samples);
                    totalSamples += samples.length;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("音频解码失败: " + e.getMessage(), e);
        }

        if (totalSamples == 0) {
            throw new IllegalStateException("音频解码后无可用采样数据");
        }

        float[] merged = new float[totalSamples];
        int offset = 0;
        for (float[] chunk : chunks) {
            System.arraycopy(chunk, 0, merged, offset, chunk.length);
            offset += chunk.length;
        }

        AsrAudioInfo audioInfo = new AsrAudioInfo(
                sourceFormat(file.getOriginalFilename()),
                TARGET_SAMPLE_RATE,
                1,
                totalSamples,
                durationMs > 0 ? durationMs : Math.round(totalSamples * 1000.0 / TARGET_SAMPLE_RATE)
        );
        return new AsrDecodeAudio(merged, audioInfo);
    }

    private float[] extractSamples(Frame frame) {
        if (frame.samples == null || frame.samples.length == 0 || frame.samples[0] == null) {
            return new float[0];
        }
        Buffer sampleBuffer = frame.samples[0];
        if (sampleBuffer instanceof FloatBuffer floatBuffer) {
            FloatBuffer duplicate = floatBuffer.duplicate();
            duplicate.rewind();
            float[] samples = new float[duplicate.remaining()];
            duplicate.get(samples);
            return samples;
        }
        if (sampleBuffer instanceof ShortBuffer shortBuffer) {
            ShortBuffer duplicate = shortBuffer.duplicate();
            duplicate.rewind();
            float[] samples = new float[duplicate.remaining()];
            for (int i = 0; i < samples.length; i++) {
                samples[i] = duplicate.get() / 32768.0f;
            }
            return samples;
        }
        return new float[0];
    }

    private String sourceFormat(String fileName) {
        if (fileName == null) {
            return "unknown";
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "unknown";
        }
        return fileName.substring(index + 1).toLowerCase();
    }
}
