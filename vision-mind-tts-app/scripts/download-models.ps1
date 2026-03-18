param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$ProgressPreference = "SilentlyContinue"

$moduleRoot = Split-Path -Parent $PSScriptRoot
$projectRoot = Split-Path -Parent $moduleRoot
$modelRoot = Join-Path $projectRoot "resource\tts\model"
$modelId = "sherpa-onnx-vits-zh-ll"
$archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-vits-zh-ll.tar.bz2"
$archivePath = Join-Path $modelRoot ($modelId + ".tar.bz2")
$targetDir = Join-Path $modelRoot $modelId
$legacyModelDir = Join-Path $modelRoot "tts-model"
$legacyPaths = @(
    (Join-Path $modelRoot "vits-melo-tts-zh_en"),
    (Join-Path $modelRoot "vits-melo-tts-zh_en.tar.bz2"),
    (Join-Path $modelRoot "vits-zh-hf-fanchen-wnj"),
    (Join-Path $modelRoot "vits-zh-hf-fanchen-wnj.tar.bz2"),
    (Join-Path $modelRoot "vits-icefall-zh-aishell3.tar.bz2"),
    $legacyModelDir
)
$requiredFiles = @(
    "model.onnx",
    "lexicon.txt",
    "tokens.txt",
    "phone.fst",
    "date.fst",
    "number.fst",
    "README.md",
    "G_multisperaker_latest.json",
    "dict\README.md"
)
$runtimeDownloads = @(
    @{
        Path = "resource/lib/sherpa-onnx/sherpa-onnx-v1.12.29.jar"
        Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.29/sherpa-onnx-v1.12.29.jar"
    },
    @{
        Path = "resource/lib/sherpa-onnx/sherpa-onnx-native-lib-win-x64-v1.12.29.jar"
        Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.29/sherpa-onnx-native-lib-win-x64-v1.12.29.jar"
    },
    @{
        Path = "resource/lib/sherpa-onnx/sherpa-onnx-native-lib-linux-x64-v1.12.29.jar"
        Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.29/sherpa-onnx-native-lib-linux-x64-v1.12.29.jar"
    },
    @{
        Path = "resource/lib/sherpa-onnx/sherpa-onnx-native-lib-linux-aarch64-v1.12.29.jar"
        Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.29/sherpa-onnx-native-lib-linux-aarch64-v1.12.29.jar"
    }
)

function Save-RemoteFile {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][string]$Destination,
        [switch]$ForceDownload
    )

    if ((Test-Path $Destination) -and -not $ForceDownload) {
        Write-Host "[skip] $Destination"
        return
    }

    $directory = Split-Path -Parent $Destination
    if ($directory) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }

    Write-Host "[download] $Url"
    Invoke-WebRequest -Uri $Url -OutFile $Destination
    Write-Host "[saved] $Destination"
}

function Test-ModelReady {
    param(
        [Parameter(Mandatory = $true)][string]$Dir,
        [Parameter(Mandatory = $true)][array]$ExpectedFiles
    )

    foreach ($name in $ExpectedFiles) {
        if (-not (Test-Path (Join-Path $Dir $name))) {
            return $false
        }
    }

    $onnxFiles = Get-ChildItem -Path $Dir -Filter *.onnx -File -ErrorAction SilentlyContinue
    if (-not $onnxFiles) {
        return $false
    }

    foreach ($onnxFile in $onnxFiles) {
        if ($onnxFile.Length -gt 1024) {
            return $true
        }
    }

    return $false
}

function Expand-ModelArchive {
    param(
        [Parameter(Mandatory = $true)][string]$ArchiveFile,
        [Parameter(Mandatory = $true)][string]$DestinationRoot,
        [Parameter(Mandatory = $true)][string]$ExpectedDir,
        [Parameter(Mandatory = $true)][array]$ExpectedFiles,
        [switch]$ForceExtract
    )

    if (Test-Path $ExpectedDir) {
        if ($ForceExtract) {
            Remove-Item -Recurse -Force $ExpectedDir
        } elseif (Test-ModelReady -Dir $ExpectedDir -ExpectedFiles $ExpectedFiles) {
            Write-Host "[skip] $ExpectedDir"
            return
        } else {
            Write-Host "[repair] $ExpectedDir"
            Remove-Item -Recurse -Force $ExpectedDir
        }
    }

    if (-not (Get-Command tar -ErrorAction SilentlyContinue)) {
        throw "tar command is required to extract archived models."
    }

    New-Item -ItemType Directory -Path $DestinationRoot -Force | Out-Null
    tar -xjf $ArchiveFile -C $DestinationRoot

    if (-not (Test-Path $ExpectedDir)) {
        throw "Model archive extraction failed.`nMissing directory: $ExpectedDir"
    }
    if (-not (Test-ModelReady -Dir $ExpectedDir -ExpectedFiles $ExpectedFiles)) {
        throw "Model archive extraction failed.`nIncomplete directory: $ExpectedDir"
    }
}

foreach ($legacyPath in $legacyPaths) {
    if (Test-Path $legacyPath) {
        Remove-Item -Recurse -Force $legacyPath
        Write-Host "[cleanup] $legacyPath"
    }
}

if (Test-Path $targetDir) {
    if ($Force) {
        Remove-Item -Recurse -Force $targetDir
        Write-Host "[repair] $targetDir"
    } elseif (Test-ModelReady -Dir $targetDir -ExpectedFiles $requiredFiles) {
        Write-Host "[skip] $targetDir"
    } else {
        Write-Host "[repair] $targetDir"
        Remove-Item -Recurse -Force $targetDir
    }
}

if (-not (Test-Path $targetDir)) {
    if (-not (Test-Path $archivePath) -or $Force) {
        Save-RemoteFile -Url $archiveUrl -Destination $archivePath -ForceDownload:$Force
    } else {
        Write-Host "[skip] $archivePath"
    }

    Expand-ModelArchive -ArchiveFile $archivePath -DestinationRoot $modelRoot -ExpectedDir $targetDir -ExpectedFiles $requiredFiles -ForceExtract:$Force
}

foreach ($item in $runtimeDownloads) {
    $destination = Join-Path $projectRoot $item.Path
    Save-RemoteFile -Url $item.Url -Destination $destination -ForceDownload:$Force
}

Write-Host ""
Write-Host "Resources downloaded."
Write-Host "Model:"
Write-Host " - sherpa-onnx-vits-zh-ll"
Write-Host "Start with: mvn spring-boot:run"
