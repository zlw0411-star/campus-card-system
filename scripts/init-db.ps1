param([string]$HostName='127.0.0.1',[int]$Port=3306,[string]$User='root',[string]$Database='campus_card',[switch]$Seed,[switch]$WebDemo,[switch]$PromptPassword)
. "$PSScriptRoot/tools.ps1"
if ($Database -notmatch '^[A-Za-z0-9_]+$') { throw 'Database name must contain only letters, digits and underscore.' }
$mysql = Get-TaskMySql
$arguments = @('--protocol=TCP',"--host=$HostName","--port=$Port","--user=$User",'--default-character-set=utf8mb4',"--init-command=SET time_zone = '+08:00'")
if ($PromptPassword) { $arguments += '-p' }
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$createSql = 'CREATE DATABASE IF NOT EXISTS `' + $Database + '` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'
$createSql | & $mysql @arguments
if ($LASTEXITCODE -ne 0) { throw 'Database creation failed.' }
Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $ProjectRoot 'database/schema.sql') | & $mysql @arguments "--database=$Database"
if ($LASTEXITCODE -ne 0) { throw 'Schema initialization failed.' }
if ($Seed) {
    Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $ProjectRoot 'database/seed.sql') | & $mysql @arguments "--database=$Database"
    if ($LASTEXITCODE -ne 0) { throw 'Seed initialization failed.' }
}
if ($WebDemo) {
    Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $ProjectRoot 'database/demo-web.sql') | & $mysql @arguments "--database=$Database"
    if ($LASTEXITCODE -ne 0) { throw 'Optional web demo initialization failed.' }
}
Write-Output "Initialized $Database. Seed=$Seed WebDemo=$WebDemo"
