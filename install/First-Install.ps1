<#
.SYNOPSIS
    First-time setup of QuickSettings on the car's head unit over adb.
.DESCRIPTION
    Finds (or installs) adb, waits for the car, pushes the latest release APK to the car's
    Downloads folder when the app is missing, then applies every adb-only grant that is not
    already in place. Safe to run again: it only applies what is missing.
.PARAMETER Serial
    adb serial of the car, when more than one device is connected.
.PARAMETER TimeoutMinutes
    How long to wait for the car to appear. Default 10.
#>
[CmdletBinding()]
param(
    [string]$Serial,
    [int]$TimeoutMinutes = 10
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'  # the PS 5.1 progress bar slows downloads to a crawl

$Pkg = 'com.carlosrpg.cherry.quicksettings'
$Repo = 'carlosrpg/QuickSettings'
$script:Adb = $null
$script:Failed = $false

function Write-Step([string]$Text) { Write-Host ''; Write-Host "== $Text" -ForegroundColor Cyan }
function Write-Info([string]$Text) { Write-Host "   $Text" }
function Write-Warn([string]$Text) { Write-Host "   $Text" -ForegroundColor Yellow }
function Stop-WithError([string]$Text) {
    Write-Host ''
    Write-Host "ERROR: $Text" -ForegroundColor Red
    exit 1
}

function Find-Adb {
    $cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Links\adb.exe'),
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
    )
    $pkgRoot = Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Packages'
    if (Test-Path $pkgRoot) {
        $found = Get-ChildItem $pkgRoot -Directory -Filter 'Google.PlatformTools*' -ErrorAction SilentlyContinue |
            ForEach-Object { Get-ChildItem $_.FullName -Recurse -Filter adb.exe -ErrorAction SilentlyContinue } |
            Select-Object -First 1
        if ($found) { $candidates = @($candidates[0], $found.FullName, $candidates[1]) }
    }
    foreach ($c in $candidates) { if ($c -and (Test-Path $c)) { return $c } }
    return $null
}

# Runs adb and returns its output lines as strings. Stderr is merged in; under 'Stop' PS 5.1
# would turn native stderr into a terminating error, so relax it for the call.
function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$AdbArgs)
    $all = @()
    if ($Serial) { $all += @('-s', $Serial) }
    $all += $AdbArgs
    # The head unit's adbd silently drops any command line containing "install". A local file
    # (the push source) never reaches the car, so it is exempt.
    $sent = $all | Where-Object { -not (Test-Path -LiteralPath $_ -PathType Leaf) }
    if (($sent -join ' ') -match 'install') { throw "Refusing adb command containing 'install': $($sent -join ' ')" }
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $out = & $script:Adb @all 2>&1 | ForEach-Object { "$_" } }
    finally { $ErrorActionPreference = $old }
    return @($out)
}

function Get-AdbDevices {
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $lines = & $script:Adb devices 2>&1 | ForEach-Object { "$_" } }
    finally { $ErrorActionPreference = $old }
    foreach ($line in $lines) {
        if ($line -match '^(\S+)\s+(device|unauthorized|offline|no permissions.*|recovery|sideload|bootloader)\s*$') {
            [pscustomobject]@{ Serial = $Matches[1]; State = $Matches[2] }
        }
    }
}

# ---------------------------------------------------------------- 1. adb
Write-Step 'Looking for adb'
$script:Adb = Find-Adb
if (-not $script:Adb) {
    Write-Warn 'adb not found. Installing Google Platform Tools with winget...'
    if (-not (Get-Command winget.exe -ErrorAction SilentlyContinue)) {
        Stop-WithError ('winget is not available. Install "App Installer" from the Microsoft Store, ' +
            'or download Android Platform Tools from https://developer.android.com/tools/releases/platform-tools ' +
            'and put adb.exe on PATH, then run this again.')
    }
    & winget.exe install --id Google.PlatformTools -e --accept-source-agreements --accept-package-agreements
    # PATH does not refresh inside this session, so look in the known folders again.
    $script:Adb = Find-Adb
    if (-not $script:Adb) { Stop-WithError 'winget finished but adb.exe was not found. Open a new terminal and run this again.' }
}
Write-Info "Using $script:Adb"

