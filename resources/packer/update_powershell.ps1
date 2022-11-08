<#
.SYNOPSIS
  Silently installs powershell
.DESCRIPTION
  Install a newer version of powershell using powershell during a packer bake
  powershell source - https://docs.microsoft.com/en-us/powershell/scripting/install/installing-powershell-on-windows
.PARAMETER version
  powershell version, defaults to 7.2.5
#>

param (
  [string]$version = "7.2.5"
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
$source = "https://github.com/PowerShell/PowerShell/releases/download/v$version/PowerShell-$version-win-x64.msi"
$destination = "$workdir\PowerShell.msi"
Write-Host "INFO: Downloading powershell $version msi from $source"
Invoke-WebRequest $source -OutFile $destination

Write-Host "INFO: Installing powershell msi"
Start-Process "msiexec.exe /package $workdir/PowerShell.msi /quiet ADD_EXPLORER_CONTEXT_MENU_OPENPOWERSHELL=1 ADD_FILE_CONTEXT_MENU_RUNPOWERSHELL=1 ENABLE_PSREMOTING=1 REGISTER_MANIFEST=1"

Write-Host "INFO: Install complete. Cleaning up"
rm -Force "$workdir\PowerShell.msi"

Write-Output "INFO: powershell $version msi install complete"