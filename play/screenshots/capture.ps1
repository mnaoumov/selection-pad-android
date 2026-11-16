<#
.SYNOPSIS
  Takes the Play listing's screenshots on one of the rig's emulator profiles.

.DESCRIPTION
  Two screens, the same on every profile:

    <profile>-1-chrome.png    the pad over Chrome, on page.html, with a phrase selected
    <profile>-2-launcher.png  the app's own screen, with the pad docked

  The profile's emulator must be up, with the debug APK installed and Chrome's first run already
  clicked through (with the service off, because the pad covers its buttons). See play/README.md.

  page.html is served from this folder over `adb reverse`, because the guest has no network.
#>
[CmdletBinding()]
param(
    [ValidateSet('rig', 'handset', 'tablet7', 'tablet10')]
    [string] $RigProfile = 'tablet7',

    [int] $Port = 8766
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$here = $PSScriptRoot
$rig = $here | Split-Path -Parent | Split-Path -Parent | Join-Path -ChildPath 'scripts\rig.ps1'
$ports = @{ rig = 5570; handset = 5572; tablet7 = 5574; tablet10 = 5576 }
$serial = "emulator-$($ports[$RigProfile])"
$appId = 'dev.mnaoumov.selectionpad'
$service = "$appId/$appId.AscAccessibilityService"

function Invoke-Adb {
    $out = & adb -s $serial @args
    if ($LASTEXITCODE -ne 0) { throw "adb $($args -join ' ') failed: $out" }
    $out
}

function Wait-Focus([string] $Pattern, [int] $Seconds = 180) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        $focus = (Invoke-Adb shell 'dumpsys window | grep mCurrentFocus') -join ' '
        if ($focus -match $Pattern) { return }
        if ($focus -match 'Not Responding') {
            Write-Host -Object "An ANR dialog has the focus ($focus); answering Wait."
            Invoke-Adb shell input keyevent KEYCODE_ENTER | Out-Null
        }
        Start-Sleep -Seconds 3
    }
    throw "Nothing matching '$Pattern' took the focus within $Seconds s."
}

function Set-Service([bool] $On) {
    $list = "$(Invoke-Adb shell settings get secure enabled_accessibility_services)".Trim()
    $others = @($list -split ':' | Where-Object { $_ -and $_ -ne 'null' -and $_ -notlike "$appId/*" })
    $new = if ($On) { $others + $service } else { $others }
    $value = if ($new.Count -gt 0) { $new -join ':' } else { '""' }
    Invoke-Adb shell settings put secure enabled_accessibility_services $value | Out-Null
    Invoke-Adb shell settings put secure accessibility_enabled $(if ($On) { 1 } else { 0 }) | Out-Null
}

# The page server, unless one is already answering.
$server = $null
try {
    $null = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/page.html" -TimeoutSec 3
} catch {
    $script = $here | Join-Path -ChildPath 'serve.ps1'
    $server = Start-Process -FilePath 'pwsh' -ArgumentList @('-NoProfile', '-File', $script, '-Root', $here, '-Port', "$Port") -WindowStyle Hidden -PassThru
    Start-Sleep -Seconds 3
}

try {
    $density = [int] ("$(Invoke-Adb shell wm density)" -replace '.*?(\d+)\s*$', '$1')
    Invoke-Adb reverse "tcp:$Port" "tcp:$Port" | Out-Null

    # The page is located with the service off: a uiautomator dump unbinds every accessibility
    # service, and the pad would cover Chrome's own first-run prompts if there were any.
    Set-Service -On $false
    Invoke-Adb shell am force-stop com.android.chrome | Out-Null
    Invoke-Adb shell am start -a android.intent.action.VIEW -d "http://127.0.0.1:$Port/page.html" -p com.android.chrome | Out-Null
    Wait-Focus -Pattern 'ChromeTabbedActivity'
    Start-Sleep -Seconds 8

    Invoke-Adb shell uiautomator dump /sdcard/listing-dump.xml | Out-Null
    $xml = (Invoke-Adb exec-out cat /sdcard/listing-dump.xml) -join ''
    $node = [regex]::Match($xml, 'text="A selection handle sits[^"]*"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if (-not $node.Success) { throw 'page.html''s second paragraph is not in the dump; is the page on screen?' }
    $left, $top, $right = [int] $node.Groups[1].Value, [int] $node.Groups[2].Value, [int] $node.Groups[3].Value
    # The first line's middle, about two fifths along it: one line is 1.6 x 19 CSS px.
    $lineHeight = 1.6 * 19 * $density / 160
    $x = [int] ($left + 0.4 * ($right - $left))
    $y = [int] ($top + $lineHeight / 2)

    Set-Service -On $true
    & $rig pad -RigProfile $RigProfile | Out-Null
    Wait-Focus -Pattern 'ChromeTabbedActivity'

    Invoke-Adb shell input swipe $x $y $x $y 900 | Out-Null
    Start-Sleep -Seconds 3
    & $rig press '⇥/word' -RigProfile $RigProfile | Out-Null
    & $rig press '⇥/word' -RigProfile $RigProfile | Out-Null
    Start-Sleep -Seconds 2
    & $rig shot ($here | Join-Path -ChildPath "$RigProfile-1-chrome.png") -RigProfile $RigProfile

    Invoke-Adb shell am start -n "$appId/.MainActivity" | Out-Null
    Wait-Focus -Pattern 'MainActivity'
    Start-Sleep -Seconds 4
    & $rig shot ($here | Join-Path -ChildPath "$RigProfile-2-launcher.png") -RigProfile $RigProfile
} finally {
    if ($server) { Stop-Process -Id $server.Id -ErrorAction SilentlyContinue }
}
