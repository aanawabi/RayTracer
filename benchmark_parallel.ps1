# benchmark_parallel.ps1
# Run from project root: D:\PDC Semester Project\ParallelRayTracer
# Usage:
#   .\benchmark_parallel.ps1              (compiles then benchmarks)
#   .\benchmark_parallel.ps1 -SkipCompile (skip compile, just benchmark)

param([switch]$SkipCompile)

$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
$cp          = Join-Path $projectRoot "out"
$port        = 5001
$maxDepth    = 5

function Compile-Project {
    Write-Host ""
    Write-Host "[Compiling...]" -ForegroundColor Yellow
    $sources = Get-ChildItem -Path (Join-Path $projectRoot "src") -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName
    & javac -d "$cp" $sources
    if ($LASTEXITCODE -ne 0) { throw "Compilation failed" }
    Write-Host "[Compile OK]" -ForegroundColor Green
}

function Run-Parallel {
    param([int]$Workers, [int]$Threads, [int]$Width, [int]$Height)

    $label = "$Workers workers x $Threads threads @ ${Width}x${Height}"
    Write-Host ""
    Write-Host "  >> $label" -ForegroundColor Cyan

    $logDir = Join-Path $projectRoot "results\logs"
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"

    # Quote $cp so spaces in the project path don't break the classpath arg
    $cpQ = "`"$cp`""

    # Build argument arrays as separate variables (avoids multi-line backtick issues)
    $masterArgs  = @("-cp", $cpQ, "master.MasterNode", $Workers, $Width, $Height, $maxDepth, $Threads)
    $masterLog   = Join-Path $logDir "master_${stamp}_${Workers}w${Threads}t_${Width}x${Height}.log"
    $masterProc  = Start-Process java -ArgumentList $masterArgs -WorkingDirectory $projectRoot -RedirectStandardOutput $masterLog -PassThru -NoNewWindow

    Start-Sleep -Seconds 2

    $workerProcs = @()
    for ($i = 0; $i -lt $Workers; $i++) {
        $workerArgs = @("-cp", $cpQ, "worker.WorkerNode", "localhost", $port)
        $wLog       = Join-Path $logDir "worker${i}_${stamp}_${Workers}w${Threads}t_${Width}x${Height}.log"
        $w          = Start-Process java -ArgumentList $workerArgs -WorkingDirectory $projectRoot -RedirectStandardOutput $wLog -PassThru -NoNewWindow
        $workerProcs += $w
        Start-Sleep -Milliseconds 300
    }

    $finished = $masterProc.WaitForExit(300000)
    if (-not $finished) {
        Write-Host "    [TIMEOUT - killing processes]" -ForegroundColor Red
        $masterProc | Stop-Process -Force -ErrorAction SilentlyContinue
        $workerProcs | Stop-Process -Force -ErrorAction SilentlyContinue
    } else {
        Write-Host "    [Done - exit $($masterProc.ExitCode)]" -ForegroundColor Green
        $workerProcs | ForEach-Object { $_.WaitForExit(30000) }
    }

    Start-Sleep -Seconds 4
}

# ---- main ------------------------------------------------------------------

if (-not $SkipCompile) { Compile-Project }

New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot "results") | Out-Null

Write-Host ""
Write-Host "==============================" -ForegroundColor Magenta
Write-Host " PARALLEL BENCHMARK SUITE"     -ForegroundColor Magenta
Write-Host "==============================" -ForegroundColor Magenta
Write-Host "Results -> results\parallel_benchmark.csv"
Write-Host "Logs    -> results\logs\"

# Section 1: Vary threads (fixed 3 workers, 1920x1080)
Write-Host ""
Write-Host "--- Section 1: Vary threads (3 workers, 1920x1080) ---" -ForegroundColor Yellow
Run-Parallel -Workers 3 -Threads 1 -Width 1920 -Height 1080
Run-Parallel -Workers 3 -Threads 2 -Width 1920 -Height 1080
Run-Parallel -Workers 3 -Threads 4 -Width 1920 -Height 1080
Run-Parallel -Workers 3 -Threads 8 -Width 1920 -Height 1080

# Section 2: Vary workers (fixed 8 threads, 1920x1080)
Write-Host ""
Write-Host "--- Section 2: Vary workers (8 threads, 1920x1080) ---" -ForegroundColor Yellow
Run-Parallel -Workers 1 -Threads 8 -Width 1920 -Height 1080
Run-Parallel -Workers 2 -Threads 8 -Width 1920 -Height 1080
# 3 workers x 8 threads already done in Section 1

# Section 3: Strong scaling (fixed 3 workers x 8 threads, vary size)
Write-Host ""
Write-Host "--- Section 3: Strong scaling (3 workers, 8 threads) ---" -ForegroundColor Yellow
Run-Parallel -Workers 3 -Threads 8 -Width 480  -Height 270
Run-Parallel -Workers 3 -Threads 8 -Width 960  -Height 540
# 1920x1080 already done in Section 1

# Section 4: Weak scaling (constant pixels per worker, 8 threads)
# 1w @ 480x270 = 129600 px/worker
# 2w @ 960x270 = 129600 px/worker
# 4w @ 1920x270 = 129600 px/worker
Write-Host ""
Write-Host "--- Section 4: Weak scaling (constant px/worker, 8 threads) ---" -ForegroundColor Yellow
Run-Parallel -Workers 1 -Threads 8 -Width 480  -Height 270
Run-Parallel -Workers 2 -Threads 8 -Width 960  -Height 270
Run-Parallel -Workers 4 -Threads 8 -Width 1920 -Height 270

Write-Host ""
Write-Host "==============================" -ForegroundColor Magenta
Write-Host " ALL BENCHMARKS COMPLETE"      -ForegroundColor Magenta
Write-Host "==============================" -ForegroundColor Magenta
Write-Host "CSV: results\parallel_benchmark.csv"
Write-Host "Next: python analyze_results.py"
