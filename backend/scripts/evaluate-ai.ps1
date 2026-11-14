# AI accuracy check: sends every photo under <Root>\<EXPECTED>\ to a running backend's /api/analyze.
# EXPECTED is a label folder (CROSSWALK, SIDEWALK, HYDRANT, OTHER, NORMAL) or NOCAR (correct = LOW confidence).
# Photos are resized like the app does (long side 1024px, JPEG 0.85) before sending.
# Correct = the label equals the folder name AND the confidence level is not LOW (below 0.4 the app asks to retake).
# Usage (backend running on 8080):  .\scripts\evaluate-ai.ps1 -Root <photo folder> -Port 8080 [-Out result.csv]
# (This file is ASCII only so Windows PowerShell 5.1 reads it correctly.)
param([Parameter(Mandatory = $true)][string]$Root, [int]$Port = 8080, [string]$Tag = '', [string]$Out = '')
Add-Type -AssemblyName System.Drawing
$tmp = Join-Path $env:TEMP "evaluate_ai_resized_$Port.jpg"
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

    $expected = $dir.Name
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $raw = curl.exe -s -F "image=@$tmp;type=image/jpeg" "http://localhost:$Port/api/analyze" --max-time 90
    $sw.Stop()
    $sec = [Math]::Round($sw.Elapsed.TotalSeconds, 1)
    try {
      $r = [Text.Encoding]::UTF8.GetString([Text.Encoding]::Default.GetBytes("$raw")) | ConvertFrom-Json
      if (-not $r.label) { throw 'no label' }
      $level = if ($r.confidence -ge 0.7) { 'HIGH' } elseif ($r.confidence -ge 0.4) { 'MEDIUM' } else { 'LOW' }
      $ok = if ($expected -eq 'NOCAR') { $level -eq 'LOW' } else { $r.label -eq $expected -and $level -ne 'LOW' }
      $chk = if ($r.checks) { ($r.checks.PSObject.Properties | ForEach-Object { "$($_.Name.Substring(0,3))=$($_.Value)" }) -join ' ' } else { 'no-checks' }
      $rows += [pscustomobject]@{ ok = $(if ($ok) { 'O' } else { 'X' }); expected = $expected; label = "$($r.label)"; level = $level; confidence = [Math]::Round($r.confidence, 2); got = "$($r.label)/$level $([Math]::Round($r.confidence,2))"; sec = $sec; file = $file.BaseName; checks = $chk; model = "$($r.modelVersion)" }
    } catch {
      $rows += [pscustomobject]@{ ok = 'X'; expected = $expected; label = 'ERROR'; level = ''; confidence = 0; got = 'ERROR'; sec = $sec; file = $file.BaseName; checks = "$raw"; model = '' }
    }
  }
}
$rows | Format-Table ok, expected, got, sec, file, checks -AutoSize | Out-String -Width 260
foreach ($group in $rows | Group-Object expected) {
  $hit = @($group.Group | Where-Object ok -eq 'O').Count
  "{0,-10} {1,3} / {2,3}  {3,5:P0}" -f $group.Name, $hit, $group.Count, ($hit / $group.Count)
}
$correct = @($rows | Where-Object ok -eq 'O').Count
"[$Tag] correct $correct / $($rows.Count), avg " + [Math]::Round(($rows | Measure-Object sec -Average).Average, 1) + "s"
if ($Out) { $rows | Export-Csv -Path $Out -NoTypeInformation -Encoding UTF8 }
