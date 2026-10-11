param([int]$Port=8088,[switch]$Background,[switch]$UseMirror)
. "$PSScriptRoot/tools.ps1"
if ($Port -lt 1024 -or $Port -gt 65535) { throw 'Invalid application port.' }
Push-Location -LiteralPath $ProjectRoot
try {
    $jar = Join-Path $ProjectRoot 'server/target/campus-card-web.jar'
    if (-not (Test-Path -LiteralPath $jar)) { & "$PSScriptRoot/build.ps1" -UseMirror:$UseMirror }
    $java = Get-TaskJava
    if ($Background) {
        $localDir = Join-Path $ProjectRoot 'local'
        New-Item -ItemType Directory -Path $localDir -Force | Out-Null
        $stateFile = Join-Path $localDir "web-$Port.json"
        if (Test-Path -LiteralPath $stateFile) {
            $state = Get-Content -Raw -LiteralPath $stateFile | ConvertFrom-Json
            $existing = Get-Process -Id $state.processId -ErrorAction SilentlyContinue
            if ($existing -and $existing.StartTime.ToUniversalTime().Ticks -eq $state.startTicks) {
                Write-Output "Application already running: http://127.0.0.1:$Port"
                return
            }
        }
        if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) { throw "Port $Port is occupied." }
        $arguments = @('-Dfile.encoding=UTF-8','-jar', ('"' + $jar + '"'), "--server.port=$Port")
        $process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $ProjectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $localDir "web-$Port.log") -RedirectStandardError (Join-Path $localDir "web-$Port-error.log")
        @{processId=$process.Id; startTicks=$process.StartTime.ToUniversalTime().Ticks; port=$Port} | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath $stateFile
        Write-Output "Application starting: http://127.0.0.1:$Port (PID $($process.Id))"
    } else {
        & $java '-Dfile.encoding=UTF-8' '-jar' $jar "--server.port=$Port"
        if ($LASTEXITCODE -ne 0) { throw "Application exited ($LASTEXITCODE)" }
    }
} finally { Pop-Location }