# ---------------------------------------------------------------- 2. device
Write-Step 'Connecting to the car'
Write-Info '1. On the car, enable ADB (developer / engineering menu).'
Write-Info '2. Connect the USB cable between this PC and the car.'
Write-Info 'Waiting for the car... (Ctrl+C to abort)'

$null = Invoke-Adb kill-server
$null = Invoke-Adb start-server

$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
$hinted = @{}
while ($true) {
    $devices = @(Get-AdbDevices)
    if ($Serial) {
        $mine = @($devices | Where-Object { $_.Serial -eq $Serial })
        if ($mine.Count -and $mine[0].State -eq 'device') { break }
    } else {
        $ready = @($devices | Where-Object { $_.State -eq 'device' })
        if ($ready.Count -eq 1) { $Serial = $ready[0].Serial; break }
        if ($ready.Count -gt 1) {
            Write-Warn 'Several devices are connected:'
            for ($i = 0; $i -lt $ready.Count; $i++) { Write-Info "[$($i + 1)] $($ready[$i].Serial)" }
            $pick = Read-Host '   Number of the car'
            $n = 0
            if ([int]::TryParse($pick, [ref]$n) -and $n -ge 1 -and $n -le $ready.Count) {
                $Serial = $ready[$n - 1].Serial; break
            }
            Write-Warn 'Invalid choice.'
            continue
        }
    }
    foreach ($d in $devices) {
        if ($d.State -ne 'device' -and -not $hinted[$d.Serial + $d.State]) {
            $hinted[$d.Serial + $d.State] = $true
            if ($d.State -eq 'unauthorized') {
                Write-Warn "$($d.Serial) is unauthorized: accept the 'Allow USB debugging' prompt on the car screen."
            } else {
                Write-Warn "$($d.Serial) is $($d.State)."
            }
        }
    }
    if ((Get-Date) -gt $deadline) { Stop-WithError "No car found within $TimeoutMinutes minutes." }
    Start-Sleep -Seconds 2
}
Write-Host "   Connected: $Serial" -ForegroundColor Green

# ---------------------------------------------------------------- 3. package
function Test-AppPresent {
    return [bool](Invoke-Adb shell pm list packages $Pkg | Where-Object { $_.Trim() -eq "package:$Pkg" })
}

