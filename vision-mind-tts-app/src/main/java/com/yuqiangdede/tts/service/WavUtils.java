package com.yuqiangdede.tts.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class WavUtils {

    private WavUtils() {
    }

    public static byte[] toWav(float[] samples, int sampleRate) {
        if (samples == null) {
            throw new IllegalArgumentException("samples must not be null");
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be > 0");
        }

        int pcmDataSize = samples.length * 2;
        int fileSize = 36 + pcmDataSize;
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(44 + pcmDataSize);
            writeAscii(output, "RIFF");
            writeInt32(output, fileSize);
            writeAscii(output, "WAVE");
            writeAscii(output, "fmt ");
            writeInt32(output, 16);
            writeInt16(output, 1);
            writeInt16(output, 1);
            writeInt32(output, sampleRate);
            writeInt32(output, sampleRate * 2);
            writeInt16(output, 2);
            writeInt16(output, 16);
            writeAscii(output, "data");
            writeInt32(output, pcmDataSize);
            for (float sample : samples) {
                float clamped = Math.max(-1.0f, Math.min(1.0f, sample));
                short pcm = (short) Math.round(clamped * 32767.0f);
                writeInt16(output, pcm);
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode WAV", e);
        }
    }

    private static void writeAscii(ByteArrayOutputStream output, String value) throws IOException {
        output.write(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static void writeInt16(ByteArrayOutputStream output, int value) throws IOException {
        output.write(value & 0xff);
        output.write((value >> 8) & 0xff);
    }

    private static void writeInt32(ByteArrayOutputStream output, int value) throws IOException {
        output.write(value & 0xff);
        output.write((value >> 8) & 0xff);
        output.write((value >> 16) & 0xff);
        output.write((value >> 24) & 0xff);
    }
}
