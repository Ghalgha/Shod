[CmdletBinding()]
param(
    [string[]]$Distros = @("Ubuntu-24.04", "Debian"),
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
$ProjectRoot = [IO.Path]::GetFullPath($ProjectRoot)

Write-Host ""
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "         Shod -- UNIFIED WSL VERIFICATION RUNNER" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "Project Root: $ProjectRoot" -ForegroundColor DarkGray
Write-Host "Distros:      $($Distros -join ', ')" -ForegroundColor DarkGray
Write-Host "Timestamp:    $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor DarkGray
Write-Host ""

$installed = @(wsl.exe -l -q | ForEach-Object { $_.Trim().Replace("`0", "") } | Where-Object { $_ -ne "" })
$results = [ordered]@{}

foreach ($distro in $Distros) {
    Write-Host "--------------------------------------------------------------------------------" -ForegroundColor Yellow
    Write-Host ">>> CHECKING DISTRO: $distro" -ForegroundColor Yellow
    Write-Host "--------------------------------------------------------------------------------" -ForegroundColor Yellow

    if ($installed -notcontains $distro) {
        Write-Host "[-] $distro not installed in WSL. Skipping.`n" -ForegroundColor DarkYellow
        $results[$distro] = "NOT_INSTALLED"
        continue
    }

    $linuxRootOutput = & wsl.exe -d $distro -- wslpath -a $ProjectRoot 2>&1
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($linuxRootOutput | Out-String))) {
        Write-Host "[ERROR] Could not translate project path for $distro" -ForegroundColor Red
        $results[$distro] = "FAILED"
        continue
    }
    $linuxRoot = ($linuxRootOutput | Select-Object -Last 1).ToString().Trim()
    $linuxScript = "$linuxRoot/scripts/run_wsl_checks.sh"
    $output = & wsl.exe -d $distro -u root -- bash $linuxScript 2>&1
    $exitCode = $LASTEXITCODE

    $output | ForEach-Object {
        if ($_ -match "OK") {
            Write-Host "  $_" -ForegroundColor Green
        } elseif ($_ -match "SKIPPED") {
            Write-Host "  $_" -ForegroundColor DarkYellow
        } elseif ($_ -match "FAILED|ERROR") {
            Write-Host "  $_" -ForegroundColor Red
        } else {
            Write-Host "  $_" -ForegroundColor Gray
        }
    }

    $isAllPassed = $output | Select-String -Pattern "ALL REQUIRED SYSTEM CHECKS PASSED SUCCESSFULLY" -SimpleMatch
    if ($exitCode -eq 0 -and $isAllPassed) {
        $results[$distro] = "READY_FOR_RELEASE"
    } else {
        $results[$distro] = "FAILED"
    }
    Write-Host ""
}

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "FINAL RELEASE READINESS MATRIX:" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan
foreach ($d in $results.Keys) {
    $status = $results[$d]
    $color = if ($status -eq "READY_FOR_RELEASE") { "Green" } else { "Red" }
    Write-Host ("Distro: {0,-16} | Status: {1}" -f $d, $status) -ForegroundColor $color
}
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host ""
if (@($results.Values | Where-Object { $_ -eq "FAILED" }).Count -gt 0) { exit 1 }
