param([switch]$SkipTests, [switch]$UseMirror)
. "$PSScriptRoot/tools.ps1"
Push-Location -LiteralPath $ProjectRoot
try {
    $maven = Get-TaskMaven
    $arguments = @('-B','-ntp','package')
    if ($UseMirror) { $arguments += @('-s', (Join-Path $ProjectRoot 'config/maven-settings-mirror.xml')) }
    if ($SkipTests) { $arguments += '-DskipTests' }
    & $maven @arguments
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed ($LASTEXITCODE)" }
} finally { Pop-Location }
