param([string]$ProjectRoot = ".")

$ErrorActionPreference = "Stop"
$root = (Resolve-Path -LiteralPath $ProjectRoot).Path
$model = Join-Path $root "resource\rfdetr\model\rfdetr-small.onnx"
$metadata = Join-Path $root "resource\rfdetr\model\rfdetr-small.metadata.json"
if (-not (Test-Path -LiteralPath $model) -or -not (Test-Path -LiteralPath $metadata)) {
    throw "RF-DETR model resources are missing. Run scripts\init-rfdetr.ps1 first."
}
$maven = Get-Command mvn -ErrorAction SilentlyContinue
if ($null -eq $maven) {
    throw "mvn is unavailable. Add Maven to PATH before starting RF-DETR."
}
$env:VISION_MIND_PATH = Join-Path $root "resource"
& $maven.Source -pl vision-mind-rfdetr-app spring-boot:run
