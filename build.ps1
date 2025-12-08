<#
  bt-takeover build helper (Windows)
  Builds and optionally publishes a single-file EXE for Windows.

  Examples:
    .\build.ps1
    .\build.ps1 -Configuration Debug -Publish:$false
#>

[CmdletBinding()]
param(
  [ValidateSet('Debug','Release')]
  [string]$Configuration = 'Release',

  [string]$Runtime = 'win-x64',

  [switch]$Publish = $true,

  [switch]$SelfContained = $true
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Include-Helpers {
  $candidates = @(
    'script-helpers\ps\helpers.ps1',
    'script-helpers\powershell\helpers.ps1',
    'script-helpers\init.ps1'
  )
  foreach ($f in $candidates) {
    if (Test-Path $f) { . $f; return $true }
  }
  return $false
}

function Log-Info  { param($m) Write-Host "[INFO]  $m" -ForegroundColor Cyan }
function Log-Warn  { param($m) Write-Host "[WARN]  $m" -ForegroundColor Yellow }
function Log-Error { param($m) Write-Host "[ERROR] $m" -ForegroundColor Red }

if (-not (Include-Helpers)) { Log-Warn 'script-helpers not found; using built-in logging.' }

if (-not (Get-Command dotnet -ErrorAction SilentlyContinue)) {
  Log-Error 'dotnet CLI not found. Install .NET 8 SDK.'
  exit 1
}

function New-AppIcon {
  try {
    $svg = Join-Path (Get-Location) 'assets/logo/headphones-noise.svg'
    $ico = Join-Path (Get-Location) 'src/Assets/AppIcon.ico'
    if (-not (Test-Path $svg)) { return }
    if (Test-Path $ico) { return }
    if (Get-Command magick -ErrorAction SilentlyContinue) {
      Log-Info "Generating Windows .ico from SVG via ImageMagick..."
      & magick convert -background none -density 384 `
        "$svg" -define icon:auto-resize=256,128,64,48,32,16 `
        "$ico" | Out-Null
      if (Test-Path $ico) { Log-Info "Generated: $ico" } else { Log-Warn 'ICO generation failed.' }
    } elseif (Get-Command convert.exe -ErrorAction SilentlyContinue) {
      Log-Info "Generating Windows .ico from SVG via convert.exe..."
      & convert.exe -background none -density 384 `
        "$svg" -define icon:auto-resize=256,128,64,48,32,16 `
        "$ico" | Out-Null
      if (Test-Path $ico) { Log-Info "Generated: $ico" } else { Log-Warn 'ICO generation failed.' }
    } else {
      Log-Warn 'ImageMagick not found; skipping ICO generation. Install ImageMagick to auto-generate AppIcon.ico.'
    }
  } catch {
    Log-Warn "ICO generation error: $_"
  }
}

New-AppIcon
Log-Info 'Restoring packages...'
dotnet restore 'src/BtTakeover.csproj'

Log-Info "Building ($Configuration)..."
dotnet build 'src/BtTakeover.csproj' -c $Configuration --nologo

if ($Publish) {
  $outDir = Join-Path (Get-Location) "publish\$Runtime"
  New-Item -ItemType Directory -Force -Path $outDir | Out-Null
  Log-Info "Publishing single-file (SelfContained=$SelfContained) to $outDir..."
  dotnet publish 'src/BtTakeover.csproj' -c $Configuration -r $Runtime `
    /p:PublishSingleFile=true /p:SelfContained=$($SelfContained.IsPresent) /p:IncludeNativeLibrariesForSelfExtract=true `
    -o $outDir
  Log-Info "Publish complete: $outDir\BtTakeover.exe"
} else {
  Log-Info 'Publish disabled.'
}
