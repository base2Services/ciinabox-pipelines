<#
.SYNOPSIS
  Downloads cookbooks directly from s3
.DESCRIPTION
  Downloads chef cookbooks from s3 to avoid the slow winrm through packer.
  Cookbooks are expected to already exist in a s3 bucket stored in a tar.gz format 
  and the ec2 instances has IAM access to the bucket though an assumed role.
  It is also assumed 7-Zip is already installed.
#>

$ErrorActionPreference = "Stop"
Set-ExecutionPolicy Bypass -force

$WorkDir         = "c:\temp\"
$ChefDir         = "C:\chef\"
$CookbookDir     = "C:\chef\cookbooks"
$GzipPath        = "$WorkDir\cookbooks.tar.gz"
$TarPath         = "$WorkDir\cookbooks.tar"
$SourceBucket    = $ENV:SOURCE_BUCKET
$BucketRegion    = $ENV:BUCKET_REGION
$CookbookPath    = $ENV:COOKBOOK_PATH

Write-Output "INFO: Downloading cookbooks from s3 location: $SourceBucket/$CookbookPath"
Read-S3Object -Region $BucketRegion -BucketName $SourceBucket -Key $CookbookPath -File "$WorkDir\cookbooks.tar.gz"

Write-Output "INFO: Deleting dir $CookbookDir"
if(Test-Path -Path $CookbookDir ){
  Remove-Item -Recurse -Force $CookbookDir
}

Write-Output "INFO: Extracting $GzipPath to $CookbookDir"
& "C:\Program Files\7-Zip\7z.exe" x $GzipPath -o"$WorkDir" -y
& "C:\Program Files\7-Zip\7z.exe" x $TarPath -o"$ChefDir" -y

Write-Output "INFO: Cleaning up $GzipPath $TarPath"
rm $GzipPath
rm $TarPath
