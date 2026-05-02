# benchmark_sequential.ps1
# Run from project root to (re)collect clean sequential baseline data.
# Usage: .\benchmark_sequential.ps1
#        .\benchmark_sequential.ps1 -SkipCompile

param([switch]$SkipCompile)

$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
$cp          = Join-Path $projectRoot "out"

function Compile-Project {
    Write-Host "`n[Compiling...]" -ForegroundColor Yellow
    $sources = Get-ChildItem -Path (Join-Path $projectRoot "src") `
                             -Recurse -Filter "*.java" |
               Select-Object -ExpandProperty FullName
    & javac -d $cp $sources
    if ($LASTEXITCODE -ne 0) { throw "Compilation failed" }
    Write-Host "[Compile OK]" -ForegroundColor Green
}

if (-not $SkipCompile) { Compile-Project }

New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot "results") | Out-Null

# Reset CSV so we start with clean, consistently-formatted data
$csvPath = Join-Path $projectRoot "results\sequential_benchmark.csv"
Set-Content -Path $csvPath -Value "width,height,maxDepth,mode,time_ms" -Encoding utf8
Write-Host "`nReset $csvPath" -ForegroundColor Yellow

Write-Host "`n==============================" -ForegroundColor Magenta
Write-Host " SEQUENTIAL BENCHMARK SUITE" -ForegroundColor Magenta
Write-Host "==============================`n" -ForegroundColor Magenta

$sizes = @(
    @(480,  270),
    @(960,  540),
    @(1920, 1080)
)

foreach ($sz in $sizes) {
    $w = $sz[0]; $h = $sz[1]

    Write-Host "--- ${w}x${h} (seq) ---" -ForegroundColor Cyan
    & java -cp $cp core.SequentialRunner $w $h 5 seq

    Write-Host "--- ${w}x${h} (bvh) ---" -ForegroundColor Cyan
    & java -cp $cp core.SequentialRunner $w $h 5 bvh
}

Write-Host "`n==============================" -ForegroundColor Magenta
Write-Host " SEQUENTIAL BENCHMARKS DONE" -ForegroundColor Magenta
Write-Host "==============================" -ForegroundColor Magenta
Write-Host "CSV: results\sequential_benchmark.csv"
