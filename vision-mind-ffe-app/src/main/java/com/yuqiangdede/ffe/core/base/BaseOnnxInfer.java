package com.yuqiangdede.ffe.core.base;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtLoggingLevel;
import ai.onnxruntime.OrtSession;
import lombok.Getter;

@Getter
public abstract class BaseOnnxInfer extends OpenCVLoader{

    /**
     * -- GETTER --
     *  获取环境信息
     *
     */
    private OrtEnvironment env;
    /**
     * -- GETTER --
     *  获取输入端的名称
     *
     */
    private final String[] inputNames;
    /**
     * -- GETTER --
     *  获取session
     *
     */
    private final OrtSession[] sessions;


    /**
     * 构造方法，用于初始化ONNX模型推理环境。
     *
     * @param modelPath ONNX模型的路径。
     * @param threads 推理过程中使用的线程数。
     * @throws RuntimeException 如果在初始化过程中发生异常，则抛出运行时异常。
     */
    public BaseOnnxInfer(String modelPath, int threads){
        try {
            this.env = OrtEnvironment.getEnvironment();
            try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
                opts.setInterOpNumThreads(threads);
                opts.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR);
                opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
                this.sessions = new OrtSession[]{env.createSession(modelPath, opts)};
                this.inputNames = new String[]{this.sessions[0].getInputNames().iterator().next()};
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取输入端的名称
     * @return
     */
    public String getInputName() {
        return inputNames[0];
    }

    /**
     * 获取session
     * @return
     */
    public OrtSession getSession() {
        return sessions[0];
    }

    /**
     * 关闭服务
     */
    public void close(){
        try {
            if(sessions != null){
                for(OrtSession session : sessions){
                    session.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
