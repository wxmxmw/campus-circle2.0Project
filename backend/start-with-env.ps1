param(
  [switch]$SkipPackage
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

$envFile = Join-Path $scriptDir '.env'
if (-not (Test-Path $envFile)) {
  Write-Host ".env not found. Copy .env.example to .env first." -ForegroundColor Yellow
  exit 1
}

[System.IO.File]::ReadAllLines($envFile, [System.Text.Encoding]::UTF8) | ForEach-Object {
  $line = $_.Trim()
  if (-not $line -or $line.StartsWith('#')) {
    return
  }

  $pair = $line -split '=', 2
  if ($pair.Length -ne 2) {
    return
  }

  $name = $pair[0].Trim()
  $value = $pair[1].Trim()

  if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
    $value = $value.Substring(1, $value.Length - 2)
  }

  [System.Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

Write-Host "Loaded .env" -ForegroundColor Green
if (-not $env:JWT_SECRET -or $env:JWT_SECRET.StartsWith('replace-with-') -or
    [System.Text.Encoding]::UTF8.GetByteCount($env:JWT_SECRET) -lt 32) {
  throw 'Set JWT_SECRET to a unique random value of at least 32 bytes in .env.'
}
Write-Host "LLM_BASE_URL=$env:LLM_BASE_URL"
Write-Host "LLM_MODEL=$env:LLM_MODEL"
if (($env:LLM_MODEL -like '*?*') -or ($env:LLM_MODEL -like '*�*')) {
  Write-Host "Warning: LLM_MODEL still looks garbled. Please save .env as UTF-8." -ForegroundColor Yellow
}

if (-not $SkipPackage) {
  mvn clean package -DskipTests
  if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
  }
}

java -jar target\campus-backend-0.0.1-SNAPSHOT.jar
