<#
.SYNOPSIS
  Sets up the EC2-Launch file
.DESCRIPTION
  Windows AMIs for Windows Server 2016 and later
  https://docs.aws.amazon.com/AWSEC2/latest/WindowsGuide/ec2launch.html
#>

$ErrorActionPreference = "Stop"
Set-ExecutionPolicy Bypass -force
Write-Output "INFO: Setting up the EC2-Launch file"

$EC2SettingsFile="C:\ProgramData\Amazon\EC2-Windows\Launch\Config\LaunchConfig.json"
$json = Get-Content $EC2SettingsFile | ConvertFrom-Json
$json.setComputerName = "true"
$json.setWallpaper = "true"
$json.addDnsSuffixList = "true"
$json.extendBootVolumeSize = "true"
$json.adminPasswordType = "Random"
$json | ConvertTo-Json  | set-content $EC2SettingsFile

Write-Output "INFO: Successfully completed creating the EC2-Launch file"
