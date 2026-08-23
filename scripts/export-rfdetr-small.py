"""Export the pinned RF-DETR Small checkpoint to the Java ONNX Runtime contract."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path


COCO_CLASS_NAMES = {
    1: "person", 2: "bicycle", 3: "car", 4: "motorcycle", 5: "airplane", 6: "bus", 7: "train", 8: "truck",
    9: "boat", 10: "traffic light", 11: "fire hydrant", 13: "stop sign", 14: "parking meter", 15: "bench",
    16: "bird", 17: "cat", 18: "dog", 19: "horse", 20: "sheep", 21: "cow", 22: "elephant", 23: "bear",
    24: "zebra", 25: "giraffe", 27: "backpack", 28: "umbrella", 31: "handbag", 32: "tie", 33: "suitcase",
    34: "frisbee", 35: "skis", 36: "snowboard", 37: "sports ball", 38: "kite", 39: "baseball bat",
    40: "baseball glove", 41: "skateboard", 42: "surfboard", 43: "tennis racket", 44: "bottle", 46: "wine glass",
    47: "cup", 48: "fork", 49: "knife", 50: "spoon", 51: "bowl", 52: "banana", 53: "apple", 54: "sandwich",
    55: "orange", 56: "broccoli", 57: "carrot", 58: "hot dog", 59: "pizza", 60: "donut", 61: "cake",
    62: "chair", 63: "couch", 64: "potted plant", 65: "bed", 67: "dining table", 70: "toilet", 72: "tv",
    73: "laptop", 74: "mouse", 75: "remote", 76: "keyboard", 77: "cell phone", 78: "microwave", 79: "oven",
    80: "toaster", 81: "sink", 82: "refrigerator", 84: "book", 85: "clock", 86: "vase", 87: "scissors",
    88: "teddy bear", 89: "hair drier", 90: "toothbrush",
}

EXPECTED = {
    "input": [1, 3, 512, 512],
    "dets": [1, 300, 4],
    "labels": [1, 300, 91],
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def tensor_metadata(model_path: Path) -> dict:
    import onnx

    model = onnx.load(model_path)
    tensors = {}
    for item in [*model.graph.input, *model.graph.output]:
        dimensions = [dimension.dim_value for dimension in item.type.tensor_type.shape.dim]
        tensors[item.name] = dimensions
    for name, expected_shape in EXPECTED.items():
        actual_shape = tensors.get(name)
        if actual_shape != expected_shape:
            raise RuntimeError(f"RF-DETR ONNX tensor contract mismatch for {name}: expected={expected_shape}, actual={actual_shape}")
    return {
        "modelSha256": sha256(model_path),
        "source": "https://github.com/roboflow/rf-detr/releases/tag/1.9.2",
        "license": "Apache-2.0",
        "input": {"name": "input", "type": "float32", "shape": EXPECTED["input"]},
        "outputs": {
            "dets": {"name": "dets", "type": "float32", "shape": EXPECTED["dets"]},
            "labels": {"name": "labels", "type": "float32", "shape": EXPECTED["labels"]},
        },
        "classNames": COCO_CLASS_NAMES,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--resource-dir", required=True, type=Path)
    args = parser.parse_args()
    if not args.checkpoint.is_file():
        raise FileNotFoundError(f"RF-DETR Small checkpoint is missing: {args.checkpoint}")

    from rfdetr import RFDETRSmall

    args.output_dir.mkdir(parents=True, exist_ok=True)
    model = RFDETRSmall(pretrain_weights=str(args.checkpoint))
    exported = Path(model.export(output_dir=str(args.output_dir), shape=(512, 512), batch_size=1,
                                dynamic_batch=False, opset_version=17, format="onnx"))
    if not exported.is_file():
        raise RuntimeError(f"RF-DETR export did not create an ONNX file: {exported}")

    args.resource_dir.mkdir(parents=True, exist_ok=True)
    target_model = args.resource_dir / "rfdetr-small.onnx"
    target_metadata = args.resource_dir / "rfdetr-small.metadata.json"
    shutil.copy2(exported, target_model)
    target_metadata.write_text(json.dumps(tensor_metadata(target_model), indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"model": str(target_model), "sha256": sha256(target_model), "metadata": str(target_metadata)}))


if __name__ == "__main__":
    main()
