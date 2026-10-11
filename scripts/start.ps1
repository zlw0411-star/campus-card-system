param([switch]$CheckDatabase)
. "$PSScriptRoot/tools.ps1"
Push-Location -LiteralPath $ProjectRoot
try {
    $jar = Join-Path $ProjectRoot 'frontend/target/campus-card-system.jar'
    if (-not (Test-Path -LiteralPath $jar)) { & "$PSScriptRoot/build.ps1" }
    $java = Get-TaskJava
    $arguments = @('-Dfile.encoding=UTF-8','-jar',$jar)
    if ($CheckDatabase) { $arguments += '--check-db' }
    & $java @arguments
    if ($LASTEXITCODE -ne 0) { throw "Application exited ($LASTEXITCODE)" }
} finally { Pop-Location }
