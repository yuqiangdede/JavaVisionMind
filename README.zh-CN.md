# JavaVisionMind

> 一个模块化的 Spring Boot 工具集，涵盖目标检测、图像检索与多模态 LLM 等视觉能力。

[English README](README.md)

## 目录
- [项目简介](#项目简介)
- [目录结构](#目录结构)
- [环境准备](#环境准备)
- [快速开始](#快速开始)
- [接口概览](#接口概览)
  - [vision-mind-yolo-app](#vision-mind-yolo-app-图像分析服务)
  - [vision-mind-ffe-app](#vision-mind-ffe-app-人脸特征服务)
  - [vision-mind-reid-app](#vision-mind-reid-app-行人重识别)
  - [vision-mind-tbir-app](#vision-mind-tbir-app-文本图像检索)
  - [vision-mind-llm-core](#vision-mind-llm-core-语言服务)
- [资源下载](#资源下载)
- [路线图](#路线图)

## 项目简介

JavaVisionMind 集成了多种视觉推理能力：目标检测、姿态估计、人脸识别、行人重识别、文本图像检索以及大模型交互。模块之间相互解耦，按需启动即可快速落地特定服务。

## 目录结构

| 模块 | 说明 |
| --- | --- |
| `vision-mind-yolo-core` | YOLOv11、FAST-SAM、姿态/分割推理的核心工具类。 |
| `vision-mind-yolo-app` | 将 `vision-mind-yolo-core` 能力暴露为 REST API。 |
| `vision-mind-ffe-app` | 人脸检测、对齐、特征提取与索引维护。 |
| `vision-mind-reid-app` | 基于 Lucene 的行人重识别及检索逻辑。 |
| `vision-mind-tbir-app` | 基于 CLIP 向量的文本图像检索服务。 |
| `vision-mind-llm-core` | OpenAI / Ollama 风格的对话与多模态封装。 |
| `vision-mind-common` | 通用 DTO、几何/图像工具方法。 |
| `vision-mind-test-sth` | 集成验证与实验脚本。 |

## 环境准备

1. 安装 **JDK 17** 与 **Maven 3.8+**。
2. 下载模型与 OpenCV 运行库，并设置 `VISION_MIND_PATH` 环境变量：

   ```powershell
   # Windows PowerShell
   setx VISION_MIND_PATH "F:\\TestSth\\JavaVisionMind\\resource"
   ```

   ```bash
   # Linux / macOS
   export VISION_MIND_PATH=/opt/JavaVisionMind/resource
   ```

   目录期望结构：

   ```text
   ${VISION_MIND_PATH}
   └── lib
       └── opencv
           ├── opencv_java490.dll   # Windows
           └── libopencv_java490.so # Linux
   ```

3. 解压 `resource.7z` 到仓库根目录（模型文件位于 `resource/...`）。
4. 确认 JVM 可以正常加载 `opencv_java490`。

> **提示**：如果只做单元测试，可在 JVM 中设置 `-Dvision-mind.skip-opencv=true`，框架会跳过原生库加载。

## 快速开始

### 构建

```bash
mvn clean install -DskipTests
```

### 启动单个服务

| 服务 | 启动命令 |
| --- | --- |
| YOLO 图像分析 | `mvn -pl vision-mind-yolo-app spring-boot:run` |
| 人脸特征提取 | `mvn -pl vision-mind-ffe-app spring-boot:run` |
| 行人重识别 | `mvn -pl vision-mind-reid-app spring-boot:run` |
| 文本图像检索 | `mvn -pl vision-mind-tbir-app spring-boot:run` |
| LLM 对话接口 | `mvn -pl vision-mind-llm-core spring-boot:run` |

服务默认监听 `/api` 前缀，可在各模块的 `application.properties` 中调整端口与路径。

## 接口概览

每个模块均使用统一的响应结构 `HttpResult<T>`，包含 `code`、`msg`、`data` 字段，`code="0"` 表示成功。

### vision-mind-yolo-app 图像分析服务

| 方法 | 路径 | 功能 | 请求体 | 响应 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/img/detect` | 目标检测，可指定检测/屏蔽多边形 | `DetectionRequestWithArea` | `HttpResult<List<Box>>` |
| POST | `/api/v1/img/detectI` | 检测并返回标注图 | `DetectionRequestWithArea` | `image/jpeg` |
| POST | `/api/v1/img/detectFace` | 人脸检测 | `DetectionRequestWithArea` | `HttpResult<List<Box>>` |
| POST | `/api/v1/img/detectFaceI` | 人脸检测标注图 | `DetectionRequestWithArea` | `image/jpeg` |
| POST | `/api/v1/img/pose` | 人体姿态估计 | `DetectionRequestWithArea` | `HttpResult<List<BoxWithKeypoints>>` |
| POST | `/api/v1/img/poseI` | 姿态估计标注图 | `DetectionRequestWithArea` | `image/jpeg` |
| POST | `/api/v1/img/sam` | FAST-SAM 分割，返回框列表 | `DetectionRequest` | `HttpResult<List<Box>>` |
| POST | `/api/v1/img/samI` | FAST-SAM 标注图 | `DetectionRequest` | `image/jpeg` |
| POST | `/api/v1/img/seg` | YOLO 分割结果 | `DetectionRequestWithArea` | `HttpResult<List<SegDetection>>` |
| POST | `/api/v1/img/segI` | YOLO 分割标注图 | `DetectionRequestWithArea` | `image/jpeg` |

### vision-mind-ffe-app 人脸特征服务

| 方法 | 路径 | 功能 | 请求体 | 响应 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/face/computeFaceVector` | 计算人脸特征并返回 | `InputWithUrl` | `HttpResult<FaceImage>` |
| POST | `/api/v1/face/saveFaceVector` | 保存人脸向量到索引 | `Input4Save` | `HttpResult<Void>` |
| POST | `/api/v1/face/computeAndSaveFaceVector` | 计算并批量入库 | `InputWithUrl` | `HttpResult<List<FaceInfo>>` |
| POST | `/api/v1/face/deleteFace` | 删除索引中的向量 | `Input4Del` | `HttpResult<Void>` |
| POST | `/api/v1/face/findMostSimilarFace` | 按图搜人 | `Input4Search` | `HttpResult<List<FaceInfo4Search>>` |
| POST | `/api/v1/face/findMostSimilarFaceI` | 搜索并返回示意图 | `Input4Search` | `image/jpeg` |
| POST | `/api/v1/face/calculateSimilarity` | 两张人脸相似度 | `Input4Compare` | `HttpResult<Double>` |
| POST | `/api/v1/face/findSave` | 搜索并自动补入索引 | `Input4Search` | `HttpResult<FaceInfo4SearchAdd>` |

### vision-mind-reid-app 行人重识别

| 方法 | 路径 | 功能 | 请求体 | 响应 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/reid/feature/single` | 提取单人特征 | `{ imgUrl }` | `HttpResult<Feature>` |
| POST | `/api/v1/reid/store/single` | 提取并写入索引 | `{ imgUrl, cameraId?, humanId? }` | `HttpResult<Feature>` |
| POST | `/api/v1/reid/feature/calculateSimilarity` | 两人相似度 | `{ imgUrl1, imgUrl2 }` | `HttpResult<Float>` |
| POST | `/api/v1/reid/feature/multi` | 多目标检测并提特征 | `{ imgUrl }` | `HttpResult<List<Feature>>` |
| POST | `/api/v1/reid/searchOrStore` | 单封面流程：未命中则入库 | `{ imgUrl, threshold }` | `HttpResult<Human>` |
| POST | `/api/v1/reid/associateStore` | 多封面流程：命中也入库 | `{ imgUrl, threshold }` | `HttpResult<Human>` |

### vision-mind-tbir-app 文本图像检索

| 方法 | 路径 | 功能 | 请求体 | 响应 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/tbir/saveImg` | 保存图片向量 | `SaveImageRequest` | `HttpResult<ImageSaveResult>` |
| POST | `/api/v1/tbir/deleteImg` | 删除索引图片 | `{ imgId }` | `HttpResult<Void>` |
| POST | `/api/v1/tbir/searchImg` | 按图片 ID 检索 | `{ imgId }` | `HttpResult<SearchResult>` |
| POST | `/api/v1/tbir/searchImgI` | 按图片 ID 检索并返回图像 | `{ imgId }` | `image/jpeg` |
| POST | `/api/v1/tbir/search` | 文本检索图片 | `SearchRequest` | `HttpResult<SearchResult>` |
| POST | `/api/v1/tbir/searchI` | 文本检索并返回图像 | `SearchRequest` | `image/jpeg` |
| POST | `/api/v1/tbir/imgSearch` | 上传图片检索 | `multipart/form-data` (`image`, `topN`) | `HttpResult<SearchResult>` |

> **DTO 速查**：`SearchResult` 由 `results` 与 `totalHits` 组成；`HitImage` 提供命中框信息，方便前端绘制。

### vision-mind-llm-core 语言服务

| 方法 | 路径 | 功能 | 请求体 | 响应 |
| --- | --- | --- | --- | --- |
| POST | `/api/translate` | 中文翻译英文 | `Message` (`message`, `img?`) | Plain text |
| POST | `/api/chat` | 通用对话 | `Message` (`message`) | Plain text |
| POST | `/api/chatWithImg` | 图像 + 文本多模态对话 | `Message` (`message`, `img`) | Plain text |

## 资源下载

- 仓库根目录提供 Postman 集合：`JavaVisionMind.postman_collection.json`。
- 模型与阈值配置位于各模块的 `src/main/resources/application.properties`。

## 路线图

- 支持 LLaMA 等本地大模型并开启流式回复。
- 在 Lucene 之外加入轻量向量数据库备选。
- 重构 YOLO 视频流处理能力，提升实时应用体验。

欢迎提交 Issue 或 PR，一起完善 JavaVisionMind！
