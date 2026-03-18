param(
    [string]$ProjectRoot = "."
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path $ProjectRoot
Write-Host "[bootstrap] root: $root"

& (Join-Path $root "scripts\\verify-env.ps1") -ProjectRoot $root

Write-Host "[bootstrap] 建议执行："
Write-Host "  mvn -B -DskipTests clean package"
Write-Host "  mvn -pl vision-mind-yolo-app spring-boot:run"
Write-Host "  mvn -pl vision-mind-asr-app spring-boot:run"
