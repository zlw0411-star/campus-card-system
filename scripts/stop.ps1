param([int]$Port=8088)
. "$PSScriptRoot/tools.ps1"
if ($Port -lt 1024 -or $Port -gt 65535) { throw 'Invalid application port.' }
$stateFile = Join-Path $ProjectRoot "local/web-$Port.json"
if (-not (Test-Path -LiteralPath $stateFile)) { Write-Output 'No application started by this script.'; return }
$state = Get-Content -Raw -LiteralPath $stateFile | ConvertFrom-Json
$process = Get-Process -Id $state.processId -ErrorAction SilentlyContinue
if ($process) {
    if ($process.StartTime.ToUniversalTime().Ticks -ne $state.startTicks) { throw 'PID has been reused; refusing to stop another process.' }
    $command = Get-CimInstance Win32_Process -Filter "ProcessId=$($state.processId)"
    $jar = Join-Path $ProjectRoot 'server/target/campus-card-web.jar'
    if (-not $command.CommandLine.Contains($jar)) { throw 'Process is not this project; refusing to stop it.' }
    Stop-Process -Id $state.processId
}
Remove-Item -LiteralPath $stateFile
Write-Output "Stopped campus card server on port $Port."
