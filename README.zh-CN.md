# JavaVisionMind

> 一个模块化的 Spring Boot 工具集，整合了目标检测、图像检索以及多模态 LLM 能力。

[English README](README.md)

## 目录
- [项目简介](#项目简介)
- [目录结构](#目录结构)
- [环境准备](#环境准备)
- [快速开始](#快速开始)
- [接口概览](#接口概览)
  - [vision-mind-yolo-app（图像分析）](#vision-mind-yolo-app图像分析)
  - [vision-mind-ffe-app（人脸特征提取）](#vision-mind-ffe-app人脸特征提取)
  - [vision-mind-reid-app（行人重识别）](#vision-mind-reid-app行人重识别)
  - [vision-mind-tbir-app（文本图像检索）](#vision-mind-tbir-app文本图像检索)
  - [vision-mind-llm-core（语言服务）](#vision-mind-llm-core语言服务)
- [资源下载](#资源下载)
- [接口流程参考](#接口流程参考)
- [路线图](#路线图)

## 项目简介

JavaVisionMind 是一组相互独立的 Spring Boot 服务，覆盖目标检测、姿态估计、人脸识别、行人重识别、文本图像检索以及大语言模型交互。每个能力都以独立模块提供，可按需部署所需的功能。

## 目录结构

| 模块 | 说明 |
| --- | --- |
| `vision-mind-yolo-core` | 提供 YOLOv11、FAST-SAM、姿态估计与分割模型的核心推理工具。 |
| `vision-mind-yolo-app` | 基于 `vision-mind-yolo-core` 的 REST API 外壳，用于图像分析。 |
| `vision-mind-ffe-app` | 包含检测、对齐、特征提取、相似度检索与索引维护的人脸服务。 |
| `vision-mind-reid-app` | 行人重识别流程，支持 Lucene、内存与 Elasticsearch 向量检索。 |
| `vision-mind-tbir-app` | 基于 CLIP 向量的图像检索服务，兼容 Lucene、内存与 Elasticsearch 存储。 |
| `vision-mind-llm-core` | 封装 OpenAI/Ollama 等聊天接口，为多模态提示提供统一入口。 |
| `vision-mind-common` | 共享的 DTO、数学工具以及图像/向量辅助方法。 |
| `vision-mind-test-sth` | 用于集成实验和手工验证的测试沙箱。 |

## 环境准备

1. 安装 **JDK 17** 和 **Maven 3.8+**。
2. 下载所需的模型文件以及 OpenCV 原生运行库。设置环境变量 `VISION_MIND_PATH`，让所有模块都能定位到权重和 `.dll/.so` 文件：
   
   ```powershell
   # Windows PowerShell
   setx VISION_MIND_PATH "F:\TestSth\JavaVisionMind\resource"
   ```

   ```bash
   # Linux / macOS
   export VISION_MIND_PATH=/opt/JavaVisionMind/resource
   ```

   目录结构示例：

   ```text
   ${VISION_MIND_PATH}
   └── lib
       └── opencv
           ├── opencv_java490.dll   # Windows
           └── libopencv_java490.so # Linux
   ```

3. 确认 JVM 能够根据操作系统加载 `opencv_java490`（服务会自动选择 `.dll` 或 `.so`）。
4. 从项目的发布页面下载 `resource.7z`，解压到仓库根目录，使模型文件与各模块同级（例如 `resource/yolo/model/yolo.onnx`）。

> **提示**：若仅需调试非图像模块，可在 JVM 参数中加入 `-Dvision-mind.skip-opencv=true` 暂时跳过 OpenCV 加载。

## 快速开始

### 构建

```bash
mvn clean install -DskipTests
```

### 启动服务

- YOLO 图像分析：`mvn -pl vision-mind-yolo-app spring-boot:run`
- 人脸特征服务：`mvn -pl vision-mind-ffe-app spring-boot:run`
- 行人重识别：`mvn -pl vision-mind-reid-app spring-boot:run`
- 文本图像检索：`mvn -pl vision-mind-tbir-app spring-boot:run`
- LLM 对话网关：`mvn -pl vision-mind-llm-core spring-boot:run`

所有服务默认以 `/api` 作为上下文路径，可在各模块的 `application.properties` 中调整端口与路径。

### 向量存储开关

- `vision-mind-ffe-app`、`vision-mind-reid-app` 与 `vision-mind-tbir-app` 暴露 `vector.store.mode` 配置。
- 取值 `lucene`（默认）时将向量持久化到磁盘，`memory` 使用内置 chroma 向量库运行于内存，`elasticsearch` 可接入外部 ES 集群。
- 选择 Elasticsearch 模式时会直接写入全维度向量；仅有 Lucene 后端会应用 ReID 投影矩阵。

## 接口概览

### vision-mind-yolo-app（图像分析）

| 方法 | 路径 | 说明 | 请求体 | 响应 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/img/detect` | 执行目标检测，可配置包含/排除多边形区域。 | `DetectionRequestWithArea` JSON（字段：`imgUrl`, `threshold?`, `types?`, `detectionFrames?`, `blockingFrames?`） | `HttpResult<List<Box>>` |
| POST | `/api/v1/img/detectI` | 与上一接口相同，但返回标注后的图像。 | `DetectionRequestWithArea` | `image/jpeg` 字节流 |
| POST | `/api/v1/img/detectFace` | 检测指定区域内的人脸。 | `DetectionRequestWithArea` | `HttpResult<List<Box>>` |
| POST | `/api/v1/img/detectFaceI` | 人脸检测并返回可视化图像。 | `DetectionRequestWithArea` | `image/jpeg` 字节流 |
| POST | `/api/v1/img/pose` | 人体姿态估计。 | `DetectionRequestWithArea` | `HttpResult<List<BoxWithKeypoints>>` |
| POST | `/api/v1/img/poseI` | 姿态估计并叠加骨架预览。 | `DetectionRequestWithArea` | `image/jpeg` 字节流 |
| POST | `/api/v1/img/sam` | FAST-SAM 分割，输出边界框。 | `DetectionRequest`（字段：`imgUrl`, `threshold?`, `types?`） | `HttpResult<List<Box>>` |
| POST | `/api/v1/img/samI` | FAST-SAM 分割的图像可视化。 | `DetectionRequest` | `image/jpeg` 字节流 |
| POST | `/api/v1/img/seg` | YOLO 分割输出掩码信息。 | `DetectionRequestWithArea` | `HttpResult<List<SegDetection>>` |
| POST | `/api/v1/img/segI` | 分割结果的图像可视化。 | `DetectionRequestWithArea` | `image/jpeg` 字节流 |

### vision-mind-ffe-app（人脸特征提取）

| 方法 | 路径 | 说明 | 请求体 | 响应 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/face/computeFaceVector` | 检测人脸并返回特征，不做持久化。 | `InputWithUrl`（字段：`imgUrl`, `groupId?`, `faceScoreThreshold?`） | `HttpResult<FaceImage>` |
| POST | `/api/v1/face/saveFaceVector` | 保存外部计算好的人脸向量。 | `Input4Save`（字段：`imgUrl`, `groupId`, `id`, `embeds`） | `HttpResult<Void>` |
| POST | `/api/v1/face/computeAndSaveFaceVector` | 检测人脸、筛选高质量向量并保存，同时返回新增项。 | `InputWithUrl` | `HttpResult<List<FaceInfo4Add>>` |
| POST | `/api/v1/face/deleteFace` | 按文档 ID 删除已存向量。 | `Input4Del`（字段：`id`） | `HttpResult<Void>` |
| POST | `/api/v1/face/findMostSimilarFace` | 使用探测图像检索索引。 | `Input4Search`（字段：`imgUrl`, `groupId?`, `faceScoreThreshold?`, `confidenceThreshold?`） | `HttpResult<List<FaceInfo4Search>>` |
| POST | `/api/v1/face/findMostSimilarFaceI` | 返回最佳匹配的人脸预览图。 | `Input4Search` | `image/jpeg` 字节流 |
| POST | `/api/v1/face/calculateSimilarity` | 计算两张图片的余弦相似度。 | `Input4Compare`（字段：`imgUrl`, `imgUrl2`） | `HttpResult<Double>` |
| POST | `/api/v1/face/findSave` | 先检索，无命中则写入索引。 | `Input4Search` | `HttpResult<FaceInfo4SearchAdd>` |

### vision-mind-reid-app（行人重识别）

| 方法 | 路径 | 说明 | 请求体 | 响应 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/reid/feature/single` | 提取单个人体的向量。 | JSON `{ "imgUrl": "..." }` | `HttpResult<Feature>` |
| POST | `/api/v1/reid/feature/calculateSimilarity` | 比较两个人体区域的相似度。 | JSON `{ "imgUrl1": "...", "imgUrl2": "..." }` | `HttpResult<Float>` |
| POST | `/api/v1/reid/feature/multi` | 检测多个人体并返回每个向量。 | JSON `{ "imgUrl": "..." }` | `HttpResult<List<Feature>>` |
| POST | `/api/v1/reid/store/single` | 提取并持久化向量，同时保存元数据。 | JSON `{ "imgUrl": "...", "cameraId?": "...", "humanId?": "..." }` | `HttpResult<Feature>` |
| POST | `/api/v1/reid/search` | 通过图像检索图库。 | JSON `{ "imgUrl": "...", "cameraId?": "...", "topN": ..., "threshold": ... }` | `HttpResult<List<Human>>` |
| POST | `/api/v1/reid/searchOrStore` | 先检索；未命中则插入。 | JSON `{ "imgUrl": "...", "threshold": ... }` | `HttpResult<Human>` |
| POST | `/api/v1/reid/associateStore` | 总是存储探测图像，并关联命中的对象。 | JSON `{ "imgUrl": "...", "threshold": ... }` | `HttpResult<Human>` |

### vision-mind-tbir-app（文本图像检索）

| 方法 | 路径 | 说明 | 请求体 | 响应 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/tbir/saveImg` | 图像入库：检测、增强、向量化并索引。 | `SaveImageRequest`（字段：`imgUrl`, `imgId?`, `cameraId?`, `groupId?`, `meta?`, `threshold?`, `types?`） | `HttpResult<ImageSaveResult>` |
| POST | `/api/v1/tbir/deleteImg` | 从索引中删除图像及其衍生内容。 | `DeleteImageRequest`（字段：`imgId`） | `HttpResult<Void>` |
| POST | `/api/v1/tbir/searchImg` | 按存储的图像 ID 查询元数据。 | `SearchImageRequest`（字段：`imgId`） | `HttpResult<SearchResult>` |
| POST | `/api/v1/tbir/searchImgI` | 基于图像 ID 绘制目标框并返回预览。 | `SearchImageRequest` | `image/jpeg` 字节流 |
| POST | `/api/v1/tbir/search` | 文本检索图像。 | `SearchRequest`（字段：`query`, `cameraId?`, `groupId?`, `topN?`） | `HttpResult<SearchResult>` |
| POST | `/api/v1/tbir/searchI` | 文本检索并返回图像预览。 | `SearchRequest` | `image/jpeg` 字节流 |
| POST | `/api/v1/tbir/imgSearch` | 上传图像进行以图搜图。 | `multipart/form-data`（`image`, `topN`） | `HttpResult<SearchResult>` |

> **DTO 快速参考**
>
> - `SaveImageRequest` 继承 `DetectionRequestWithArea`，新增可选字段 `imgId`、`cameraId`、`groupId` 与自定义 `meta`。
> - `SearchResult` 聚合 `HitImage` 列表（包含图片 URL、标注框和分数）。
> - `HitImage` 在可视化接口中保留匹配框信息，便于前端绘制。

### vision-mind-llm-core（语言服务）

| 方法 | 路径 | 说明 | 请求体 | 响应 |
| --- | --- | --- | --- | --- |
| POST | `/api/translate` | 将文本从中文翻译成英文。 | `Message`（字段：`message`, `img?`） | 纯文本 |
| POST | `/api/chat` | 通用对话接口。 | `Message`（字段：`message`） | 纯文本 |
| POST | `/api/chatWithImg` | 图文多模态对话。 | `Message`（字段：`message`, `img`） | 纯文本 |

## 资源下载

- 仓库根目录提供 Postman 集合：`JavaVisionMind.postman_collection.json`。
- 各模块的默认配置位于 `src/main/resources/application.properties`。

## 接口流程参考

### vision-mind-yolo-app

#### /api/v1/img/detect
1. 控制器校验 `imgUrl` 并记录调用日志后再下发请求（vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:45）。
2. `ImgAnalysisService.detectArea` 将图像下载为 OpenCV Mat（vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:70）。
3. `analysis` 执行 YOLOv11 推理，将原始输出映射为 `Box` 并按请求类别过滤（vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:121）。
4. 结果根据包含/排除多边形及比例要求进行过滤（vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:82）。
5. 剩余的框由控制器封装成 `HttpResult` 返回（vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:60）。

#### /api/v1/img/detectI
1. 控制器重复参数校验并记录耗时（vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:70）。
2. `detectAreaI` 将图像渲染为 `BufferedImage`，内部复用 `detectArea`（vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:110）。
3. 服务在返回前绘制包含/阻断区域及检测框，控制器以 JPEG 字节流输出（vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:80）。

#### /api/v1/img/detectFace
1. 控制器校验载荷（vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:99）。
2. `ImgAnalysisService.detectFace` 调用人脸专用的 YOLO 模型（vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:213）。
3. 与通用检测相同，按照包含/排除多边形过滤人脸框（vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:220）。
4. 过滤后的框回传给控制器包装响应（vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:112）。

#### /api/v1/img/detectFaceI
1. 控制器执行与 JSON 接口相同的校验（vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:118）。
2. `detectFaceI` 绘制人脸框与包含/排除区域，生成标注图（vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:253）。
3. 控制器输出 JPEG 字节流（vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:128）。

#### /api/v1/img/pose
1. 控制器校验载荷并记录日志（vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:147）。
2. `poseArea` 调用 YOLOv11 姿态模型并应用多边形过滤（vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:148）。
3. 筛选后的 `BoxWithKeypoints` 返回给控制器（vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:160）。

#### /api/v1/img/poseI
1. 控制器完成参数校验（vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:173）。
2. `poseAreaI` 复用 `poseArea`，绘制骨架连线并返回 `BufferedImage`（vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:187）。
3. 控制器将结果转换为 JPEG（vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:183）。

#### /api/v1/img/sam
1. 控制器校验参数并直接转发（vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:197）。
2. `sam` 执行 FastSAM 分割并返回边界框列表（vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:279）。

#### /api/v1/img/samI
1. 控制器校验请求（vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:216）。
2. `samI` 在原图上绘制 FastSAM 的框并返回标注图像（vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:284）。

#### /api/v1/img/seg
1. 控制器校验载荷后调用服务（vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:260）。
2. `segArea` 执行分割并按类别生成多边形（vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:294）。

#### /api/v1/img/segI
1. 控制器将请求转发给服务（vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:238）。
2. `segAreaI` 在原图上绘制分割多边形并返回图像（vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:299）。

### vision-mind-ffe-app

#### /api/v1/face/computeFaceVector
1. 控制器校验 `imgUrl` 并记录日志（vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/controller/FaceController.java:60）。
2. `FaceService.computeFaceVector` 提取人脸与对应特征（vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/service/FaceService.java:142）。
3. `getFaceInfos` 在返回前剥离 Base64 数据（vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/service/FaceService.java:154）。

#### /api/v1/face/saveFaceVector
1. 控制器确认向量信息齐全（vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/controller/FaceController.java:78）。
2. `saveFaceVector` 通过 `FfeVectorStoreUtil.add` 持久化向量（vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/service/FaceService.java:95）。

#### /api/v1/face/computeAndSaveFaceVector
1. 控制器校验载荷（vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/controller/FaceController.java:96）。
2. `computeAndSaveFaceVector` 按阈值过滤人脸、保存合格向量并返回精简列表（vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/service/FaceService.java:77）。

#### /api/v1/face/deleteFace
1. 控制器检查文档 ID（vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/controller/FaceController.java:118）。
2. `delete` 删除对应 Lucene 记录（vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/service/FaceService.java:105）。

#### /api/v1/face/findMostSimilarFace
1. 控制器校验阈值设置（vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/controller/FaceController.java:135）。
2. `findMostSimilarFace` 执行特征提取、质量过滤并在 Lucene 中检索 Top-1（vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/service/FaceService.java:116）。

#### /api/v1/face/findMostSimilarFaceI
1. 控制器重复载荷校验（vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/controller/FaceController.java:153）。
2. 控制器将服务返回的最佳匹配图像直接流式输出（vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/controller/FaceController.java:163）。

#### /api/v1/face/calculateSimilarity
1. 控制器确认提供了两条 URL（vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/controller/FaceController.java:186）。
2. `calculateSimilarity` 提取两张图的特征、归一化后计算余弦相似度（vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/service/FaceService.java:177）。

#### /api/v1/face/findSave
1. 控制器校验请求（vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/controller/FaceController.java:212）。
2. `findSave` 对每个人脸先检索，再对未命中项执行入库并返回新增与命中结果（vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/service/FaceService.java:197）。

### vision-mind-reid-app

#### /api/v1/reid/feature/single
1. 控制器校验请求体（vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/controller/ReidController.java:23）。
2. `featureSingle` 计算探测图的向量并附加 UUID（vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/service/ReidService.java:75）。

#### /api/v1/reid/feature/calculateSimilarity
1. 控制器检查两条 URL（vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/controller/ReidController.java:39）。
2. `calculateSimilarity` 生成两条向量并计算余弦相似度（vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/service/ReidService.java:82）。

#### /api/v1/reid/feature/multi
1. 控制器校验载荷（vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/controller/ReidController.java:56）。
2. `featureMulti` 通过 `ImgAnalysisService.detectArea` 执行检测、裁剪行人、提取向量并返回集合（vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/service/ReidService.java:89）。

#### /api/v1/reid/store/single
1. 控制器要求必要的 ID 字段（vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/controller/ReidController.java:72）。
2. `storeSingle` 提取向量、生成 UUID，并调用 `ReidVectorStoreUtil.add` 持久化（vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/service/ReidService.java:109）。

#### /api/v1/reid/search
1. 控制器校验 `imgUrl`、`topN` 与 `threshold`（vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/controller/ReidController.java:106）。
2. `search` 计算探测向量，并在 Lucene 中按可选摄像头限制检索匹配（vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/service/ReidService.java:117）。

#### /api/v1/reid/searchOrStore
1. 控制器校验请求体（vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/controller/ReidController.java:125）。
2. `searchOrStore` 优先返回最优匹配，若未命中则持久化新向量（vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/service/ReidService.java:123）。

#### /api/v1/reid/associateStore
1. 控制器校验请求（vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/controller/ReidController.java:142）。
2. `associateStore` 先检索匹配，再无条件保存新向量，并与命中对象建立关联（vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/service/ReidService.java:138）。

### vision-mind-tbir-app

#### /api/v1/tbir/saveImg
1. 控制器校验载荷（vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/controller/TbirController.java:46）。
2. `saveImg` 生成或复用 `imgId`，可选执行 YOLO/FastSAM 检测、裁剪增强子图，使用 CLIP 向量化主图与子图，并携带元数据入库（vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/service/TbirService.java:61）。

#### /api/v1/tbir/deleteImg
1. 控制器检查 `imgId`（vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/controller/TbirController.java:66）。
2. 服务端实现目前仍为 TODO，保留占位逻辑（vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/service/TbirService.java:167）。

#### /api/v1/tbir/searchImg
1. 控制器校验请求（vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/controller/TbirController.java:82）。
2. `searchImg` 按存储的 ID 聚合 Lucene 命中结果并转换为 `HitImage` DTO（vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/service/TbirService.java:321）。

#### /api/v1/tbir/searchImgI
1. 控制器校验载荷（vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/controller/TbirController.java:98）。
2. `searchImgI` 复用 `searchImg`，下载匹配图片、绘制框并返回缓冲图像（vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/service/TbirService.java:331）。

#### /api/v1/tbir/search
1. 控制器校验文本查询（vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/controller/TbirController.java:124）。
2. `searchByText` 通过 LLM 扩展提示，使用 CLIP 编码，查询 Lucene，借助 `getFinalList` 融合结果并返回排序后的 `HitImage`（vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/service/TbirService.java:182）。

#### /api/v1/tbir/searchI
1. 控制器校验并转发请求（vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/controller/TbirController.java:143）。
2. `searchByTextI` 在每张结果图上绘制匹配框用于流式预览（vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/service/TbirService.java:285）。

#### /api/v1/tbir/imgSearch
1. 控制器接收 multipart 上传（vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/controller/TbirController.java:170）。
2. `imgSearch` 对探测图进行向量化，查询 Lucene 并返回排序结果（vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/service/TbirService.java:302）。

### vision-mind-llm-core

#### /api/translate
1. 控制器封装翻译提示后调用服务（vision-mind-llm-core/src/main/java/com/yuqiangdede/llm/controller/ChatController.java:23）。
2. `LLMService.chat` 校验输入并路由到 OpenAI 或 Ollama，若两者均未配置则抛出异常（vision-mind-llm-core/src/main/java/com/yuqiangdede/llm/service/LLMService.java:22）。

#### /api/chat
1. 控制器转发自由形式的提示（vision-mind-llm-core/src/main/java/com/yuqiangdede/llm/controller/ChatController.java:39）。
2. `LLMService.chat` 与上相同，负责选择具体提供方（vision-mind-llm-core/src/main/java/com/yuqiangdede/llm/service/LLMService.java:22）。

#### /api/chatWithImg
1. 控制器校验文本与可选图像参数（vision-mind-llm-core/src/main/java/com/yuqiangdede/llm/controller/ChatController.java:50）。
2. `chatWithImg` 补充默认系统提示（若缺失），并调用配置好的 OpenAI 视觉端点（vision-mind-llm-core/src/main/java/com/yuqiangdede/llm/service/LLMService.java:49）。

*待办提示：若需要完善删除能力，请实现 vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/service/TbirService.java:167 中的 TODO。*

## 路线图

- 支持 LLaMA 等离线大模型的流式响应。
- 为 Elasticsearch 向量后端补充更完善的索引维护与监控工具。
- 在 `vision-mind-yolo-core` 中恢复 YOLO 视频流处理管线。

欢迎通过 Issue 或 PR 参与贡献。

## 可扩展功能灵感

以下是基于现有模块可以进一步演进的方向，方便团队在规划后续迭代时参考：

- **多目标跟踪（MOT）**：在 `vision-mind-yolo-core` 内引入 DeepSORT、ByteTrack 等跟踪器，与检测结果结合以输出跨帧的目标轨迹，可用于安防巡检或行人路径分析。
- **细粒度属性识别**：为行人、人脸或车辆等对象增加属性分类（如性别、服饰颜色、车牌区域），以丰富向量索引中的检索条件。
- **视频结构化处理流水线**：构建批量视频解析服务，对关键帧执行检测、分割、重识别并归档结果，满足大规模视频入库或案件回溯需求。
- **跨摄像头轨迹关联**：基于现有重识别能力叠加时空约束，实现跨机位的目标身份关联与告警规则。
- **更丰富的多模态交互**：在 `vision-mind-llm-core` 中加入图像字幕生成、视觉问答（VQA）或提示模板管理，提升图文问答的可用性。
- **模型管理与监控**：提供统一的模型版本管理、在线热更新与推理性能监控面板，便于在生产环境中运维多种模型。


