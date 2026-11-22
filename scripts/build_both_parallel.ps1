$ErrorActionPreference = "Stop"

$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$serverDirectory = Join-Path $projectRoot "rust-server"
$clientDirectory = Join-Path $projectRoot "rust-client"

$buildBlock = {
    param([string]$WorkingDirectory, [string]$BuildScript)
    Set-Location -LiteralPath $WorkingDirectory
    & cmd.exe /c ('"' + $BuildScript + '" --no-tests')
    if ($LASTEXITCODE -ne 0) {
        throw "Build failed: $BuildScript (exit code $LASTEXITCODE)"
    }
}

Write-Host "Starting simultaneous builds: rust-server and rust-client..."
$serverJob = Start-Job -ScriptBlock $buildBlock -ArgumentList $serverDirectory, "build_linux.bat"
$clientJob = Start-Job -ScriptBlock $buildBlock -ArgumentList $clientDirectory, "build_so.bat"
$jobs = @($serverJob, $clientJob)

try {
    Wait-Job -Job $jobs | Out-Null
    Write-Host "=================== SERVER BUILD LOG ==================="
    Receive-Job -Job $serverJob
    Write-Host "=================== CLIENT BUILD LOG ==================="
    Receive-Job -Job $clientJob
    $failed = @($jobs | Where-Object { $_.State -ne "Completed" })
    if ($failed.Count -gt 0) { throw "One or more parallel builds failed." }
}
finally {
    Remove-Job -Job $jobs -Force -ErrorAction SilentlyContinue
}