Write-Step 'Checking for QuickSettings on the car'
if (Test-AppPresent) {
    Write-Host '   QuickSettings is on the car.' -ForegroundColor Green
} else {
    Write-Warn 'QuickSettings is not on the car yet. Fetching the latest release...'
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    $headers = @{ 'User-Agent' = 'QuickSettings-FirstSetup'; 'Accept' = 'application/vnd.github+json' }
    try {
        $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases/latest" -Headers $headers
    } catch {
        Stop-WithError "Could not reach GitHub: $($_.Exception.Message)"
    }
    $asset = @($release.assets | Where-Object { $_.name -like '*.apk' }) | Select-Object -First 1
    if (-not $asset) { Stop-WithError "Release $($release.tag_name) has no .apk asset." }

    $remoteName = $asset.name
    if ($remoteName -match 'install') { $remoteName = 'QuickSettings.apk' }  # adbd drops such lines
    $tempDir = Join-Path ([IO.Path]::GetTempPath()) ('qs-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
    New-Item -ItemType Directory -Path $tempDir | Out-Null
    $localApk = Join-Path $tempDir $remoteName
    Write-Info "Downloading $($asset.name) ($($release.tag_name))..."
    try {
        Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $localApk -Headers @{ 'User-Agent' = 'QuickSettings-FirstSetup' } -UseBasicParsing
    } catch {
        Stop-WithError "Download failed: $($_.Exception.Message)"
    }

    Write-Info "Copying it to the car's Download folder..."
    $pushOut = Invoke-Adb push $localApk "/sdcard/Download/$remoteName"
    $pushOut | ForEach-Object { Write-Info $_ }
    $check = Invoke-Adb shell ls "/sdcard/Download/$remoteName"
    if (-not ($check | Where-Object { $_.Trim() -eq "/sdcard/Download/$remoteName" })) {
        Stop-WithError 'The APK did not reach the car.'
    }
    Remove-Item -Recurse -Force $tempDir -ErrorAction SilentlyContinue

    Write-Host ''
    Write-Host "   Now, on the car:" -ForegroundColor Yellow
    Write-Info "1. Open a file manager and go to Download (Downloads)."
    Write-Info "2. Tap $remoteName and install it."
    Write-Info "3. Open QuickSettings once."
    while ($true) {
        $null = Read-Host '   Press Enter when done'
        if (Test-AppPresent) { break }
        Write-Warn 'QuickSettings is still not on the car. Finish the steps above, then press Enter.'
    }
    Write-Host '   QuickSettings is on the car.' -ForegroundColor Green
}

# ---------------------------------------------------------------- 4. grants
$TapRelay = "$Pkg/.overlay.TapRelayService"
$TapRelayPattern = [regex]::Escape("$Pkg/") + '(' + [regex]::Escape($Pkg) + ')?' + [regex]::Escape('.overlay.TapRelayService')

function Get-A11yServices {
    $v = ((Invoke-Adb shell settings --user current get secure enabled_accessibility_services) -join '').Trim()
    if ($v -eq 'null') { return '' }
    return $v
}

$grants = @(
    @{
        Name  = 'Draw over other apps (required)'
        Check = { (Invoke-Adb shell appops get $Pkg SYSTEM_ALERT_WINDOW) -match 'SYSTEM_ALERT_WINDOW: allow' }
        Apply = { Invoke-Adb shell appops set $Pkg SYSTEM_ALERT_WINDOW allow }
    },
    @{
        Name  = 'Notification access (required)'
        Check = { (Invoke-Adb shell settings get secure enabled_notification_listeners) -match ([regex]::Escape("$Pkg/") + '\S*QsNotificationListener') }
        Apply = { Invoke-Adb shell cmd notification allow_listener "$Pkg/.nav.QsNotificationListener" }
    },
    @{
        Name  = 'Foreground app / usage stats'
        Check = { (Invoke-Adb shell appops get $Pkg GET_USAGE_STATS) -match 'GET_USAGE_STATS: allow' }
        Apply = { Invoke-Adb shell appops set $Pkg GET_USAGE_STATS allow }
    },
    @{
        Name  = 'Tap pass-through (accessibility service)'
        Check = { (Get-A11yServices) -match $TapRelayPattern }
        Apply = {
            # Append so the car's own accessibility services stay on.
            $current = Get-A11yServices
            $list = if ($current) { "${current}:$TapRelay" } else { $TapRelay }
            Invoke-Adb shell settings --user current put secure enabled_accessibility_services $list
            Invoke-Adb shell settings --user current put secure accessibility_enabled 1
        }
    },
    @{
        Name  = 'Wi-Fi and ADB automation (WRITE_SECURE_SETTINGS)'
        Check = { (Invoke-Adb shell dumpsys package $Pkg) -match 'android\.permission\.WRITE_SECURE_SETTINGS: granted=true' }
        Apply = { Invoke-Adb shell pm grant $Pkg android.permission.WRITE_SECURE_SETTINGS }
    }
)

Write-Step 'Checking permissions'
foreach ($g in $grants) {
    if (& $g.Check) {
        Write-Host ('   [OK]      ' + $g.Name) -ForegroundColor Green
        continue
    }
    $out = & $g.Apply
    if (& $g.Check) {
        Write-Host ('   [APPLIED] ' + $g.Name) -ForegroundColor Green
    } else {
        $script:Failed = $true
        Write-Host ('   [FAILED]  ' + $g.Name) -ForegroundColor Red
        @($out) | Where-Object { $_ } | ForEach-Object { Write-Info "          $_" }
    }
}

Write-Host ''
Write-Info 'Microphone (voice search only): open QuickSettings settings and tap Grant next to Microphone.'
if ($script:Failed) { Stop-WithError 'Some permissions could not be applied. See the README for the manual commands.' }
Write-Host ''
Write-Host 'Done. QuickSettings is set up.' -ForegroundColor Green
exit 0
