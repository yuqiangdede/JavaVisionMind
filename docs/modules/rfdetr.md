# RF-DETR Small Module

- App: `vision-mind-rfdetr-app`
- Core: `vision-mind-rfdetr-core`
- Runtime: CPU ONNX Runtime only
- Address: `http://localhost:17011/vision-mind-rfdetr`

## Resources

Run `scripts\init-rfdetr.ps1` before starting the service. It downloads and validates the Small checkpoint, caches large installers under `D:\cache`, exports the ONNX model to `resource\rfdetr\model`, and writes a SHA-256-bound metadata sidecar. The model is intentionally ignored by Git; a copied workspace contains the generated resources, while a fresh clone rebuilds them with the script.

The exported contract is fixed to `input [1,3,512,512]`, `dets [1,300,4]`, and `labels [1,300,91]`. Detection uses direct RGB resize, ImageNet normalization, sigmoid scores, global query/class top-300, sparse COCO IDs, and no NMS.

## API

| Function | Route |
| --- | --- |
| Detect JSON | `POST /api/v1/vision/detect` or `/api/v1/img/detect` |
| Detect preview | `POST /api/v1/vision/detect/preview` or `/api/v1/img/detectI` |
| Upload | `POST /api/v1/vision/detect/upload` |
| Video sampling | `POST /api/v1/vision/video/detect` or `/api/v1/video/detect` |

`types` uses RF-DETR COCO category IDs, not YOLO's contiguous category list. For example: `person=1`, `car=3`, and `toothbrush=90`. Video input supports RTSP, HTTP, and local video paths but requires the OpenCV native library from the project resource directory.
