# ASR Demo

最小示例目标：验证 `vision-mind-asr-app` 的统一接口可用。

## 前置条件

1. 服务已启动：`mvn -pl vision-mind-asr-app spring-boot:run`
2. 默认地址：`http://localhost:17008/vision-mind-asr`

## 快速调用

```bash
bash examples/asr-demo/curl.sh
```

## 说明

- 默认调用新接口 `/api/v1/audio/asr/transcribe/source`。
- 支持 `audioUrl` 或 `audioBase64` 二选一。
