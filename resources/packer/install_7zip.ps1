<#
.SYNOPSIS
  Silently install 7-Zip
.DESCRIPTION
  Install 7-Zip using powershell during a packer bake
.PARAMETER version
  7-Zip MSI build number (filename), default 2301 -> 7z2301-x64.msi from GitHub ip7z/7zip release 23.01.
  Other values use https://www.7-zip.org/a/7z{version}-x64.msi
#>

param (
  [string]$version = "2301"
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

# Download the installer (23.01+ MSIs: official hosting is GitHub; www.7-zip.org often 404s)
if ($version -eq "2301") {
  $source = "https://github.com/ip7z/7zip/releases/download/23.01/7z2301-x64.msi"
} else {
  $source = "https://www.7-zip.org/a/7z$version-x64.msi"
}
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
