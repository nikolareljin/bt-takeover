<#
.SYNOPSIS
  One-click sideload installer for BtTakeover MSIX.

.DESCRIPTION
  - Installs BtTakeover.cer to Trusted People (LocalMachine; falls back to CurrentUser).
  - Installs the BtTakeover *.msix using Add-AppxPackage.
  - Must run elevated. If not, re-launches itself with elevation.

.USAGE
  - Place BtTakeover.cer and BtTakeover-*-win-x64.msix in the same folder as this script.
  - Right-click > Run with PowerShell (Admin) or run in an elevated PowerShell.
  - Optional parameters for custom paths:
    .\install-sideload.ps1 -MsixPath .\BtTakeover-v1.2.3-win-x64.msix -CertPath .\BtTakeover.cer
#>

[CmdletBinding()]
param(
  [string]$MsixPath,
  [string]$CertPath
)

function Ensure-Elevated {
  $current = [Security.Principal.WindowsIdentity]::GetCurrent()
  $principal = New-Object Security.Principal.WindowsPrincipal($current)
  if (-not $principal.IsInRole([Security.Principal.WindowsBuiltinRole]::Administrator)) {
    Write-Host "Elevation required. Relaunching as administrator..." -ForegroundColor Yellow
    $argsList = "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`""
    if ($MsixPath) { $argsList += " -MsixPath `"$MsixPath`"" }
    if ($CertPath) { $argsList += " -CertPath `"$CertPath`"" }
    Start-Process -FilePath "powershell.exe" -Verb RunAs -ArgumentList $argsList | Out-Null
    exit 0
  }
}

function Resolve-Inputs {
  if (-not $CertPath) {
    if (Test-Path "BtTakeover.cer") { $CertPath = (Resolve-Path "BtTakeover.cer").Path }
    elseif (Get-ChildItem -Filter *.cer -File -ErrorAction SilentlyContinue | Select-Object -First 1) {
      $CertPath = (Get-ChildItem -Filter *.cer -File | Select-Object -First 1).FullName
    }
  }
  if (-not $MsixPath) {
    $msix = Get-ChildItem -Filter "BtTakeover-*-win-x64.msix" -File -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $msix) { $msix = Get-ChildItem -Filter *.msix -File -ErrorAction SilentlyContinue | Select-Object -First 1 }
    if ($msix) { $MsixPath = $msix.FullName }
  }
  if (-not (Test-Path $CertPath)) { throw "Certificate not found. Set -CertPath or place BtTakeover.cer next to the script." }
  if (-not (Test-Path $MsixPath)) { throw "MSIX not found. Set -MsixPath or place the MSIX next to the script." }
}

function Install-Certificate {
  param([string]$Path)
  Write-Host "Installing certificate: $Path" -ForegroundColor Cyan
  try {
    Import-Certificate -FilePath $Path -CertStoreLocation Cert:\\LocalMachine\\TrustedPeople | Out-Null
    Write-Host "Installed cert to LocalMachine\\TrustedPeople" -ForegroundColor Green
  } catch {
    Write-Host "LocalMachine install failed, trying CurrentUser..." -ForegroundColor Yellow
    Import-Certificate -FilePath $Path -CertStoreLocation Cert:\\CurrentUser\\TrustedPeople | Out-Null
    Write-Host "Installed cert to CurrentUser\\TrustedPeople" -ForegroundColor Green
  }
}

function Install-Package {
  param([string]$Path)
  Write-Host "Installing MSIX: $Path" -ForegroundColor Cyan
  Add-AppxPackage -ForceApplicationShutdown -ForceUpdateFromAnyVersion "$Path"
  Write-Host "MSIX installation completed." -ForegroundColor Green
}

try {
  Ensure-Elevated
  Resolve-Inputs
  Install-Certificate -Path $CertPath
  Install-Package -Path $MsixPath
  Write-Host "Done." -ForegroundColor Green
} catch {
  Write-Error $_
  exit 1
}

