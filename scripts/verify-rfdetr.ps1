param([string]$ProjectRoot = ".")

$ErrorActionPreference = "Stop"
$root = (Resolve-Path -LiteralPath $ProjectRoot).Path
$model = Join-Path $root "resource\rfdetr\model\rfdetr-small.onnx"
$metadata = Join-Path $root "resource\rfdetr\model\rfdetr-small.metadata.json"
if (-not (Test-Path -LiteralPath $model) -or -not (Test-Path -LiteralPath $metadata)) {
    throw "RF-DETR model resources are missing. Run scripts\init-rfdetr.ps1 first."
}
$metadataValue = Get-Content -LiteralPath $metadata -Raw | ConvertFrom-Json
$modelHash = (Get-FileHash -LiteralPath $model -Algorithm SHA256).Hash.ToLowerInvariant()
if ($modelHash -ne $metadataValue.modelSha256.ToLowerInvariant()) {
    throw "RF-DETR ONNX SHA-256 does not match metadata"
}
$maven = Get-Command mvn -ErrorAction SilentlyContinue
if ($null -eq $maven) {
    throw "mvn is unavailable. Add Maven to PATH before verification."
}
$env:VISION_MIND_PATH = Join-Path $root "resource"
& $maven.Source -pl vision-mind-rfdetr-app -am test "-Dvision-mind.skip-opencv=true"
Write-Host "RF-DETR module tests and model integrity verification passed."
