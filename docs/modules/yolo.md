# YOLO Module

- App: `vision-mind-yolo-app`
- Core: `vision-mind-yolo-core`
- Domain: `vision`

## Unified API

- JSON: `/api/v1/vision/detect`
- Preview: `/api/v1/vision/detect/preview`
- Video: `/api/v1/vision/video/detect`
- Upload: `/api/v1/vision/detect/upload`
- Depth summary: `/api/v1/vision/depth`
- Depth map: `/api/v1/vision/depth/map`
- Depth preview: `/api/v1/vision/depth/preview`

旧接口（如 `/api/v1/img/detectI`）保留兼容。

## 配置

YOLO app 只读取
`vision-mind-yolo-app/src/main/resources/application.yml`；`yolo-core` 不携带运行时
配置。模型和阈值位于 `vision-mind.yolo`，例如
`vision-mind.yolo.models.detect`、`vision-mind.yolo.nms.detect` 和
`vision-mind.yolo.default-types`。OpenCV 路径和 GPU 开关位于
`vision-mind.native.dll-path`、`vision-mind.native.so-path` 和
`vision-mind.native.use-gpu`。

## YOLO26 单目深度估计

深度估计使用 `yolo26n-depth.onnx`，默认相对资源路径为
`resource/yolo/model/yolo26n-depth.onnx`。可在 `application.yml` 中通过
`vision-mind.yolo.depth.model` 覆盖。模型应由独立的 `testExport` Python 导出工程生成；
Java 服务不会在运行时下载模型。

默认保护限制：最多 `2,100,000` 像素（覆盖 1920×1080）、编码图片最多 25 MiB、
JSON 请求体最多 35 MiB、最多 2 个活动深度请求、单路推理并发、排队最多 5 秒；
远程图片连接超时 5 秒，
包含重定向与响应体读取在内的总时限为 15 秒。这些值可通过
`vision-mind.yolo.depth.*` 配置覆盖。完整 JSON 深度数组默认只允许 500,000 像素以内，
较大图片请使用二进制 `/depth/map` 接口。

旧的 `application.properties`、`yolo-core.properties` 和旧的扁平深度配置键不再读取。

图片来源默认允许 `data:image/...;base64` 和公网 HTTP(S)。HTTP 重定向最多 3 次，
每一跳都会重新校验目标；本机、私网、链路本地和保留地址默认拒绝。可信内网图床可通过
`VISION_MIND_YOLO_DEPTH_ALLOWED_REMOTE_HOSTS` 配置精确主机名（多个主机用逗号分隔）。
本地文件默认关闭，UNC/Windows device path 始终拒绝；如需读取本机图片，必须同时设置：

```powershell
$env:VISION_MIND_YOLO_DEPTH_ALLOW_LOCAL_FILES = 'true'
$env:VISION_MIND_YOLO_DEPTH_LOCAL_ROOTS = 'D:\images,D:\camera-snapshots'
```

服务只会读取这些根目录内经真实路径解析后的普通文件，避免 `..` 或符号链接越界。

模型契约：

- 输入：RGB、`float32`、NCHW，居中 `114` letterbox，像素除以 `255`；
- 当前静态导出 shape：`images [1,3,768,768]`；
- 输出：`output0 [1,1,768,768]`，数值为已标定的估计距离，单位米；
- 输出不需要 `sigmoid`、`exp`、置信度过滤或 NMS；
- Java 会裁掉 letterbox padding，并将深度图双线性恢复到原图尺寸。

### 1. 获取统计信息

```powershell
$body = @{
    imgUrl = 'https://images.example.com/scene.jpg'
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri 'http://localhost:17001/vision-mind-yolo/api/v1/vision/depth' `
    -ContentType 'application/json' `
    -Body $body
```

响应包含原图宽高、有效像素数、`minDepth`、`maxDepth`、`meanDepth`、
`medianDepth`，单位均为米。默认不把完整数组放入 JSON；调试小图时可传
`"includeDepthMap": true`，数组为 `row-major`，索引是 `y * width + x`。

### 2. 获取原始深度图

`POST /api/v1/vision/depth/map` 返回 `application/octet-stream`：

- 数据类型：little-endian `float32`；
- 布局：row-major；
- 数据长度：`width * height * 4` 字节；
- `X-Depth-Width`、`X-Depth-Height`、`X-Depth-Unit`、`X-Depth-Dtype`
  响应头描述 shape 与单位。

生产调用优先使用该接口，避免逐像素 JSON 带来的体积和 GC 开销。

### 3. 获取预览图

`POST /api/v1/vision/depth/preview` 返回 PNG。默认使用 `disparity` 模式：
对逆深度的 2%～98% 分位区间着色，近处为暖色。若需要跨图片保持固定颜色尺度，
使用米制范围：

```json
{
  "imgUrl": "D:\\images\\scene.jpg",
  "visualizationMode": "metric",
  "minDepth": 0.0,
  "maxDepth": 20.0
}
```

单目模型输出是估计值，不等同于深度传感器实测。对固定摄像机要求准确绝对距离时，
需要先用该摄像机的带真值数据校准模型，再重新导出 ONNX。
