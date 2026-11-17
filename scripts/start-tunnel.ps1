# Opens a Cloudflare quick tunnel to the local backend and points Netlify's /api proxy at it.
# - The backend must already be running (cd backend; .\gradlew.bat bootRun).
# - A quick tunnel gets a NEW random address every time, so netlify.toml must be updated each run.
# Usage (from the repository root):
#   .\scripts\start-tunnel.ps1          -> start the tunnel and write the address into netlify.toml
#   .\scripts\start-tunnel.ps1 -Push    -> also git commit + push netlify.toml (Netlify redeploys in about 1 minute)
# Keep this window open. Closing it (or Ctrl+C) stops the tunnel and the Netlify site loses its backend.
# (This file is ASCII only so Windows PowerShell 5.1 reads it correctly.)
param([int]$Port = 8080, [switch]$Push)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$toml = Join-Path $root 'netlify.toml'
$log = Join-Path $env:TEMP "giltter_tunnel_$PID.log"

try { Invoke-WebRequest "http://localhost:$Port/api/health" -UseBasicParsing -TimeoutSec 5 | Out-Null }
catch { Write-Host "Backend is not answering on port $Port. Start it first: cd backend; .\gradlew.bat bootRun" -ForegroundColor Red; exit 1 }

$tunnel = Start-Process cloudflared -ArgumentList @('tunnel', '--url', "http://localhost:$Port") -NoNewWindow -PassThru -RedirectStandardError $log
$url = $null
for ($i = 0; $i -lt 60 -and -not $url; $i++) {
  Start-Sleep -Seconds 1
  if (Test-Path $log) {
    $m = Select-String -Path $log -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' | Select-Object -First 1
    if ($m) { $url = $m.Matches[0].Value }
  }
}
if (-not $url) { Write-Host "Could not read the tunnel address. Log: $log" -ForegroundColor Red; Stop-Process -Id $tunnel.Id -ErrorAction SilentlyContinue; exit 1 }
Write-Host "Tunnel address: $url" -ForegroundColor Green

# Replace the backend address in the /api proxy rule of netlify.toml (UTF-8 without BOM).
$text = [IO.File]::ReadAllText($toml)
$new = [regex]::Replace($text, '(?m)^(\s*to = ")https://[^"/]+(/api/:splat")', "`${1}$url`${2}")
if ($new -eq $text) {
  if ($text -match [regex]::Escape("$url/api/:splat")) { Write-Host 'netlify.toml already has this address.' }
  else { Write-Host 'Could not find the /api rule in netlify.toml.' -ForegroundColor Red; exit 1 }
} else {
  [IO.File]::WriteAllText($toml, $new, (New-Object Text.UTF8Encoding $false))
  Write-Host 'netlify.toml updated.'
}

if ($Push) {
  Push-Location $root
  git add netlify.toml
  git commit -m "Netlify: point /api at the new backend tunnel address"
  git push
  Pop-Location
  Write-Host 'Pushed. Netlify will redeploy in about 1 minute.' -ForegroundColor Green
} else {
  Write-Host 'Next: git add netlify.toml; git commit -m "Netlify: new tunnel address"; git push'
}

Write-Host "Tunnel is running. Keep this window open. (Ctrl+C to stop)"
Wait-Process -Id $tunnel.Id
