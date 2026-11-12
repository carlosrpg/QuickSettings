<#
.SYNOPSIS
Installs QuickSettings on the head unit over adb and turns on notification access.

.EXAMPLE
.\scripts\Install-OnCar.ps1 -Apk .\QuickSettings-0.1.1.apk
.\scripts\Install-OnCar.ps1 -Apk .\QuickSettings-0.1.1.apk -Serial 192.168.1.20:5555 -TapPassThrough
#>
param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [string]$Serial,
    # Also enable the optional accessibility service that passes edge-zone taps through.
    [switch]$TapPassThrough
)
$ErrorActionPreference = 'Stop'
$target = @()
if ($Serial) { $target = @('-s', $Serial) }
$setup = Join-Path $PSScriptRoot 'qs-setup.sh'

# No command line may contain the word the car's adbd filters, so push and run a script.
adb @target push $Apk /data/local/tmp/QuickSettings.apk
if ($LASTEXITCODE) { throw "adb push of the APK failed" }
adb @target push $setup /data/local/tmp/qs-setup.sh
if ($LASTEXITCODE) { throw "adb push of the setup script failed" }
$mode = if ($TapPassThrough) { 'tap' } else { '' }
adb @target shell "sh /data/local/tmp/qs-setup.sh $mode"
