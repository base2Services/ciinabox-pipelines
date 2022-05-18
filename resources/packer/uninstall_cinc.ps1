<#
.SYNOPSIS
  Uninstall the Cinc package and remove cookbooks
.DESCRIPTION
  Removes Cinc installation and any cookbooks to 
  avoid issues with upstream bakes using chef
  It searches for the cinc installation via the vendor to match any version 
#>

$ErrorActionPreference = "Stop"
Set-ExecutionPolicy Bypass -force

Write-Output "INFO: Uninstalling Cinc ..."

$cinc = Get-WmiObject -Class Win32_Product -Filter "Vendor = 'Cinc Software, Inc.'"
$cinc.Uninstall()

Write-Output "INFO: Cleaning up cookbooks"
Remove-Item -Recurse -Force c:/chef

Write-Output "INFO: Cinc cleanup complete"