[CmdletBinding()]
param([string[]]$Distros=@(),[string]$ProjectRoot="",[string]$ServerBinary="",[string]$ServerTestBinary="",[switch]$CheckDocker)
$ErrorActionPreference="Stop"
function Convert-WslPath([string]$Distro,[string]$Path) {
 $o=@(& wsl.exe -d $Distro -- wslpath -a $Path 2>&1); $c=$LASTEXITCODE
 if($c -ne 0 -or $o.Count -eq 0){throw "Cannot convert path '$Path' for WSL distro '$Distro'."}
 return (($o | ForEach-Object {"$_".Trim()} | Where-Object {$_} | Select-Object -Last 1))
}
try {
 if([string]::IsNullOrWhiteSpace($ProjectRoot)){$ProjectRoot=Split-Path -Parent $PSScriptRoot}
 $ProjectRoot=(Resolve-Path -LiteralPath $ProjectRoot).Path
 $runnerWin=Join-Path $ProjectRoot "scripts\run_wsl_checks.sh"
 if(-not(Test-Path -LiteralPath $runnerWin -PathType Leaf)){throw "Missing scripts\run_wsl_checks.sh under '$ProjectRoot'."}
 $installedOut=@(& wsl.exe --list --quiet 2>&1); $installedCode=$LASTEXITCODE
 if($installedCode -ne 0){throw "Cannot list installed WSL distros (exit $installedCode)."}
 $installed=@($installedOut | ForEach-Object {("$_" -replace [string][char]0,"").Trim().TrimStart("*").Trim()} | Where-Object {$_})
 if($Distros.Count -eq 0){$Distros=$installed}
 if($Distros.Count -eq 0){throw "No installed WSL distros. Install one or pass -Distros."}
 Write-Host "Shod WSL checks"; Write-Host "Project: $ProjectRoot"; Write-Host "Distros: $($Distros -join ', ')"; Write-Host "Docker opt-in: $([bool]$CheckDocker)"
 $results=@()
 foreach($distro in $Distros){
  Write-Host ""; Write-Host "--- $distro ---" -ForegroundColor Yellow
  if($installed -notcontains $distro){Write-Host "NOT_INSTALLED" -ForegroundColor Red;$results += [pscustomobject]@{Distro=$distro;Status="NOT_INSTALLED"};continue}
  try {
   $runnerWsl=Convert-WslPath $distro $runnerWin
   $args=@("-d",$distro,"-u","root","--","env")
   if($CheckDocker){$args += "SHOD_CHECK_DOCKER=1"}else{$args += "SHOD_CHECK_DOCKER=0"}
   if($ServerBinary){$serverWsl=Convert-WslPath $distro (Resolve-Path -LiteralPath $ServerBinary).Path;$args += "SHOD_SERVER_BIN=$serverWsl"}
   if($ServerTestBinary){$testWsl=Convert-WslPath $distro (Resolve-Path -LiteralPath $ServerTestBinary).Path;$args += "SHOD_SERVER_TEST_BIN=$testWsl"}
   $args += @("bash",$runnerWsl)
   $output=@(& wsl.exe @args 2>&1);$exitCode=$LASTEXITCODE
   foreach($line in $output){$s="$_";if($s -match "FAILED|ERROR"){Write-Host $s -ForegroundColor Red}elseif($s -match "SKIPPED"){Write-Host $s -ForegroundColor DarkYellow}else{Write-Host $s}}
   $marker=$output | Where-Object {"$_" -match "^STATUS: requested checks completed successfully\.$"}
   if($exitCode -eq 0 -and $null -ne $marker){$results += [pscustomobject]@{Distro=$distro;Status="CHECKS_PASSED"};Write-Host "CHECKS_PASSED" -ForegroundColor Green}
   else{$results += [pscustomobject]@{Distro=$distro;Status="FAILED"};Write-Host "FAILED (wsl exit $exitCode)" -ForegroundColor Red}
  }catch{$results += [pscustomobject]@{Distro=$distro;Status="FAILED"};Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red}
 }
 Write-Host ""; Write-Host "Final result:"; $results | Format-Table -AutoSize
 if(@($results | Where-Object {$_.Status -ne "CHECKS_PASSED"}).Count -gt 0){exit 1};exit 0
}catch{Write-Error $_;exit 1}
