# JavaVisionMind

[中文文档](README.zh-CN.md) | [English](README.md)

> A modular Spring Boot toolkit that bundles computer-vision and multimodal utilities such as detection, retrieval, and LLM integration.

## Table of Contents
- [Overview](#overview)
- [Repository Layout](#repository-layout)
- [Environment Setup](#environment-setup)
- [Quick Start](#quick-start)
- [API Reference](#api-reference)
  - [vision-mind-yolo-app](#vision-mind-yolo-app-image-analysis)
  - [vision-mind-ocr-app](#vision-mind-ocr-app-optical-character-recognition)
  - [vision-mind-ffe-app](#vision-mind-ffe-app-face-feature-extraction)
  - [vision-mind-reid-app](#vision-mind-reid-app-person-re-identification)
  - [vision-mind-tbir-app](#vision-mind-tbir-app-text-based-image-retrieval)
  - [vision-mind-llm-core](#vision-mind-llm-core-language-services)
- [Resources](#resources)
- [Endpoint Flow Reference](#endpoint-flow-reference)
- [Roadmap](#roadmap)

## Overview

JavaVisionMind is a collection of independent Spring Boot services that cover object detection, pose estimation, face recognition, person re-identification, text-based image retrieval, and large-language-model interactions. Each capability ships as a separate module so you can deploy only what you need.


## Repository Layout

| Module | Description |
| --- | --- |
| `vision-mind-yolo-core` | Core inference utilities for YOLOv11, FAST-SAM, pose estimation, and segmentation models. |
| `vision-mind-yolo-app` | REST facade that exposes the image-analysis capabilities from `vision-mind-yolo-core`. |
| `vision-mind-ocr-core` | PaddleOCR detector/recognizer/classifier pipeline reused by the OCR service. |
| `vision-mind-ocr-app` | REST wrapper that surfaces OCR results as JSON or annotated images. |
| `vision-mind-ffe-app` | Face feature extraction service including detection, alignment, similarity search, and index maintenance. |
| `vision-mind-reid-app` | Person re-identification workflows backed by Lucene for vector retrieval. |
| `vision-mind-tbir-app` | Text-Based Image Retrieval service built on CLIP embeddings plus Lucene vector search. |
| `vision-mind-llm-core` | Wrapper around OpenAI/Ollama style chat endpoints that powers multimodal prompts. |
| `vision-mind-common` | Shared DTOs, math helpers, and image/vector utilities. |
| `vision-mind-test-sth` | Scratchpad used for integration experiments and manual verification. |

## Environment Setup

1. Install **JDK 17** and **Maven 3.8+**.
2. Download the required model bundles and OpenCV native runtime. Define the `VISION_MIND_PATH` environment variable so every module can locate weights and `.dll/.so` files:

   ```bash
   # Windows PowerShell
   setx VISION_MIND_PATH "F:\\TestSth\\JavaVisionMind\\resource"

   # Linux / macOS shell
   export VISION_MIND_PATH=/opt/JavaVisionMind/resource
   ```

   Expected structure:

   ```text
   ${VISION_MIND_PATH}
   |-- lib
       |-- opencv
           |-- opencv_java490.dll   # Windows
           `-- libopencv_java490.so # Linux
   ```

3. Verify the JVM can load `opencv_java490` for your OS (the services auto-pick `.dll` or `.so`).
4. Download `resource.7z` from the project release page, extract it to the repository root so that model files sit alongside the modules (for example `resource/yolo/model/yolo.onnx`).

## Quick Start

### Build

```bash
mvn clean install -DskipTests
```

### Run Services

- YOLO API: `mvn -pl vision-mind-yolo-app spring-boot:run`
- OCR service: `mvn -pl vision-mind-ocr-app spring-boot:run`
- Face feature service: `mvn -pl vision-mind-ffe-app spring-boot:run`
- Person re-identification: `mvn -pl vision-mind-reid-app spring-boot:run`
- Text-based image retrieval: `mvn -pl vision-mind-tbir-app spring-boot:run`
- LLM chat facade: `mvn -pl vision-mind-llm-core spring-boot:run`

Each service uses `/api` as the context root. Default ports can be overridden in the respective `application.properties`.

### Vector storage toggle

- `vision-mind-ffe-app`, `vision-mind-reid-app`, and `vision-mind-tbir-app` expose a `vector.store.mode` switch.
- Set to `lucene` (default) to persist vectors on disk, `memory` to use the embedded chroma store, or `elasticsearch` to back vectors with an external ES cluster.
- The Elasticsearch mode shares full-dimension embeddings; only the Lucene backend applies the ReID projection matrix.

## API Reference

Below tables outline the primary REST endpoints exposed by each runnable module. `HttpResult<T>` denotes the project-wide response wrapper containing `success`, `message`, and `data` fields.

### vision-mind-yolo-app (Image Analysis)

| Method | Path | Description | Request Body | Response |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/img/detect` | Run object detection within optional include/exclude polygons. | `DetectionRequestWithArea` JSON (`imgUrl`, `threshold?`, `types?`, `detectionFrames?`, `blockingFrames?`) | `HttpResult<List<Box>>` |
| POST | `/api/v1/img/detectI` | Same as above but returns the annotated image. | `DetectionRequestWithArea` | `image/jpeg` bytes |
| POST | `/api/v1/img/detectFace` | Detect faces in given regions. | `DetectionRequestWithArea` | `HttpResult<List<Box>>` |
| POST | `/api/v1/img/detectFaceI` | Face detection with inline visualization. | `DetectionRequestWithArea` | `image/jpeg` bytes |
| POST | `/api/v1/img/pose` | Human pose estimation. | `DetectionRequestWithArea` | `HttpResult<List<BoxWithKeypoints>>` |
| POST | `/api/v1/img/poseI` | Pose estimation with skeleton overlay. | `DetectionRequestWithArea` | `image/jpeg` bytes |
| POST | `/api/v1/img/sam` | FAST-SAM segmentation, returns bounding boxes. | `DetectionRequest` (`imgUrl`, `threshold?`, `types?`) | `HttpResult<List<Box>>` |
| POST | `/api/v1/img/samI` | FAST-SAM segmentation visualization. | `DetectionRequest` | `image/jpeg` bytes |
| POST | `/api/v1/img/seg` | YOLO segmentation output with masks. | `DetectionRequestWithArea` | `HttpResult<List<SegDetection>>` |
| POST | `/api/v1/img/segI` | Segmentation visualization. | `DetectionRequestWithArea` | `image/jpeg` bytes |

### vision-mind-ocr-app (Optical Character Recognition)

| Method | Path | Description | Request Body | Response |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/ocr/detect` | Run PaddleOCR text detection/recognition with optional include/exclude polygons and switchable light (`det/rec.onnx`) or heavy (`det2/rec2.onnx`) models. | `OcrDetectionRequest` (`detectionLevel?`, `imgUrl`, `detectionFrames?`, `blockingFrames?`) | `HttpResult<List<OcrDetectionResult>>` |
| POST | `/api/v1/ocr/detect-image` | Same as above but streams the annotated image. | `OcrDetectionRequest` | `image/jpeg` bytes |

### vision-mind-ffe-app (Face Feature Extraction)

| Method | Path | Description | Request Body | Response |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/face/computeFaceVector` | Detect faces and return embeddings without persisting. | `InputWithUrl` (`imgUrl`, `groupId?`, `faceScoreThreshold?`) | `HttpResult<FaceImage>` |
| POST | `/api/v1/face/saveFaceVector` | Persist an externally computed face vector. | `Input4Save` (`imgUrl`, `groupId`, `id`, `embeds`) | `HttpResult<Void>` |
| POST | `/api/v1/face/computeAndSaveFaceVector` | Detect faces, store high-quality embeddings, and return inserted items. | `InputWithUrl` | `HttpResult<List<FaceInfo4Add>>` |
| POST | `/api/v1/face/deleteFace` | Remove a stored face vector by document ID. | `Input4Del` (`id`) | `HttpResult<Void>` |
| POST | `/api/v1/face/findMostSimilarFace` | Search the index with a probe image. | `Input4Search` (`imgUrl`, `groupId?`, `faceScoreThreshold?`, `confidenceThreshold?`) | `HttpResult<List<FaceInfo4Search>>` |
| POST | `/api/v1/face/findMostSimilarFaceI` | Retrieve the best match preview image. | `Input4Search` | `image/jpeg` bytes |
| POST | `/api/v1/face/calculateSimilarity` | Compare two image URLs using cosine similarity. | `Input4Compare` (`imgUrl`, `imgUrl2`) | `HttpResult<Double>` |
| POST | `/api/v1/face/findSave` | Search first; if nothing matches insert the face into the index. | `Input4Search` | `HttpResult<FaceInfo4SearchAdd>` |

### vision-mind-reid-app (Person Re-identification)

| Method | Path | Description | Request Body | Response |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/reid/feature/single` | Extract a single body feature vector. | JSON map `{ "imgUrl": "..." }` | `HttpResult<Feature>` |
| POST | `/api/v1/reid/feature/calculateSimilarity` | Compare two person crops. | JSON map `{ "imgUrl1", "imgUrl2" }` | `HttpResult<Float>` |
| POST | `/api/v1/reid/feature/multi` | Detect multiple persons and return vectors for each. | JSON map `{ "imgUrl": "..." }` | `HttpResult<List<Feature>>` |
| POST | `/api/v1/reid/store/single` | Extract and store a feature with metadata. | JSON map `{ "imgUrl", "cameraId?", "humanId?" }` | `HttpResult<Feature>` |
| POST | `/api/v1/reid/search` | Search the gallery by image. | JSON map `{ "imgUrl", "cameraId?", "topN", "threshold" }` | `HttpResult<List<Human>>` |
| POST | `/api/v1/reid/searchOrStore` | Single-cover workflow: search first, otherwise insert. | JSON map `{ "imgUrl", "threshold" }` | `HttpResult<Human>` |
| POST | `/api/v1/reid/associateStore` | Multi-cover workflow: always store the probe and link to the match. | JSON map `{ "imgUrl", "threshold" }` | `HttpResult<Human>` |

### vision-mind-tbir-app (Text-Based Image Retrieval)

| Method | Path | Description | Request Body | Response |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/tbir/saveImg` | Ingest an image: detect, augment, vectorize, and index. | `SaveImageRequest` (`imgUrl`, `imgId?`, `cameraId?`, `groupId?`, `meta?`, `threshold?`, `types?`) | `HttpResult<ImageSaveResult>` |
| POST | `/api/v1/tbir/deleteImg` | Remove an image and its variants from the index. | `DeleteImageRequest` (`imgId`) | `HttpResult<Void>` |
| POST | `/api/v1/tbir/searchImg` | Retrieve metadata by stored image ID. | `SearchImageRequest` (`imgId`) | `HttpResult<SearchResult>` |
| POST | `/api/v1/tbir/searchImgI` | Render bounding boxes for search results of an image ID. | `SearchImageRequest` | `image/jpeg` bytes |
| POST | `/api/v1/tbir/search` | Text-to-image retrieval. | `SearchRequest` (`query`, `cameraId?`, `groupId?`, `topN?`) | `HttpResult<SearchResult>` |
| POST | `/api/v1/tbir/searchI` | Text-to-image retrieval with visualization. | `SearchRequest` | `image/jpeg` bytes |
| POST | `/api/v1/tbir/imgSearch` | Image-to-image search via multipart upload. | `multipart/form-data` (`image`, `topN`) | `HttpResult<SearchResult>` |

> **DTO quick reference**
>
> - `SaveImageRequest` extends `DetectionRequestWithArea`, adding optional `imgId`, `cameraId`, `groupId`, and arbitrary metadata map.
> - `SearchResult` wraps a list of `HitImage` entries (image URL, boxes, score, metadata).
> - `HitImage` retains matched sub-boxes for visualization endpoints.

### vision-mind-llm-core (Language Services)

| Method | Path | Description | Request Body | Response |
| --- | --- | --- | --- | --- |
| POST | `/api/translate` | Prompt the configured LLM to translate Chinese text to English. | `Message` (`message`, optional `img`) | Plain text |
| POST | `/api/chat` | Free-form chat completion. | `Message` (`message`) | Plain text |
| POST | `/api/chatWithImg` | Multimodal chat using an image URL/base64 plus prompt. | `Message` (`message`, `img`) | Plain text |

## Resources

- `JavaVisionMind.postman_collection.json` (repository root) provides ready-to-run Postman/Apifox requests for every endpoint.
- Model configuration lives under each module鈥檚 `src/main/resources/application*.properties` for per-service tuning.

## Roadmap

- LLaMA deployment support with streaming responses.
- Alternative in-memory vector backends alongside Lucene.
- YOLO video-stream processing pipeline resurrection in `vision-mind-yolo-core`.



## Endpoint Flow Reference

### vision-mind-yolo-app

#### /api/v1/img/detect
1. Controller validates imgUrl and logs before delegating (vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:45).
2. ImgAnalysisService.detectArea downloads the image into an OpenCV Mat (vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:70).
3. analysis runs YOLOv11 inference, maps raw outputs to Box objects, and filters by requested class IDs (vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:121).
4. Detections must overlap include polygons and avoid block polygons according to the configured ratios before they are returned (vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:82).
5. Remaining boxes are wrapped in HttpResult and returned (vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:60).

#### /api/v1/img/detectI
1. Controller repeats validation and timing (vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:70).
2. detectAreaI renders the image as BufferedImage and reuses detectArea (vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:110).
3. Include/block frames and boxes are drawn over the image before the controller streams JPEG bytes (vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:80).

#### /api/v1/img/detectFace
1. Controller checks the payload (vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:99).
2. ImgAnalysisService.detectFace runs the face-trained YOLO model (vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:213).
3. Polygon filtering is applied identically to generic detections (vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:220).
4. Boxes are returned to the controller for response wrapping (vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:112).

#### /api/v1/img/detectFaceI
1. Validation mirrors the JSON endpoint (vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:118).
2. detectFaceI draws bounding boxes plus include/exclude frames and returns the annotated image (vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:253).
3. Controller streams the JPEG bytes (vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:128).

#### /api/v1/img/pose
1. Controller validates payload and logs (vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:147).
2. poseArea invokes the YOLOv11 pose model and filters polygons (vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:148).
3. Filtered BoxWithKeypoints are returned (vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:160).

#### /api/v1/img/poseI
1. Controller handles validation (vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:173).
2. poseAreaI reuses poseArea, draws skeleton overlays, and returns a BufferedImage (vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:187).
3. Controller streams JPEG (vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:183).

#### /api/v1/img/sam
1. Controller validates and passes through (vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:197).
2. sam executes FastSAM segmentation and returns boxes (vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:279).

#### /api/v1/img/samI
1. Controller validates (vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:216).
2. samI draws FastSAM boxes onto the image and returns annotated bytes (vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:284).

#### /api/v1/img/seg
1. Controller checks payload and delegates (vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:260).
2. segArea runs segmentation and returns per-class polygons (vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:294).

#### /api/v1/img/segI
1. Controller forwards to the service (vision-mind-yolo-app/src/main/java/com/yuqiangdede/yolo/controller/ImgAnalysisController.java:238).
2. segAreaI draws segmentation polygons on the original image and returns them (vision-mind-yolo-core/src/main/java/com/yuqiangdede/yolo/service/ImgAnalysisService.java:299).

### vision-mind-ocr-app

#### /api/v1/ocr/detect
1. Controller validates input, logs timing, and delegates to the service (vision-mind-ocr-app/src/main/java/com/yuqiangdede/ocr/controller/OcrController.java:30).
2. `OcrService.detect` routes the request into the shared inference pipeline (vision-mind-ocr-core/src/main/java/com/yuqiangdede/ocr/service/OcrService.java:93).
3. `runInference` downloads the image, selects the light/heavy engine, executes PaddleOCR, and applies include/exclude polygons (vision-mind-ocr-core/src/main/java/com/yuqiangdede/ocr/service/OcrService.java:115).
4. Area-filtered detections are returned to the controller for wrapping (vision-mind-ocr-core/src/main/java/com/yuqiangdede/ocr/service/OcrService.java:146).

#### /api/v1/ocr/detect-image
1. Controller invokes the overlay variant and prepares HTTP headers (vision-mind-ocr-app/src/main/java/com/yuqiangdede/ocr/controller/OcrController.java:47).
2. `detectWithOverlayBytes` reuses `detectWithOverlay` and encodes the annotated image as JPEG (vision-mind-ocr-core/src/main/java/com/yuqiangdede/ocr/service/OcrService.java:107).
3. `detectWithOverlay` draws OCR polygons plus include/exclude frames prior to returning (vision-mind-ocr-core/src/main/java/com/yuqiangdede/ocr/service/OcrService.java:98).

### vision-mind-ffe-app

#### /api/v1/face/computeFaceVector
1. Controller validates imgUrl and logs (vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/controller/FaceController.java:60).
2. FaceService.computeFaceVector extracts faces and embeddings (vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/service/FaceService.java:142).
3. getFaceInfos strips base64 payloads before returning (vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/service/FaceService.java:154).

#### /api/v1/face/saveFaceVector
1. Controller demands vector info (vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/controller/FaceController.java:78).
2. saveFaceVector persists embeddings with FfeVectorStoreUtil.add (vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/service/FaceService.java:95).

#### /api/v1/face/computeAndSaveFaceVector
1. Controller validates payload (vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/controller/FaceController.java:96).
2. computeAndSaveFaceVector filters faces by the requested threshold, stores qualifying embeddings, and returns the trimmed list (vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/service/FaceService.java:77).

#### /api/v1/face/deleteFace
1. Controller checks document ID (vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/controller/FaceController.java:118).
2. delete removes the Lucene record (vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/service/FaceService.java:105).

#### /api/v1/face/findMostSimilarFace
1. Controller validates thresholds (vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/controller/FaceController.java:135).
2. findMostSimilarFace runs extraction, filters by quality, and executes a Lucene top-1 search (vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/service/FaceService.java:116).

#### /api/v1/face/findMostSimilarFaceI
1. Controller repeats validation (vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/controller/FaceController.java:153).
2. The controller streams the top match image returned by the service (vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/controller/FaceController.java:163).

#### /api/v1/face/calculateSimilarity
1. Controller ensures two URLs (vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/controller/FaceController.java:186).
2. calculateSimilarity extracts both embeddings, normalizes them, and computes cosine similarity (vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/service/FaceService.java:177).

#### /api/v1/face/findSave
1. Controller validates the request (vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/controller/FaceController.java:212).
2. findSave searches for each quality face, inserting any misses and returning both found and added items (vision-mind-ffe-app/src/main/java/com/yuqiangdede/ffe/service/FaceService.java:197).

### vision-mind-reid-app

#### /api/v1/reid/feature/single
1. Controller validates request (vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/controller/ReidController.java:23).
2. featureSingle embeds the probe and tags it with a UUID (vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/service/ReidService.java:75).

#### /api/v1/reid/feature/calculateSimilarity
1. Controller checks both URLs (vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/controller/ReidController.java:39).
2. calculateSimilarity embeds both probes and computes cosine similarity (vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/service/ReidService.java:82).

#### /api/v1/reid/feature/multi
1. Controller validates payload (vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/controller/ReidController.java:56).
2. featureMulti runs YOLO detection via ImgAnalysisService.detectArea, crops each person, embeds them, and returns the list (vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/service/ReidService.java:89).

#### /api/v1/reid/store/single
1. Controller enforces required IDs (vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/controller/ReidController.java:72).
2. storeSingle embeds the probe, assigns a UUID, and stores using ReidVectorStoreUtil.add (vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/service/ReidService.java:109).

#### /api/v1/reid/search
1. Controller validates imgUrl, topN, and threshold (vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/controller/ReidController.java:106).
2. search embeds the probe and queries Lucene for matching humans with optional camera scoping (vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/service/ReidService.java:117).

#### /api/v1/reid/searchOrStore
1. Controller validates payload (vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/controller/ReidController.java:125).
2. searchOrStore returns the best match or persists a new feature when none is found (vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/service/ReidService.java:123).

#### /api/v1/reid/associateStore
1. Controller validates request (vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/controller/ReidController.java:142).
2. associateStore searches for an existing match and always persists the new embedding, linking it to the matched human if available (vision-mind-reid-app/src/main/java/com/yuqiangdede/reid/service/ReidService.java:138).

### vision-mind-tbir-app

#### /api/v1/tbir/saveImg
1. Controller validates payload (vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/controller/TbirController.java:46).
2. saveImg generates or reuses imgId, optionally collects YOLO/FastSAM detections, crops and augments regions, embeds both main and sub-images with CLIP, and persists embeddings with metadata (vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/service/TbirService.java:61).

#### /api/v1/tbir/deleteImg
1. Controller checks imgId (vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/controller/TbirController.java:66).
2. deleteImg validates the identifier, invokes the vector store deletion, and records execution time (vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/service/TbirService.java:167).

#### /api/v1/tbir/searchImg
1. Controller validates (vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/controller/TbirController.java:82).
2. searchImg collects Lucene hits by stored ID and merges them into HitImage DTOs (vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/service/TbirService.java:321).

#### /api/v1/tbir/searchImgI
1. Controller validates payload (vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/controller/TbirController.java:98).
2. searchImgI reuses searchImg, downloads matched images, draws boxes, and returns buffered previews (vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/service/TbirService.java:331).

#### /api/v1/tbir/search
1. Controller validates query text (vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/controller/TbirController.java:124).
2. searchByText expands prompts via LLM, embeds each with CLIP, queries Lucene, merges hits through getFinalList, and returns ranked HitImage results (vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/service/TbirService.java:182).

#### /api/v1/tbir/searchI
1. Controller validates and delegates (vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/controller/TbirController.java:143).
2. searchByTextI draws matched boxes on each result image for preview streaming (vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/service/TbirService.java:285).

#### /api/v1/tbir/imgSearch
1. Controller accepts multipart upload (vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/controller/TbirController.java:170).
2. imgSearch embeds the probe image, queries Lucene, and returns ranked matches (vision-mind-tbir-app/src/main/java/com/yuqiangdede/tbir/service/TbirService.java:302).

### vision-mind-llm-core

#### /api/translate
1. Controller applies a translation prompt wrapper and delegates (vision-mind-llm-core/src/main/java/com/yuqiangdede/llm/controller/ChatController.java:23).
2. LLMService.chat validates input and routes to OpenAI or Ollama, throwing if neither is configured (vision-mind-llm-core/src/main/java/com/yuqiangdede/llm/service/LLMService.java:22).

#### /api/chat
1. Controller forwards the free-form prompt (vision-mind-llm-core/src/main/java/com/yuqiangdede/llm/controller/ChatController.java:39).
2. LLMService.chat handles provider selection as above (vision-mind-llm-core/src/main/java/com/yuqiangdede/llm/service/LLMService.java:22).

#### /api/chatWithImg
1. Controller validates text and optional image (vision-mind-llm-core/src/main/java/com/yuqiangdede/llm/controller/ChatController.java:50).
2. chatWithImg enforces payload completeness, injects a default system prompt if needed, and calls the configured OpenAI vision endpoint (vision-mind-llm-core/src/main/java/com/yuqiangdede/llm/service/LLMService.java:49).

Contributions and issue reports are welcome.

## Ideas for Future Enhancements

The following directions can extend the current toolkit and may serve as inspiration for upcoming releases:

- **Multi-object tracking (MOT)**: Integrate trackers such as DeepSORT or ByteTrack within `vision-mind-yolo-core` and pair them with detection outputs to provide cross-frame trajectories for security patrols or pedestrian-path analytics.
- **Fine-grained attribute recognition**: Add attribute classifiers for pedestrians, faces, or vehicles (e.g., gender, clothing color, license-plate region) so that vector indexes can support richer filtering.
- **Video structuring pipeline**: Build a batch video ingestion service that runs detection, segmentation, and re-identification on key frames, then archives the structured results for large-scale video libraries or case investigations.
- **Cross-camera association**: Combine the existing re-identification stack with spatiotemporal constraints to correlate identities across camera feeds and trigger rule-based alerts.
- **Richer multimodal interactions**: Extend `vision-mind-llm-core` with image captioning, visual question answering (VQA), or prompt-template management to improve multimodal Q&A use cases.
- **Model management & observability**: Provide unified model versioning, hot swapping, and inference performance dashboards to streamline operating multiple models in production.



