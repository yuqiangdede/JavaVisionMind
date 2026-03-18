package com.yuqiangdede.platform.common.runtime;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

public class OnnxSessionFactory {

    private final OrtEnvironment environment;

    public OnnxSessionFactory() {
        this.environment = OrtEnvironment.getEnvironment();
    }

    public OrtSession createSession(String modelPath, String provider, int threads) throws OrtException {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        if (threads > 0) {
            options.setIntraOpNumThreads(threads);
            options.setInterOpNumThreads(Math.max(1, threads / 2));
        }
        if ("cpu".equalsIgnoreCase(provider) || provider == null || provider.isBlank()) {
            options.addCPU(true);
        }
        return environment.createSession(modelPath, options);
    }

    public OrtEnvironment getEnvironment() {
        return environment;
    }
}
