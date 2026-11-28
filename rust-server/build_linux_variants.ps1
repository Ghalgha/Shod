param(
    [Parameter(Mandatory = $false)][string]$BuildScript,
    [Parameter(Mandatory = $false)][string]$ServerDir,
    [Parameter(Mandatory = $false)][string]$ProjectRoot,
    [Parameter(Mandatory = $false)][string]$AssetsDir,
    [Parameter(Mandatory = $false)][string]$CargoFeatures = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

if (-not $ServerDir) {
    if ($BuildScript) {
        $ServerDir = (Get-Item -LiteralPath $BuildScript).DirectoryName
    } else {
        $ServerDir = $PSScriptRoot
    }
}
$ServerDir = (Resolve-Path -LiteralPath $ServerDir).Path

if (-not $ProjectRoot) {
    $ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $ServerDir "..")).Path
} else {
    $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

if (-not $AssetsDir) {
    $AssetsDir = Join-Path $ProjectRoot "app\src\main\assets"
}
New-Item -ItemType Directory -Force -Path $AssetsDir | Out-Null

$shimDir = Join-Path ([System.IO.Path]::GetTempPath()) "shod-py3-shim"
New-Item -ItemType Directory -Force -Path $shimDir | Out-Null
Set-Content -Path (Join-Path $shimDir "python3.cmd") -Value "@exit /b 1"
$env:PATH = "$shimDir;$env:PATH"

$zig = Get-Command zig.exe -ErrorAction SilentlyContinue
if (-not $zig -and $env:LOCALAPPDATA) {
    $wingetZig = Get-ChildItem -Path "$env:LOCALAPPDATA\Microsoft\WinGet\Packages" -Filter "zig.exe" -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($wingetZig) {
        $env:PATH = "$($wingetZig.DirectoryName);$env:PATH"
    }
}

$cargo = (Get-Command cargo.exe -ErrorAction Stop).Source
$logsDir = Join-Path $ProjectRoot "build\shod-uring-linux-logs"
New-Item -ItemType Directory -Force -Path $logsDir | Out-Null

$variants = @(
    @{ Name = 'amd64'; Target = 'x86_64-unknown-linux-musl'; Asset = 'shod-linux-amd64' },
    @{ Name = 'arm64'; Target = 'aarch64-unknown-linux-musl'; Asset = 'shod-linux-arm64' },
    @{ Name = 'armv7'; Target = 'armv7-unknown-linux-musleabihf'; Asset = 'shod-linux-armv7' }
)

# Check all required Rust targets before starting any build processes.
$rustup = Get-Command rustup.exe -ErrorAction SilentlyContinue
if (-not $rustup) {
    Write-Host "Не найден rustup.exe. Установи Rust через rustup и повтори запуск." -ForegroundColor Red
    exit 1
}

Push-Location $ServerDir
try {
    $rustupOutput = @(& $rustup.Source target list --installed 2>&1)
    $rustupExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($rustupExitCode -ne 0) {
    Write-Host "Не удалось проверить установленные Rust-компоненты:" -ForegroundColor Red
    $rustupOutput | ForEach-Object { Write-Host "  $_" }
    exit 1
}

$installedTargets = @($rustupOutput | ForEach-Object { "$($_)".Trim() })
$missingTargets = @($variants | Where-Object { $_.Target -notin $installedTargets })
if ($missingTargets.Count -gt 0) {
    Write-Host "Не установлены компоненты Rust для следующих архитектур:" -ForegroundColor Red
    foreach ($item in $missingTargets) {
        Write-Host "  $($item.Name): $($item.Target)" -ForegroundColor Yellow
    }
    Write-Host "Установи недостающие компоненты командами:" -ForegroundColor Yellow
    foreach ($item in $missingTargets) {
        Write-Host "  rustup target add $($item.Target) --toolchain 1.97.1"
    }
    exit 1
}

Write-Host "========================================================================" -ForegroundColor Cyan
Write-Host "  Building 3 Linux Server Variants in Parallel (Single Window)" -ForegroundColor Cyan
Write-Host "  Targets: amd64 (x86_64), arm64 (aarch64), armv7 (armv7eabihf)" -ForegroundColor Cyan
Write-Host "========================================================================" -ForegroundColor Cyan

$tasks = @()

try {
foreach ($variant in $variants) {
    $targetDir = Join-Path $ProjectRoot "build\shod-uring-linux-$($variant.Asset)"
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
    $logFile = Join-Path $logsDir "$($variant.Name).log"
    if (Test-Path -LiteralPath $logFile) {
        Remove-Item -LiteralPath $logFile -Force -ErrorAction SilentlyContinue
    }

    $extraArgs = if ($CargoFeatures) { " $CargoFeatures" } else { "" }
    $cmdArgs = "/d /c (`"$cargo`" zigbuild --locked --release --target $($variant.Target)$extraArgs) > `"$logFile`" 2>&1"

    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = "cmd.exe"
    $info.Arguments = $cmdArgs
    $info.WorkingDirectory = $ServerDir
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.EnvironmentVariables["CARGO_TARGET_DIR"] = $targetDir
    $info.EnvironmentVariables["PATH"] = $env:PATH

    $proc = [System.Diagnostics.Process]::Start($info)

    $tasks += [pscustomobject]@{
        Name = $variant.Name
        Target = $variant.Target
        Asset = $variant.Asset
        TargetDir = $targetDir
        LogFile = $logFile
        Process = $proc
        Stream = $null
        Reader = $null
        CurrentCrate = "initializing"
        ExitCode = $null
        Duration = $null
        WarningCount = 0
        ErrorCount = 0
    }
}

$isInteractive = $false
try {
    if ([Environment]::UserInteractive -and -not [Console]::IsOutputRedirected) {
        $null = [Console]::WindowWidth
        $isInteractive = $true
    }
} catch {
    $isInteractive = $false
}

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$lastDisplayUpdate = [DateTime]::MinValue
$lastNonInteractivePrint = [DateTime]::MinValue
$lastPrintedStatus = ""
$maxStatusLen = 0

while ($true) {
        $anyRunning = $false

        foreach ($item in $tasks) {
            if (-not $item.Process.HasExited) {
                $anyRunning = $true
            } elseif ($null -eq $item.ExitCode) {
                $item.Process.Refresh()
                $item.ExitCode = $item.Process.ExitCode
                $item.Duration = $sw.Elapsed
            }

            if ($null -eq $item.Reader -and (Test-Path -LiteralPath $item.LogFile)) {
                try {
                    $stream = [System.IO.File]::Open($item.LogFile, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
                    $item.Stream = $stream
                    $item.Reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8)
                } catch {
                }
            }

            if ($null -ne $item.Reader) {
                while ($true) {
                    $line = $item.Reader.ReadLine()
                    if ($null -eq $line) { break }
                    $trimmed = $line.Trim()
                    if ([string]::IsNullOrWhiteSpace($trimmed)) { continue }

                    if ($trimmed -match '^(?:Compiling|Checking|Building)\s+([^\s]+)') {
                        $item.CurrentCrate = $Matches[1]
                    } elseif ($trimmed -match '^Finished\s+') {
                        $item.CurrentCrate = "Finishing"
                    }

                    if ($trimmed -match '^(?:warning:\s*|CMake Warning|warning\[\w+\]:\s*|[a-zA-Z0-9_-]+@[\d\.]+: warning:)') {
                        $item.WarningCount++
                        if ($isInteractive) {
                            $pad = " " * [Math]::Min(120, [Math]::Max(80, $maxStatusLen))
                            Write-Host ("`r$pad`r") -NoNewline
                        }
                        Write-Host "  [!][$($item.Name)] $trimmed" -ForegroundColor Yellow
                    }
                    elseif ($trimmed -match '^(?:error:\s*|CMake Error|error\[\w+\]:\s*|error occurred in\s*)') {
                        $item.ErrorCount++
                        if ($isInteractive) {
                            $pad = " " * [Math]::Min(120, [Math]::Max(80, $maxStatusLen))
                            Write-Host ("`r$pad`r") -NoNewline
                        }
                        Write-Host "  [x][$($item.Name)] $trimmed" -ForegroundColor Red
                    }
                }
            }

            if ($item.Process.HasExited -and $null -ne $item.Stream) {
                $item.Reader.Dispose()
                $item.Stream.Dispose()
                $item.Reader = $null
                $item.Stream = $null
                $item.CurrentCrate = if ($item.ExitCode -eq 0) { "Done" } else { "Failed" }
            }
        }

        $now = [DateTime]::UtcNow
        if (($now - $lastDisplayUpdate).TotalMilliseconds -ge 300 -or -not $anyRunning) {
            $lastDisplayUpdate = $now
            $elapsed = $sw.Elapsed.ToString("mm\:ss")

            $parts = foreach ($item in $tasks) {
                if ($null -ne $item.ExitCode) {
                    if ($item.ExitCode -eq 0) {
                        "$($item.Name): [OK]"
                    } else {
                        "$($item.Name): [FAILED]"
                    }
                } else {
                    "$($item.Name): $($item.CurrentCrate)"
                }
            }
            $statusText = "[$elapsed] " + ($parts -join " | ")

            if ($isInteractive) {
                $maxLen = 80
                try { $maxLen = [Math]::Max(40, [Console]::WindowWidth - 2) } catch {}
                if ($statusText.Length -gt $maxLen) {
                    $statusText = $statusText.Substring(0, $maxLen - 3) + "..."
                }
                if ($statusText.Length -lt $maxStatusLen) {
                    $statusText = $statusText.PadRight($maxStatusLen)
                } else {
                    $maxStatusLen = $statusText.Length
                }
                Write-Host -NoNewline ("`r" + $statusText)
            } else {
                if ($statusText -ne $lastPrintedStatus -and (($now - $lastNonInteractivePrint).TotalSeconds -ge 4 -or -not $anyRunning)) {
                    $lastNonInteractivePrint = $now
                    $lastPrintedStatus = $statusText
                    Write-Host $statusText
                }
            }
        }

        if (-not $anyRunning) {
            break
        }
        Start-Sleep -Milliseconds 100
    }
} catch {
    # A launch or monitoring error must not leave earlier build processes running.
    foreach ($item in $tasks) {
        if ($null -ne $item.Process) {
            try {
                if (-not $item.Process.HasExited) {
                    & "$env:SystemRoot\System32\taskkill.exe" /PID $item.Process.Id /T /F 2>$null | Out-Null
                    if (-not $item.Process.HasExited) {
                        $item.Process.Kill()
                    }
                }
            } catch {
                try {
                    if (-not $item.Process.HasExited) { $item.Process.Kill() }
                } catch {}
            }
        }
    }
    foreach ($item in $tasks) {
        if ($null -ne $item.Process) {
            try { $item.Process.WaitForExit() } catch {}
        }
    }
    throw
} finally {
    foreach ($item in $tasks) {
        if ($null -ne $item.Stream) {
            try { $item.Reader.Dispose() } catch {}
            try { $item.Stream.Dispose() } catch {}
        }
    }
}

if ($isInteractive) {
    $pad = " " * [Math]::Min(120, [Math]::Max(80, $maxStatusLen))
    Write-Host ("`r$pad`r") -NoNewline
}

Write-Host "`n========================================================================" -ForegroundColor Cyan
Write-Host "  Linux Server Build Summary ($($sw.Elapsed.ToString('mm\:ss')) total):" -ForegroundColor Cyan
Write-Host "========================================================================" -ForegroundColor Cyan

$hasFailure = $false

foreach ($item in $tasks) {
    $dur = if ($null -ne $item.Duration) { $item.Duration.ToString("mm\:ss") } else { "N/A" }
    $warns = if ($item.WarningCount -gt 0) { " ($($item.WarningCount) warnings)" } else { "" }

    if ($item.ExitCode -eq 0) {
        $srcBinary = Join-Path $item.TargetDir "$($item.Target)\release\shod"
        if (-not (Test-Path -LiteralPath $srcBinary)) {
            $srcBinary = Join-Path $item.TargetDir "release\shod"
        }

        if ((Test-Path -LiteralPath $srcBinary) -and (Get-Item -LiteralPath $srcBinary).Length -gt 0) {
            $size = (Get-Item -LiteralPath $srcBinary).Length
            Write-Host "  [READY] $($item.Name) ($dur)$warns -> $($item.Asset) ($size bytes)" -ForegroundColor Green
        } else {
            Write-Host "  [FAIL] $($item.Name) binary missing or empty at $srcBinary" -ForegroundColor Red
            $hasFailure = $true
        }
    } else {
        Write-Host "  [FAIL] $($item.Name) (ExitCode $($item.ExitCode), $dur)" -ForegroundColor Red
        $hasFailure = $true
    }
}

if ($hasFailure) {
    Write-Host "`n========================================================================" -ForegroundColor Red
    Write-Host "  Build Failures - Log Tail (last 40 lines):" -ForegroundColor Red
    Write-Host "========================================================================" -ForegroundColor Red
    foreach ($item in $tasks) {
        if ($item.ExitCode -ne 0 -or -not (Test-Path -LiteralPath (Join-Path $AssetsDir $item.Asset))) {
            Write-Host "--- Log for $($item.Name) ($($item.LogFile)) ---" -ForegroundColor Red
            if (Test-Path -LiteralPath $item.LogFile) {
                Get-Content -LiteralPath $item.LogFile -Tail 40 | ForEach-Object {
                    Write-Host "  $_" -ForegroundColor DarkRed
                }
            }
        }
    }
    exit 1
}

# Stage and validate the complete set before touching the published assets.
$stagingDir = Join-Path $ProjectRoot "build\shod-linux-publish-$PID"
$backupDir = Join-Path $stagingDir "backup"
$publishedAssets = @()

try {
    New-Item -ItemType Directory -Force -Path $stagingDir | Out-Null
    New-Item -ItemType Directory -Force -Path $backupDir | Out-Null

    foreach ($item in $tasks) {
        $srcBinary = Join-Path $item.TargetDir "$($item.Target)\release\shod"
        if (-not (Test-Path -LiteralPath $srcBinary)) {
            $srcBinary = Join-Path $item.TargetDir "release\shod"
        }
        $stagedBinary = Join-Path $stagingDir $item.Asset
        Copy-Item -LiteralPath $srcBinary -Destination $stagedBinary
        if ((Get-Item -LiteralPath $stagedBinary).Length -le 0) {
            throw "Staged binary is empty: $($item.Asset)"
        }

        $destBinary = Join-Path $AssetsDir $item.Asset
        if (Test-Path -LiteralPath $destBinary) {
            Copy-Item -LiteralPath $destBinary -Destination (Join-Path $backupDir $item.Asset)
        }
    }

    try {
        foreach ($item in $tasks) {
            $stagedBinary = Join-Path $stagingDir $item.Asset
            $destBinary = Join-Path $AssetsDir $item.Asset
            $temporaryDestination = "$destBinary.shod-new-$PID"
            Copy-Item -LiteralPath $stagedBinary -Destination $temporaryDestination -Force

            if (Test-Path -LiteralPath $destBinary) {
                [System.IO.File]::Replace($temporaryDestination, $destBinary, $null)
            } else {
                [System.IO.File]::Move($temporaryDestination, $destBinary)
            }
            $publishedAssets += $item.Asset
        }
    } catch {
        Write-Host "Publication failed; restoring the previous Linux asset set..." -ForegroundColor Red
        foreach ($item in $tasks) {
            $destBinary = Join-Path $AssetsDir $item.Asset
            $backupBinary = Join-Path $backupDir $item.Asset
            $temporaryRestore = "$destBinary.shod-restore-$PID"

            try {
                if (Test-Path -LiteralPath $backupBinary) {
                    Copy-Item -LiteralPath $backupBinary -Destination $temporaryRestore -Force
                    if (Test-Path -LiteralPath $destBinary) {
                        [System.IO.File]::Replace($temporaryRestore, $destBinary, $null)
                    } else {
                        [System.IO.File]::Move($temporaryRestore, $destBinary)
                    }
                } elseif (Test-Path -LiteralPath $destBinary) {
                    Remove-Item -LiteralPath $destBinary -Force
                }
            } catch {
                Write-Host "  [!] Could not restore $($item.Asset): $($_.Exception.Message)" -ForegroundColor Red
            }
        }
        throw
    }

    foreach ($item in $tasks) {
        $destBinary = Join-Path $AssetsDir $item.Asset
        $size = (Get-Item -LiteralPath $destBinary).Length
        Write-Host "  [OK]   $($item.Name) -> $($item.Asset) ($size bytes)" -ForegroundColor Green
    }
} finally {
    foreach ($item in $tasks) {
        $destBinary = Join-Path $AssetsDir $item.Asset
        foreach ($temporaryFile in @("$destBinary.shod-new-$PID", "$destBinary.shod-restore-$PID")) {
            if (Test-Path -LiteralPath $temporaryFile) {
                Remove-Item -LiteralPath $temporaryFile -Force -ErrorAction SilentlyContinue
            }
        }
    }
    if (Test-Path -LiteralPath $stagingDir) {
        Remove-Item -LiteralPath $stagingDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "All Linux server variants compiled and published to $AssetsDir successfully.`n" -ForegroundColor Green
exit 0
