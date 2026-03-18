# 原生库加载排查

## 现象

- OpenCV / ONNX 相关 `UnsatisfiedLinkError`
- 或应用在初始化阶段找不到 `.dll/.so`

## 统一加载机制

- OpenCV 由 `NativeLibraryManager` 统一加载
- 可用开关：`vision-mind.native-load.enabled`
- 测试场景可通过 `-Dvision-mind.skip-opencv=true` 跳过 OpenCV 加载

## 建议步骤

1. 确认资源目录存在：
   - `resource/lib/opencv/opencv_java*.dll`（Windows）
   - `resource/lib/opencv/libopencv_java*.so`（Linux）
2. 执行 `scripts/verify-env.*`，确认资源根目录与清单可读。
3. 在 CI 或本地测试时加上：
   - `-Dvision-mind.skip-opencv=true`

## 常见误区

- 仅设置了 `VISION_MIND_PATH`，但目录结构与清单不匹配。
- 模型在外部目录，未同步 `manifest.json`。
