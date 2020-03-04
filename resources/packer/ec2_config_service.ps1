<#
.SYNOPSIS
  Sets up the EC2-Config Service
.DESCRIPTION
  Windows AMIs for Windows Server 2012 R2 and earlier
  https://docs.aws.amazon.com/AWSEC2/latest/WindowsGuide/ec2config-service.html
#>

$ErrorActionPreference = "Stop"
Set-ExecutionPolicy Bypass -force
Write-Output "INFO: Setting up the EC2-Config file"

$EC2SettingsFile = "C:\Program Files\Amazon\Ec2ConfigService\Settings\Config.xml"
$xml = [xml](get-content $EC2SettingsFile)
$xmlElement = $xml.get_DocumentElement()
$xmlElementToModify = $xmlElement.Plugins

foreach ($element in $xmlElementToModify.Plugin) {
  if ($element.name -eq "Ec2SetPassword") {
    $element.State = "Enabled"
  } elseif ($element.name -eq "Ec2HandleUserData") {
    $element.State = "Enabled"
  } elseif ($element.name -eq "Ec2DynamicBootVolumeSize") {
    $element.State = "Enabled"
  }
}

$xml.Save($EC2SettingsFile)

Write-Output "INFO: Successfully completed setting up the EC2-Config Service"
