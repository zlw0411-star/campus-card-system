$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
function Find-TaskTool([string]$Name, [string[]]$Candidates) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    foreach ($candidate in $Candidates) { if ($candidate -and (Test-Path -LiteralPath $candidate)) { return $candidate } }
    throw "Cannot find $Name. Install it and set PATH or JAVA_HOME/MAVEN_HOME/MYSQL_HOME."
}
function Get-TaskJava {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates = @((Join-Path $env:JAVA_HOME 'bin/java.exe')) + $candidates }
    Find-TaskTool 'java' $candidates
}
function Get-TaskMaven {
    if (-not $env:JAVA_HOME) { $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent (Get-TaskJava)) }
    $candidates = @()
    if ($env:MAVEN_HOME) { $candidates = @((Join-Path $env:MAVEN_HOME 'bin/mvn.cmd')) + $candidates }
    Find-TaskTool 'mvn' $candidates
}
function Get-TaskMySql {
    $candidates = @()
    if ($env:MYSQL_HOME) { $candidates = @((Join-Path $env:MYSQL_HOME 'bin/mysql.exe')) + $candidates }
    Find-TaskTool 'mysql' $candidates
}

function Get-TaskNpm {
    Find-TaskTool 'npm.cmd' @()
}
