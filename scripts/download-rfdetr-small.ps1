param(
    [string]$CacheRoot = "D:\cache",
    [int]$Parallelism = 8,
    [int64]$ChunkSize = 4194304
)

$ErrorActionPreference = "Stop"
$url = "https://rfdetr.storage.googleapis.com/small_coco/checkpoint_best_regular.pth"
$cacheDir = Join-Path $CacheRoot "models\rfdetr"
$target = Join-Path $cacheDir "rf-detr-small.pth"
$partDir = Join-Path $cacheDir "rf-detr-small.parts"
$contentLength = 386045550L

New-Item -ItemType Directory -Force -Path $cacheDir, $partDir | Out-Null
$ranges = 0..([int][math]::Ceiling($contentLength / $ChunkSize) - 1) | ForEach-Object {
    $start = [int64]($_ * $ChunkSize)
    $end = [int64][math]::Min($contentLength - 1, $start + $ChunkSize - 1)
    [pscustomobject]@{
        Index = $_
        Start = $start
        End = $end
        Path = Join-Path $partDir ("part-{0:D4}" -f $_)
    }
}

$batchNumber = 0
for ($offset = 0; $offset -lt $ranges.Count; $offset += $Parallelism) {
    $batch = @($ranges | Select-Object -Skip $offset -First $Parallelism)
    $processes = foreach ($range in $batch) {
        $arguments = @(
            "--fail", "--location", "--retry", "10", "--retry-delay", "2",
            "--range", "$($range.Start)-$($range.End)",
            "--output", $range.Path, $url
        )
        Start-Process -FilePath "curl.exe" -ArgumentList $arguments -WindowStyle Hidden -PassThru
    }
    $processes | Wait-Process
    foreach ($process in $processes) {
        if ($process.ExitCode -ne 0) {
            throw "RF-DETR Small checkpoint range download failed, exit code=$($process.ExitCode)"
        }
    }
    $batchNumber++
    Write-Host "Downloaded RF-DETR Small checkpoint parts batch $batchNumber/$([math]::Ceiling($ranges.Count / $Parallelism))"
}

$stream = [System.IO.File]::Open($target, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write,
    [System.IO.FileShare]::None)
try {
    foreach ($range in $ranges) {
        $bytes = [System.IO.File]::ReadAllBytes($range.Path)
        $expected = $range.End - $range.Start + 1
        if ($bytes.LongLength -ne $expected) {
            throw "RF-DETR Small checkpoint range length mismatch: part=$($range.Index), expected=$expected, actual=$($bytes.LongLength)"
        }
        $stream.Write($bytes, 0, $bytes.Length)
    }
}
finally {
    $stream.Dispose()
}

$file = Get-Item -LiteralPath $target
$hash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
[pscustomobject]@{
    Path = $file.FullName
    Length = $file.Length
    SHA256 = $hash
}
