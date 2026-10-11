param([switch]$SkipTests, [switch]$UseMirror)
. "$PSScriptRoot/tools.ps1"
Push-Location -LiteralPath $ProjectRoot
try {
    $npm = Get-TaskNpm
    & $npm --prefix web ci --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { throw 'Web dependencies failed.' }
    & $npm --prefix web run build
    if ($LASTEXITCODE -ne 0) { throw 'Web build failed.' }
    $maven = Get-TaskMaven
    $arguments = @('-B','-ntp','clean','package')
    if ($UseMirror) { $arguments += @('-s', (Join-Path $ProjectRoot 'config/maven-settings-mirror.xml')) }
    if ($SkipTests) { $arguments += '-DskipTests' }
    & $maven @arguments
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed ($LASTEXITCODE)" }
} finally { Pop-Location }
