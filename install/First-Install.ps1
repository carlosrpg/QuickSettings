<#
.SYNOPSIS
    First-time setup of QuickSettings on the car's head unit over adb.
.DESCRIPTION
    Finds (or installs) adb, waits for the car, pushes the latest release APK to the car's
    Downloads folder when the app is missing, then applies every adb-only grant that is not
    already in place. Safe to run again: it only applies what is missing.
    Messages are in English or Brazilian Portuguese, following the Windows display language.
.PARAMETER Serial
    adb serial of the car, when more than one device is connected.
.PARAMETER TimeoutMinutes
    How long to wait for the car to appear. Default 10.
.PARAMETER Lang
    Force the message language: en or pt-BR. Default: the Windows display language.
#>
[CmdletBinding()]
param(
    [string]$Serial,
    [int]$TimeoutMinutes = 10,
    [ValidateSet('en', 'pt-BR')][string]$Lang
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'  # the PS 5.1 progress bar slows downloads to a crawl
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

# ---------------------------------------------------------------- strings
$Strings = @{
    'en' = @{
        LookingAdb        = 'Looking for adb'
        AdbMissing        = 'adb not found. Installing Google Platform Tools with winget...'
        NoWinget          = 'winget is not available. Install "App Installer" from the Microsoft Store, or download Android Platform Tools from https://developer.android.com/tools/releases/platform-tools and put adb.exe on PATH, then run this again.'
        AdbNotAfterWinget = 'winget finished but adb.exe was not found. Open a new terminal and run this again.'
        UsingAdb          = 'Using {0}'
        Connecting        = 'Connecting to the car'
        ActionNeeded      = 'ACTION NEEDED'
        ConnectTitle      = 'Connect the car'
        ConnectStep1      = 'On the car, enable ADB (developer / engineering menu).'
        ConnectStep2      = 'Connect the USB cable between this PC and the car.'
        ConnectStep3      = 'If the car asks "Allow USB debugging?", tap Allow.'
        WaitKey           = 'Nothing to press: this continues by itself when the car appears. Ctrl+C cancels.'
        Waiting           = 'Waiting for the car... {0} | adb: {1}'
        NoDevice          = 'no device'
        UnauthTitle       = 'Allow USB debugging on the car'
        UnauthStep1       = 'Look at the car screen: it is asking "Allow USB debugging?".'
        UnauthStep2       = 'Tick "Always allow from this computer" and tap Allow.'
        UnauthStep3       = 'No prompt on the screen? Unplug the USB cable and plug it back in.'
        SeveralTitle      = 'Choose the car'
        SeveralIntro      = 'Several devices are connected (a phone too?). Which one is the car?'
        SeveralKey        = 'Type the number of the car and press Enter.'
        PickPrompt        = 'Number of the car'
        InvalidChoice     = 'Invalid choice.'
        Timeout           = 'No car found within {0} minutes.'
        Connected         = 'Connected: {0}'
        CheckingApp       = 'Checking for QuickSettings on the car'
        AppPresent        = 'QuickSettings is on the car.'
        AppMissing        = 'QuickSettings is not on the car yet. Fetching the latest release...'
        NoGitHub          = 'Could not reach GitHub: {0}'
        NoAsset           = 'Release {0} has no .apk file.'
        Downloading       = 'Downloading {0} ({1})...'
        DownloadFailed    = 'Download failed: {0}'
        Pushing           = "Copying it to the car's Download folder..."
        PushFailed        = 'The APK did not reach the car.'
        InstallTitle      = 'Install QuickSettings on the car'
        InstallStep1      = 'On the car, open a file manager and go to the Download folder.'
        InstallStep2      = 'Tap {0} and install it.'
        InstallStep3      = 'Open QuickSettings once.'
        InstallKey        = 'Then press Enter here. To stop for now, type {0} and press Enter.'
        SkipKey           = 'S'
        EnterPrompt       = 'Enter = done, {0} = stop for now'
        StillMissing      = 'QuickSettings is still not on the car. Finish the steps above, then press Enter.'
        CheckingPerms     = 'Checking permissions'
        SkippedNoApp      = 'Skipped: QuickSettings is not installed yet.'
        MicTitle          = 'Allow the microphone (voice search)'
        MicStep1          = 'On the car, open QuickSettings and go to its settings screen.'
        MicStep2          = 'Tap Grant next to Microphone and accept the prompt.'
        MicKey            = 'Nothing to press here: do it on the car whenever you like.'
        Summary           = 'Summary'
        ErrorPrefix       = 'ERROR: {0}'
        St_OK             = 'OK'
        St_APPLIED        = 'APPLIED'
        St_FAILED         = 'FAILED'
        St_PENDING        = 'PENDING'
        ChkAdb            = 'adb on this PC'
        ChkCar            = 'Car connected over adb'
        ChkApp            = 'QuickSettings app installed'
        ChkOverlay        = 'Draw over other apps (required)'
        ChkNotif          = 'Notification access (required)'
        ChkUsage          = 'Foreground app / usage stats'
        ChkA11y           = 'Tap pass-through (accessibility service)'
        ChkWss            = 'Wi-Fi and ADB automation (WRITE_SECURE_SETTINGS)'
        ChkMic            = 'Microphone (voice search)'
        FinalFailed       = 'NOT DONE: some steps FAILED. Fix them and run this again; the manual commands are in the README.'
        FinalPending      = 'ALMOST DONE. Still to do on the car: {0}.'
        FinalDone         = 'ALL DONE. QuickSettings is set up.'
    }
    'pt-BR' = @{
        LookingAdb        = 'Procurando o adb'
        AdbMissing        = 'adb não encontrado. Instalando o Google Platform Tools com o winget...'
        NoWinget          = 'O winget não está disponível. Instale o "Instalador de Aplicativo" pela Microsoft Store, ou baixe o Android Platform Tools em https://developer.android.com/tools/releases/platform-tools e coloque o adb.exe no PATH; depois rode isto de novo.'
        AdbNotAfterWinget = 'O winget terminou, mas o adb.exe não foi encontrado. Abra um novo terminal e rode isto de novo.'
        UsingAdb          = 'Usando {0}'
        Connecting        = 'Conectando à central multimídia'
        ActionNeeded      = 'AÇÃO NECESSÁRIA'
        ConnectTitle      = 'Conecte a central multimídia'
        ConnectStep1      = 'Na central multimídia, ative o ADB (menu de desenvolvedor / engenharia).'
        ConnectStep2      = 'Ligue o cabo USB entre este PC e o carro.'
        ConnectStep3      = 'Se a central perguntar "Permitir depuração USB?", toque em Permitir.'
        WaitKey           = 'Não precisa apertar nada: o script continua sozinho quando a central aparecer. Ctrl+C cancela.'
        Waiting           = 'Aguardando a central... {0} | adb: {1}'
        NoDevice          = 'nenhum dispositivo'
        UnauthTitle       = 'Permita a depuração USB na central'
        UnauthStep1       = 'Olhe a tela da central: ela está perguntando "Permitir depuração USB?".'
        UnauthStep2       = 'Marque "Sempre permitir neste computador" e toque em Permitir.'
        UnauthStep3       = 'A pergunta não apareceu? Desconecte o cabo USB e conecte de novo.'
        SeveralTitle      = 'Escolha a central multimídia'
        SeveralIntro      = 'Há vários dispositivos conectados (um celular também?). Qual é a central?'
        SeveralKey        = 'Digite o número da central e aperte Enter.'
        PickPrompt        = 'Número da central'
        InvalidChoice     = 'Opção inválida.'
        Timeout           = 'Nenhuma central encontrada em {0} minutos.'
        Connected         = 'Conectado: {0}'
        CheckingApp       = 'Verificando se o QuickSettings está na central'
        AppPresent        = 'O QuickSettings está na central.'
        AppMissing        = 'O QuickSettings ainda não está na central. Baixando a versão mais recente...'
        NoGitHub          = 'Não foi possível acessar o GitHub: {0}'
        NoAsset           = 'A versão {0} não tem um arquivo .apk.'
        Downloading       = 'Baixando {0} ({1})...'
        DownloadFailed    = 'Falha no download: {0}'
        Pushing           = 'Copiando para a pasta Download da central...'
        PushFailed        = 'O APK não chegou à central.'
        InstallTitle      = 'Instale o QuickSettings na central'
        InstallStep1      = 'Na central, abra um gerenciador de arquivos e vá até a pasta Download.'
        InstallStep2      = 'Toque em {0} e instale.'
        InstallStep3      = 'Abra o QuickSettings uma vez.'
        InstallKey        = 'Depois aperte Enter aqui. Para parar por agora, digite {0} e aperte Enter.'
        SkipKey           = 'P'
        EnterPrompt       = 'Enter = pronto, {0} = parar por agora'
        StillMissing      = 'O QuickSettings ainda não está na central. Termine os passos acima e aperte Enter.'
        CheckingPerms     = 'Verificando as permissões'
        SkippedNoApp      = 'Ignorado: o QuickSettings ainda não está instalado.'
        MicTitle          = 'Permita o microfone (busca por voz)'
        MicStep1          = 'Na central, abra o QuickSettings e vá até a tela de configurações.'
        MicStep2          = 'Toque em Permitir ao lado de Microfone e aceite a pergunta.'
        MicKey            = 'Não precisa apertar nada aqui: faça isso na central quando quiser.'
        Summary           = 'Resumo'
        ErrorPrefix       = 'ERRO: {0}'
        St_OK             = 'OK'
        St_APPLIED        = 'APLICADO'
        St_FAILED         = 'FALHOU'
        St_PENDING        = 'PENDENTE'
        ChkAdb            = 'adb neste PC'
        ChkCar            = 'Central conectada pelo adb'
        ChkApp            = 'App QuickSettings instalado'
        ChkOverlay        = 'Sobrepor a outros apps (obrigatório)'
        ChkNotif          = 'Acesso às notificações (obrigatório)'
        ChkUsage          = 'App em primeiro plano / estatísticas de uso'
        ChkA11y           = 'Repasse de toques (serviço de acessibilidade)'
        ChkWss            = 'Automação de Wi-Fi e ADB (WRITE_SECURE_SETTINGS)'
        ChkMic            = 'Microfone (busca por voz)'
        FinalFailed       = 'NÃO CONCLUÍDO: algumas etapas FALHARAM. Corrija e rode isto de novo; os comandos manuais estão no README.'
        FinalPending      = 'QUASE LÁ. Ainda falta fazer na central: {0}.'
        FinalDone         = 'TUDO PRONTO. O QuickSettings está configurado.'
    }
}

if (-not $Lang) {
    $ui = try { (Get-UICulture).Name } catch { '' }
    if (-not $ui) { $ui = (Get-Culture).Name }
    $Lang = if ($ui -like 'pt*') { 'pt-BR' } else { 'en' }
}

# Localized string by id; falls back to English when the pt-BR table lacks the key.
function T([string]$Id) {
    $s = $Strings[$Lang][$Id]
    if ($null -eq $s) { $s = $Strings['en'][$Id] }
    if ($args.Count) { return ($s -f $args) }
    return $s
}

$Pkg = 'com.carlosrpg.cherry.quicksettings'
$Repo = 'carlosrpg/QuickSettings'
$script:Adb = $null
$script:ActionNo = 0
$script:Results = New-Object System.Collections.Generic.List[object]

function Write-Step([string]$Text) { Write-Host ''; Write-Host "== $Text" -ForegroundColor Cyan }
function Write-Info([string]$Text) { Write-Host "   $Text" }
function Write-Warn([string]$Text) { Write-Host "   $Text" -ForegroundColor Yellow }
function Add-Result([string]$CheckId, [string]$Status) {
    $script:Results.Add([pscustomobject]@{ Check = (T $CheckId); Status = $Status })
}
function Invoke-Beep { try { [Console]::Beep(880, 250) } catch { } }

function Split-Words([string]$Text, [int]$Width) {
    $lines = @(); $line = ''
    foreach ($w in ($Text -split ' ')) {
        if ($line -and ($line.Length + 1 + $w.Length) -gt $Width) { $lines += $line; $line = $w }
        elseif ($line) { $line += ' ' + $w }
        else { $line = $w }
    }
    return $lines + $line
}

# A yellow framed block for anything the user has to do, with a beep so it is noticed.
function Show-Action([string]$Title, [string[]]$Steps, [string]$KeyHint) {
    $script:ActionNo++
    $wrap = 72
    $lines = @("$(T 'ActionNeeded') ($script:ActionNo): $Title", '')
    for ($i = 0; $i -lt $Steps.Count; $i++) {
        $first = $true
        foreach ($l in (Split-Words $Steps[$i] ($wrap - 4))) {
            $lines += $(if ($first) { '{0,2}. {1}' -f ($i + 1), $l } else { "    $l" })
            $first = $false
        }
    }
    $lines += ''
    foreach ($l in (Split-Words ">> $KeyHint" $wrap)) { $lines += $l }
    $width = ($lines | ForEach-Object { $_.Length } | Measure-Object -Maximum).Maximum
    $bar = '+' + ('=' * ($width + 2)) + '+'
    Write-Host ''
    Write-Host $bar -ForegroundColor Black -BackgroundColor Yellow
    foreach ($l in $lines) { Write-Host ('| ' + $l.PadRight($width) + ' |') -ForegroundColor Black -BackgroundColor Yellow }
    Write-Host $bar -ForegroundColor Black -BackgroundColor Yellow
    Invoke-Beep
}

function Write-Summary {
    Write-Step (T 'Summary')
    $w = ($script:Results | ForEach-Object { $_.Check.Length } | Measure-Object -Maximum).Maximum
    $colors = @{ OK = 'Green'; APPLIED = 'Green'; FAILED = 'Red'; PENDING = 'Yellow' }
    foreach ($r in $script:Results) {
        Write-Host ('   ' + $r.Check.PadRight($w) + '  ') -NoNewline
        Write-Host (T "St_$($r.Status)") -ForegroundColor $colors[$r.Status]
    }
    Write-Host ''
    $pending = @($script:Results | Where-Object { $_.Status -eq 'PENDING' } | ForEach-Object { $_.Check })
    if ($script:Results | Where-Object { $_.Status -eq 'FAILED' }) {
        Write-Host (T 'FinalFailed') -ForegroundColor Black -BackgroundColor Red
        return 1
    }
    if ($pending.Count) {
        Write-Host (T 'FinalPending' ($pending -join '; ')) -ForegroundColor Black -BackgroundColor Yellow
        return 0
    }
    Write-Host (T 'FinalDone') -ForegroundColor Black -BackgroundColor Green
    return 0
}

function Stop-WithError([string]$Text, [string]$CheckId) {
    Write-Host ''
    Write-Host (T 'ErrorPrefix' $Text) -ForegroundColor Red
    Add-Result $CheckId 'FAILED'
    exit (Write-Summary)
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
Write-Step (T 'LookingAdb')
$script:Adb = Find-Adb
if ($script:Adb) {
    Add-Result 'ChkAdb' 'OK'
} else {
    Write-Warn (T 'AdbMissing')
    if (-not (Get-Command winget.exe -ErrorAction SilentlyContinue)) { Stop-WithError (T 'NoWinget') 'ChkAdb' }
    & winget.exe install --id Google.PlatformTools -e --accept-source-agreements --accept-package-agreements
    # PATH does not refresh inside this session, so look in the known folders again.
    $script:Adb = Find-Adb
    if (-not $script:Adb) { Stop-WithError (T 'AdbNotAfterWinget') 'ChkAdb' }
    Add-Result 'ChkAdb' 'APPLIED'
}
Write-Info (T 'UsingAdb' $script:Adb)

# ---------------------------------------------------------------- 2. device
Write-Step (T 'Connecting')
# Not through Invoke-Adb: the server daemon inherits a captured stdout pipe and the read never
# ends (seen with Chocolatey's adb shim). Run it uncaptured and wait for this process only.
foreach ($cmd in 'kill-server', 'start-server') {
    $psi = New-Object Diagnostics.ProcessStartInfo $script:Adb, $cmd
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $p = [Diagnostics.Process]::Start($psi)
    $null = $p.WaitForExit(20000)
}

$start = Get-Date
$deadline = $start.AddMinutes($TimeoutMinutes)
$shown = @{}
$statusWidth = try { [Console]::WindowWidth - 1 } catch { 79 }
$onStatusLine = $false
function Stop-StatusLine { if ($script:onStatusLine) { Write-Host ''; $script:onStatusLine = $false } }

while ($true) {
    $devices = @(Get-AdbDevices)
    if ($Serial) {
        $mine = @($devices | Where-Object { $_.Serial -eq $Serial })
        if ($mine.Count -and $mine[0].State -eq 'device') { break }
    } else {
        $ready = @($devices | Where-Object { $_.State -eq 'device' })
        if ($ready.Count -eq 1) { $Serial = $ready[0].Serial; break }
        if ($ready.Count -gt 1) {
            Stop-StatusLine
            # The step numbers double as the choice numbers.
            Show-Action (T 'SeveralTitle') @($ready | ForEach-Object { $_.Serial }) ((T 'SeveralIntro') + ' ' + (T 'SeveralKey'))
            $pick = Read-Host "   $(T 'PickPrompt')"
            $n = 0
            if ([int]::TryParse($pick, [ref]$n) -and $n -ge 1 -and $n -le $ready.Count) {
                $Serial = $ready[$n - 1].Serial; break
            }
            Write-Warn (T 'InvalidChoice')
            continue
        }
    }
    $unauth = @($devices | Where-Object { $_.State -eq 'unauthorized' -and (-not $Serial -or $_.Serial -eq $Serial) })
    if ($unauth.Count -and -not $shown['unauth' + $unauth[0].Serial]) {
        $shown['unauth' + $unauth[0].Serial] = $true
        Stop-StatusLine
        Show-Action (T 'UnauthTitle') @((T 'UnauthStep1'), (T 'UnauthStep2'), (T 'UnauthStep3')) (T 'WaitKey')
    } elseif (-not $devices.Count -and -not $shown['connect'] -and ((Get-Date) - $start).TotalSeconds -ge 3) {
        # Give adb a moment to see an already-plugged car before asking for anything.
        $shown['connect'] = $true
        Stop-StatusLine
        Show-Action (T 'ConnectTitle') @((T 'ConnectStep1'), (T 'ConnectStep2'), (T 'ConnectStep3')) (T 'WaitKey')
    }
    if ((Get-Date) -gt $deadline) { Stop-StatusLine; Stop-WithError (T 'Timeout' $TimeoutMinutes) 'ChkCar' }

    $state = if ($devices.Count) { ($devices | ForEach-Object { "$($_.Serial) $($_.State)" }) -join ', ' } else { T 'NoDevice' }
    $elapsed = (Get-Date) - $start
    $line = '   ' + (T 'Waiting' ('{0:00}:{1:00}' -f [int][math]::Floor($elapsed.TotalMinutes), $elapsed.Seconds) $state)
    if ($line.Length -gt $statusWidth) { $line = $line.Substring(0, $statusWidth) }
    Write-Host ("`r" + $line.PadRight($statusWidth)) -NoNewline -ForegroundColor Yellow
    $onStatusLine = $true
    Start-Sleep -Seconds 1
}
Stop-StatusLine
Write-Host ('   ' + (T 'Connected' $Serial)) -ForegroundColor Green
Add-Result 'ChkCar' 'OK'

# ---------------------------------------------------------------- 3. package
function Test-AppPresent {
    return [bool](Invoke-Adb shell pm list packages $Pkg | Where-Object { $_.Trim() -eq "package:$Pkg" })
}

Write-Step (T 'CheckingApp')
$appReady = $true
if (Test-AppPresent) {
    Write-Host ('   ' + (T 'AppPresent')) -ForegroundColor Green
    Add-Result 'ChkApp' 'OK'
} else {
    Write-Warn (T 'AppMissing')
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    $headers = @{ 'User-Agent' = 'QuickSettings-FirstSetup'; 'Accept' = 'application/vnd.github+json' }
    try {
        $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases/latest" -Headers $headers
    } catch {
        Stop-WithError (T 'NoGitHub' $_.Exception.Message) 'ChkApp'
    }
    $asset = @($release.assets | Where-Object { $_.name -like '*.apk' }) | Select-Object -First 1
    if (-not $asset) { Stop-WithError (T 'NoAsset' $release.tag_name) 'ChkApp' }

    $remoteName = $asset.name
    if ($remoteName -match 'install') { $remoteName = 'QuickSettings.apk' }  # adbd drops such lines
    $tempDir = Join-Path ([IO.Path]::GetTempPath()) ('qs-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
    New-Item -ItemType Directory -Path $tempDir | Out-Null
    $localApk = Join-Path $tempDir $remoteName
    Write-Info (T 'Downloading' $asset.name $release.tag_name)
    try {
        Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $localApk -Headers @{ 'User-Agent' = 'QuickSettings-FirstSetup' } -UseBasicParsing
    } catch {
        Stop-WithError (T 'DownloadFailed' $_.Exception.Message) 'ChkApp'
    }

    Write-Info (T 'Pushing')
    $pushOut = Invoke-Adb push $localApk "/sdcard/Download/$remoteName"
    $pushOut | ForEach-Object { Write-Info $_ }
    $check = Invoke-Adb shell ls "/sdcard/Download/$remoteName"
    if (-not ($check | Where-Object { $_.Trim() -eq "/sdcard/Download/$remoteName" })) {
        Stop-WithError (T 'PushFailed') 'ChkApp'
    }
    Remove-Item -Recurse -Force $tempDir -ErrorAction SilentlyContinue

    $skip = T 'SkipKey'
    Show-Action (T 'InstallTitle') @((T 'InstallStep1'), (T 'InstallStep2' $remoteName), (T 'InstallStep3')) (T 'InstallKey' $skip)
    while ($true) {
        $answer = Read-Host "   $(T 'EnterPrompt' $skip)"
        if ($answer.Trim() -eq $skip) { $appReady = $false; break }
        if (Test-AppPresent) { break }
        Write-Warn (T 'StillMissing')
    }
    if ($appReady) {
        Write-Host ('   ' + (T 'AppPresent')) -ForegroundColor Green
        Add-Result 'ChkApp' 'APPLIED'
    } else {
        Add-Result 'ChkApp' 'PENDING'
    }
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
        Id    = 'ChkOverlay'
        Check = { (Invoke-Adb shell appops get $Pkg SYSTEM_ALERT_WINDOW) -match 'SYSTEM_ALERT_WINDOW: allow' }
        Apply = { Invoke-Adb shell appops set $Pkg SYSTEM_ALERT_WINDOW allow }
    },
    @{
        Id    = 'ChkNotif'
        Check = { (Invoke-Adb shell settings get secure enabled_notification_listeners) -match ([regex]::Escape("$Pkg/") + '\S*QsNotificationListener') }
        Apply = { Invoke-Adb shell cmd notification allow_listener "$Pkg/.nav.QsNotificationListener" }
    },
    @{
        Id    = 'ChkUsage'
        Check = { (Invoke-Adb shell appops get $Pkg GET_USAGE_STATS) -match 'GET_USAGE_STATS: allow' }
        Apply = { Invoke-Adb shell appops set $Pkg GET_USAGE_STATS allow }
    },
    @{
        Id    = 'ChkA11y'
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
        Id    = 'ChkWss'
        Check = { (Invoke-Adb shell dumpsys package $Pkg) -match 'android\.permission\.WRITE_SECURE_SETTINGS: granted=true' }
        Apply = { Invoke-Adb shell pm grant $Pkg android.permission.WRITE_SECURE_SETTINGS }
    }
)

Write-Step (T 'CheckingPerms')
if (-not $appReady) {
    Write-Warn (T 'SkippedNoApp')
    foreach ($g in $grants) { Add-Result $g.Id 'PENDING' }
    Add-Result 'ChkMic' 'PENDING'
} else {
    foreach ($g in $grants) {
        $name = T $g.Id
        if (& $g.Check) {
            Write-Host ('   [' + (T 'St_OK') + '] ' + $name) -ForegroundColor Green
            Add-Result $g.Id 'OK'
            continue
        }
        $out = & $g.Apply
        if (& $g.Check) {
            Write-Host ('   [' + (T 'St_APPLIED') + '] ' + $name) -ForegroundColor Green
            Add-Result $g.Id 'APPLIED'
        } else {
            Write-Host ('   [' + (T 'St_FAILED') + '] ' + $name) -ForegroundColor Red
            @($out) | Where-Object { $_ } | ForEach-Object { Write-Info "          $_" }
            Add-Result $g.Id 'FAILED'
        }
    }
    # The microphone is a normal runtime permission: the user grants it from the app (read-only check here).
    if ((Invoke-Adb shell dumpsys package $Pkg) -match 'android\.permission\.RECORD_AUDIO: granted=true') {
        Add-Result 'ChkMic' 'OK'
    } else {
        Show-Action (T 'MicTitle') @((T 'MicStep1'), (T 'MicStep2')) (T 'MicKey')
        Add-Result 'ChkMic' 'PENDING'
    }
}

exit (Write-Summary)
