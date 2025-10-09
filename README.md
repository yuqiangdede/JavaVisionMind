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
  - [vision-mind-ffe-app](#vision-mind-ffe-app-face-feature-extraction)
  - [vision-mind-reid-app](#vision-mind-reid-app-person-re-identification)
  - [vision-mind-tbir-app](#vision-mind-tbir-app-text-based-image-retrieval)
  - [vision-mind-llm-core](#vision-mind-llm-core-language-services)
- [Resources](#resources)
- [Roadmap](#roadmap)

## Overview

JavaVisionMind is a collection of independent Spring Boot services that cover object detection, pose estimation, face recognition, person re-identification, text-based image retrieval, and large-language-model interactions. Each capability ships as a separate module so you can deploy only what you need.

## Repository Layout

| Module | Description |
| --- | --- |
| `vision-mind-yolo-core` | Core inference utilities for YOLOv11, FAST-SAM, pose estimation, and segmentation models. |
| `vision-mind-yolo-app` | REST facade that exposes the image-analysis capabilities from `vision-mind-yolo-core`. |
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
   鈹斺攢鈹€ lib
       鈹斺攢鈹€ opencv
           鈹溾攢鈹€ opencv_java490.dll   # Windows
           鈹斺攢鈹€ libopencv_java490.so # Linux
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
- Face feature service: `mvn -pl vision-mind-ffe-app spring-boot:run`
- Person re-identification: `mvn -pl vision-mind-reid-app spring-boot:run`
- Text-based image retrieval: `mvn -pl vision-mind-tbir-app spring-boot:run`
- LLM chat facade: `mvn -pl vision-mind-llm-core spring-boot:run`

Each service uses `/api` as the context root. Default ports can be overridden in the respective `application.properties`.

### Vector storage toggle

- `vision-mind-ffe-app`, `vision-mind-reid-app`, and `vision-mind-tbir-app` expose a `vector.persistence.enabled` flag.
- Leave it at `true` (default) to persist vectors with Lucene, or flip to `false` to run entirely in-memory via the embedded chroma store.
- Memory mode is ideal for quick validation but discards vectors on restart.

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

### vision-mind-ffe-app (Face Feature Extraction)

| Method | Path | Description | Request Body | Response |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/face/computeFaceVector` | Detect faces and return embeddings without persisting. | `InputWithUrl` (`imgUrl`, `groupId?`, `faceScoreThreshold?`) | `HttpResult<FaceImage>` |
| POST | `/api/v1/face/saveFaceVector` | Persist an externally computed face vector. | `Input4Save` (`imgUrl`, `groupId`, `id`, `embeds`) | `HttpResult<Void>` |
| POST | `/api/v1/face/computeAndSaveFaceVector` | Detect faces, store high-quality embeddings, and return inserted items. | `InputWithUrl` | `HttpResult<List<FaceInfo4Add>>` |
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

Contributions and issue reports are welcome.


