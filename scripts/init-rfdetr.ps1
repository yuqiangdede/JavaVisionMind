param(
    [string]$ProjectRoot = ".",
    [string]$CheckpointSource = "",
    [string]$CacheRoot = "D:\cache"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path -LiteralPath $ProjectRoot).Path
$runtimeRoot = Join-Path $root ".runtime\rfdetr-export"
$venvRoot = Join-Path $runtimeRoot ".venv"
$cacheModelDir = Join-Path $CacheRoot "models\rfdetr"
$cacheTorchDir = Join-Path $CacheRoot "pytorch"
$checkpoint = Join-Path $cacheModelDir "rf-detr-small.pth"
$expectedCheckpointHash = "D81979A9213A2109345158CE9232668DF4C1AE52E9B8DB3F2EC0A8CBAD959B33"
$resourceDir = Join-Path $root "resource\rfdetr\model"
$exportDir = Join-Path $runtimeRoot "onnx-output"
$python = Join-Path $venvRoot "Scripts\python.exe"

New-Item -ItemType Directory -Force -Path $runtimeRoot, $cacheModelDir, $cacheTorchDir, $resourceDir, $exportDir | Out-Null

if (-not (Test-Path -LiteralPath $checkpoint)) {
    if ($CheckpointSource) {
        if (-not (Test-Path -LiteralPath $CheckpointSource)) {
            throw "CheckpointSource does not exist: $CheckpointSource"
        }
        Copy-Item -LiteralPath $CheckpointSource -Destination $checkpoint
    } else {
        & (Join-Path $root "scripts\download-rfdetr-small.ps1") -CacheRoot $CacheRoot
    }
}

$actualCheckpointHash = (Get-FileHash -LiteralPath $checkpoint -Algorithm SHA256).Hash
if ($expectedCheckpointHash -and $actualCheckpointHash -ne $expectedCheckpointHash) {
    throw "RF-DETR Small checkpoint SHA-256 mismatch: expected=$expectedCheckpointHash actual=$actualCheckpointHash"
}

if (-not (Test-Path -LiteralPath $python)) {
    & py -3.12 -m venv $venvRoot
}

$env:PIP_CACHE_DIR = Join-Path $runtimeRoot "pip-cache"
$env:RF_HOME = Join-Path $runtimeRoot "rf-home"
& $python -m pip install --upgrade pip
# PyTorch Windows CPU 索引当前发布的 wheel 元数据版本没有 +cpu 后缀。
# 索引本身限定为 CPU，不能把缓存目录或 GPU 依赖写入运行时配置。
& $python -m pip download --dest $cacheTorchDir --index-url https://download.pytorch.org/whl/cpu torch==2.13.0 torchvision==0.28.0
& $python -m pip install --no-index --find-links $cacheTorchDir torch==2.13.0 torchvision==0.28.0
& $python -m pip install "rfdetr[onnx]==1.9.2"
& $python (Join-Path $root "scripts\export-rfdetr-small.py") --checkpoint $checkpoint --output-dir $exportDir --resource-dir $resourceDir
& $python -m pip freeze | Set-Content -LiteralPath (Join-Path $runtimeRoot "requirements.lock") -Encoding utf8

Write-Host "RF-DETR Small ONNX generated under $resourceDir; checkpoint SHA-256=$actualCheckpointHash"
