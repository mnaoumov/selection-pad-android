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

    [int] $Port = 8766,

    # The host port forwarded to Chrome's DevTools socket while the page is measured.
    [int] $DevToolsPort = 9333
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
        } elseif ($focus -match 'ComponentSystemUIDialog') {
            # The app offers its Quick Settings tile once, on the first launch after the service has
            # run; Back answers SystemUI's dialog with "Don't add tile".
            Write-Host -Object "SystemUI's dialog has the focus ($focus); answering Back."
            Invoke-Adb shell input keyevent KEYCODE_BACK | Out-Null
        }
        Start-Sleep -Seconds 3
    }
    throw "Nothing matching '$Pattern' took the focus within $Seconds s."
}

function Set-Service([bool] $On) {
    $list = "$(Invoke-Adb shell settings get secure enabled_accessibility_services)".Trim()
    $others = @($list -split ':' | Where-Object { $_ -and $_ -ne 'null' -and $_ -notlike "$appId/*" })
    # @() because an `if` that yields an empty array yields $null, which has no Count under StrictMode.
    $new = @(if ($On) { $others + $service } else { $others })
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

function Set-DemoMode([bool] $On) {
    # SystemUI's demo mode: a fixed clock, a full battery and no notification icons in the shots.
    if (-not $On) {
        Invoke-Adb shell am broadcast -a com.android.systemui.demo -e command exit | Out-Null
        return
    }
    Invoke-Adb shell settings put global sysui_demo_allowed 1 | Out-Null
    foreach ($extras in @(
        @('-e', 'command', 'enter'),
        @('-e', 'command', 'clock', '-e', 'hhmm', '1200'),
        @('-e', 'command', 'battery', '-e', 'level', '100', '-e', 'plugged', 'false'),
        @('-e', 'command', 'network', '-e', 'wifi', 'show', '-e', 'level', '4'),
        @('-e', 'command', 'network', '-e', 'mobile', 'hide'),
        @('-e', 'command', 'notifications', '-e', 'visible', 'false')
    )) {
        Invoke-Adb shell am broadcast -a com.android.systemui.demo @extras | Out-Null
    }
}

function Get-WordRect([string] $Word, [string] $Paragraph) {
    # The first line box of $Word inside the <p> that starts with $Paragraph, in CSS px from the
    # viewport's top left, plus devicePixelRatio, read through Chrome's DevTools socket.
    Invoke-Adb forward "tcp:$DevToolsPort" localabstract:chrome_devtools_remote | Out-Null
    try {
        $pages = Invoke-RestMethod -Uri "http://127.0.0.1:$DevToolsPort/json" -TimeoutSec 10
        $target = @($pages | Where-Object -FilterScript { $_.type -eq 'page' -and $_.url -like '*/page.html' })
        if ($target.Count -ne 1) { throw "Expected one page.html tab over DevTools, found $($target.Count)." }
        $expression = @"
(() => {
  const p = [...document.querySelectorAll('p')].find((e) => e.textContent.startsWith('$Paragraph'));
  const node = [...p.childNodes].find((n) => n.nodeType === 3 && n.data.includes('$Word'));
  const range = document.createRange();
  const at = node.data.indexOf('$Word');
  range.setStart(node, at);
  range.setEnd(node, at + '$Word'.length);
  const r = range.getClientRects()[0];
  return JSON.stringify({ left: r.left, top: r.top, right: r.right, bottom: r.bottom, dpr: devicePixelRatio });
})()
"@
        $socket = [System.Net.WebSockets.ClientWebSocket]::new()
        $socket.ConnectAsync([uri] $target[0].webSocketDebuggerUrl, [Threading.CancellationToken]::None).Wait()
        $request = @{ id = 1; method = 'Runtime.evaluate'; params = @{ expression = $expression; returnByValue = $true } } | ConvertTo-Json -Depth 4 -Compress
        $bytes = [Text.Encoding]::UTF8.GetBytes($request)
        $socket.SendAsync([ArraySegment[byte]]::new($bytes), 'Text', $true, [Threading.CancellationToken]::None).Wait()
        $buffer = [byte[]]::new(65536)
        $text = ''
        do {
            $received = $socket.ReceiveAsync([ArraySegment[byte]]::new($buffer), [Threading.CancellationToken]::None).Result
            $text += [Text.Encoding]::UTF8.GetString($buffer, 0, $received.Count)
        } while (-not $received.EndOfMessage)
        $socket.Dispose()
        $result = ($text | ConvertFrom-Json).result
        if ($result.PSObject.Properties.Name -contains 'exceptionDetails') { throw "The page could not measure '$Word': $($result.exceptionDetails.text)" }
        return ($result.result.value | ConvertFrom-Json)
    } finally {
        Invoke-Adb forward --remove "tcp:$DevToolsPort" | Out-Null
    }
}

try {
    Set-DemoMode -On $true
    Invoke-Adb reverse "tcp:$Port" "tcp:$Port" | Out-Null

    # The page is located with the service off: a uiautomator dump unbinds every accessibility
    # service, and the pad would cover Chrome's own first-run prompts if there were any.
    Set-Service -On $false
    Invoke-Adb shell am force-stop com.android.chrome | Out-Null
    Invoke-Adb shell am start -a android.intent.action.VIEW -d "http://127.0.0.1:$Port/page.html" -p com.android.chrome | Out-Null
    Wait-Focus -Pattern 'ChromeTabbedActivity'
    Start-Sleep -Seconds 8

    # Every run opens a new tab, and the tablet's tab strip is in the shot. The page's tab is the
    # newest, the last in the strip, so select the first with Ctrl+1 and close it with Ctrl+W until
    # the toolbar's tab count says one is left.
    foreach ($try in 1..20) {
        Invoke-Adb shell uiautomator dump /sdcard/listing-dump.xml | Out-Null
        $tabs = [regex]::Match(((Invoke-Adb exec-out cat /sdcard/listing-dump.xml) -join ''), 'See (\d+) tabs')
        if (-not $tabs.Success -or [int] $tabs.Groups[1].Value -le 1) { break }
        Invoke-Adb shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_1 | Out-Null
        Start-Sleep -Seconds 1
        Invoke-Adb shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_W | Out-Null
        Start-Sleep -Seconds 2
    }

    # The word to long-press, "under" in the second paragraph, is measured by the page itself over
    # Chrome's DevTools socket: Chrome often leaves its web content out of a uiautomator dump
    # (tablet10, 2026-11-18, six dumps over 24 s). The page's top on screen is the bottom of
    # Chrome's toolbar, which the dump always has.
    $xml = (Invoke-Adb exec-out cat /sdcard/listing-dump.xml) -join ''
    $toolbar = [regex]::Match($xml, 'resource-id="com\.android\.chrome:id/control_container"[^>]*?bounds="\[\d+,\d+\]\[\d+,(\d+)\]"')
    if (-not $toolbar.Success) { throw 'Chrome''s toolbar (control_container) is not in the dump.' }
    $word = Get-WordRect -Word 'under' -Paragraph 'A selection handle sits'
    $x = [int] (($word.left + $word.right) / 2 * $word.dpr)
    $y = [int] ([int] $toolbar.Groups[1].Value + ($word.top + $word.bottom) / 2 * $word.dpr)

    Set-Service -On $true
    & $rig pad -RigProfile $RigProfile | Out-Null
    Wait-Focus -Pattern 'ChromeTabbedActivity'

    Invoke-Adb shell input swipe $x $y $x $y 900 | Out-Null
    Start-Sleep -Seconds 3
    # One press at a time: a press takes seconds, and a touch on the pad while it runs stops it.
    foreach ($press in 1..2) {
        $done = @(Invoke-Adb logcat -d -s ASC | Select-String -Pattern 'WORD_RIGHT -> ').Count
        & $rig press '⇥/word' -RigProfile $RigProfile | Out-Null
        $deadline = (Get-Date).AddSeconds(60)
        while (@(Invoke-Adb logcat -d -s ASC | Select-String -Pattern 'WORD_RIGHT -> ').Count -le $done) {
            if ((Get-Date) -gt $deadline) { throw "Press $press of '⇥/word' did not finish within 60 s." }
            Start-Sleep -Seconds 1
        }
    }
    Start-Sleep -Seconds 2
    & $rig shot ($here | Join-Path -ChildPath "$RigProfile-1-chrome.png") -RigProfile $RigProfile

    Invoke-Adb shell am start -n "$appId/.MainActivity" | Out-Null
    Wait-Focus -Pattern 'MainActivity'
    Start-Sleep -Seconds 4
    & $rig shot ($here | Join-Path -ChildPath "$RigProfile-2-launcher.png") -RigProfile $RigProfile
} finally {
    Set-DemoMode -On $false
    if ($server) { Stop-Process -Id $server.Id -ErrorAction SilentlyContinue }
}
