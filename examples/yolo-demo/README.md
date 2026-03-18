# YOLO Demo

最小示例目标：验证 `vision-mind-yolo-app` 的新接口与旧接口并行可用。

## 前置条件

1. 服务已启动：`mvn -pl vision-mind-yolo-app spring-boot:run`
2. 默认地址：`http://localhost:17001/vision-mind-yolo`

## 快速调用

```bash
bash examples/yolo-demo/curl.sh
```

## 说明

- 脚本同时演示新接口 `/api/v1/vision/detect` 与旧接口 `/imgAnalysis/detect`。
- 如需可视化结果，可调用 `/api/v1/vision/preview`。
