<#
.SYNOPSIS
  Silently install 7-Zip
.DESCRIPTION
  Install 7-Zip using powershell during a packer bake
  7-Zip source - http://www.7-zip.org/download.html
.PARAMETER version
  7-Zip version, defaults to 1900 -> https://www.7-zip.org/a/7z1900-x64.msi
#>

param (
  [string]$version = "2107"
)

$ErrorActionPreference = "Stop"
Set-ExecutionPolicy Bypass -force

$workdir = "c:\temp\"

# Check if work directory exists if not create it
if (Test-Path -Path $workdir -PathType Container) {
  Write-Host "$workdir already exists"
} else {
  New-Item -Path $workdir  -ItemType directory
}

# override the defaults (SSLv3/TLSv1) to use TLSv1.2
[System.Net.ServicePointManager]::SecurityProtocol = (
    [System.Net.ServicePointManager]::SecurityProtocol -bor
    [System.Net.SecurityProtocolType]::Tls12
)

# Download the installer
$source = "https://www.7-zip.org/a/7z$version-x64.msi"
$destination = "$workdir\7-Zip.msi"
Write-Host "INFO: Downloading 7-Zip $version msi from $source"
Invoke-WebRequest $source -OutFile $destination

Write-Host "INFO: Installing 7-Zip msi"
$MSIArguments = @(
  "/i"
  "$workdir\7-Zip.msi"
  "/qb"
)
Start-Process "msiexec.exe" -ArgumentList $MSIArguments -Wait -NoNewWindow

Write-Host "INFO: Install complete. Cleaning up"
rm -Force $workdir\7*

# Add 7zip to path
Write-Output "INFO: Adding command 7z to the path"
[Environment]::SetEnvironmentVariable("PATH","$env:path;$env:programfiles\7-Zip","MACHINE")
$env:path = "$env:path;$env:programfiles\7-Zip"

Write-Output "INFO: 7zip msi install complete"
