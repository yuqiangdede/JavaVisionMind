param(
    [string]$ProjectRoot = "."
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path $ProjectRoot
Write-Host "[verify-env] root: $root"

function Test-Command {
    param([string]$Name)
    try {
        $null = Get-Command $Name -ErrorAction Stop
        return $true
    } catch {
        return $false
    }
}

if (-not (Test-Command "java")) {
    throw "java 未安装或不可用"
}
if (-not (Test-Command "mvn")) {
    Write-Warning "mvn 不可用，当前机器可能无法本地构建；建议依赖 GitHub Actions 验证。"
}

$resourceRoot = Join-Path $root "resource"
if (Test-Path $resourceRoot) {
    Write-Host "[verify-env] using local resource root: $resourceRoot"
} elseif ($env:VISION_MIND_PATH) {
    Write-Host "[verify-env] using env resource root: $env:VISION_MIND_PATH"
    $resourceRoot = $env:VISION_MIND_PATH
} else {
    throw "未找到 resource 目录，且未设置 VISION_MIND_PATH"
}

$manifest = Join-Path $resourceRoot "manifest.json"
if (-not (Test-Path $manifest)) {
    throw "缺少 manifest.json: $manifest"
}

Write-Host "[verify-env] java version:"
java -version

if (Test-Command "mvn") {
    Write-Host "[verify-env] maven version:"
    mvn -version
}

Write-Host "[verify-env] 环境检查通过"
