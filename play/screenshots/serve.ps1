# Serves the files in -Root on http://127.0.0.1:<Port>/, for the emulator to reach over
# `adb reverse`. capture.ps1 starts and stops it.
param([string] $Root, [int] $Port = 8766)
$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add("http://127.0.0.1:$Port/")
$listener.Start()
while ($listener.IsListening) {
    $ctx = $listener.GetContext()
    $name = $ctx.Request.Url.AbsolutePath.TrimStart('/')
    $path = Join-Path $Root $name
    if ($name -and (Test-Path -LiteralPath $path -PathType Leaf)) {
        $bytes = [System.IO.File]::ReadAllBytes($path)
        $ctx.Response.ContentType = 'text/html; charset=utf-8'
        $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
    } else { $ctx.Response.StatusCode = 404 }
    $ctx.Response.Close()
}
