# AI accuracy check: sends every photo under <Root>\<EXPECTED>\ to a running backend's /api/analyze.
# EXPECTED is a label folder (CROSSWALK, SIDEWALK, HYDRANT, OTHER, NORMAL) or NOCAR (correct = LOW confidence).
# Photos are resized like the app does (long side 1024px, JPEG 0.85) before sending.
# Usage (backend running on 8080):  .\scripts\evaluate-ai.ps1 -Root <photo folder> -Port 8080
# (This file is ASCII only so Windows PowerShell 5.1 reads it correctly.)
param([Parameter(Mandatory = $true)][string]$Root, [int]$Port = 8080, [string]$Tag = '')
Add-Type -AssemblyName System.Drawing
$tmp = Join-Path $env:TEMP 'evaluate_ai_resized.jpg'
$jpeg = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
$params = New-Object System.Drawing.Imaging.EncoderParameters 1
$params.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality), 85L

$rows = @()
foreach ($dir in Get-ChildItem $Root -Directory | Sort-Object Name) {
  foreach ($file in Get-ChildItem $dir.FullName -File | Where-Object { $_.Extension -match '\.(jpe?g|png|webp)$' } | Sort-Object Name) {
    $img = [System.Drawing.Image]::FromFile($file.FullName)
    $scale = [Math]::Min(1.0, 1024 / [Math]::Max($img.Width, $img.Height))
    $bmp = New-Object System.Drawing.Bitmap ([int]($img.Width * $scale)), ([int]($img.Height * $scale))
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = 'HighQualityBicubic'
    $g.DrawImage($img, 0, 0, $bmp.Width, $bmp.Height)
    $bmp.Save($tmp, $jpeg, $params); $g.Dispose(); $bmp.Dispose(); $img.Dispose()

    $sw = [Diagnostics.Stopwatch]::StartNew()
    $raw = curl.exe -s -F "image=@$tmp;type=image/jpeg" "http://localhost:$Port/api/analyze" --max-time 90
    $sw.Stop()
    try {
      $r = [Text.Encoding]::UTF8.GetString([Text.Encoding]::Default.GetBytes($raw)) | ConvertFrom-Json
      $level = if ($r.confidence -ge 0.7) { 'HIGH' } elseif ($r.confidence -ge 0.4) { 'MEDIUM' } else { 'LOW' }
      $expected = $dir.Name
      $ok = if ($expected -eq 'NOCAR') { $level -eq 'LOW' } else { $r.label -eq $expected -and $level -ne 'LOW' }
      $chk = if ($r.checks) { ($r.checks.PSObject.Properties | ForEach-Object { "$($_.Name.Substring(0,3))=$($_.Value)" }) -join ' ' } else { 'no-checks' }
      $rows += [pscustomobject]@{ ok = $(if ($ok) { 'O' } else { 'X' }); expected = $expected; got = "$($r.label)/$level $([Math]::Round($r.confidence,2))"; sec = [Math]::Round($sw.Elapsed.TotalSeconds, 1); file = $file.BaseName; checks = $chk }
    } catch {
      $rows += [pscustomobject]@{ ok = 'X'; expected = $dir.Name; got = 'ERROR'; sec = [Math]::Round($sw.Elapsed.TotalSeconds, 1); file = $file.BaseName; checks = "$raw" }
    }
  }
}
$rows | Format-Table ok, expected, got, sec, file, checks -AutoSize | Out-String -Width 260
$correct = ($rows | Where-Object ok -eq 'O').Count
"[$Tag] correct $correct / $($rows.Count), avg " + [Math]::Round(($rows | Measure-Object sec -Average).Average, 1) + "s"
