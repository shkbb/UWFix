<#
.SYNOPSIS
    Робить знімки екрана UWFix для документації (docs\screenshots).

.DESCRIPTION
    Створює демо-гру з синтетичними файлами в target\demo (справжні ігри не змінюються),
    запускає програму в демо-режимі (--snapshot, --select, --action) і зберігає PNG.
    Стан програми для демо зберігається окремо (target\demo-home), а не в %APPDATA%.
#>
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

& .\mvnw.cmd -B -q package -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'Помилка збирання' }

$demoRoot = Join-Path $PSScriptRoot 'target\demo\Demo Ultra Game'
$demoHome = Join-Path $PSScriptRoot 'target\demo-home'
$shots = Join-Path $PSScriptRoot 'docs\screenshots'
if (Test-Path $demoRoot) { Remove-Item -Recurse -Force $demoRoot }
if (Test-Path $demoHome) { Remove-Item -Recurse -Force $demoHome }
New-Item -ItemType Directory -Force $demoHome, $shots | Out-Null

# Синтетичний файл: випадкові байти без 16:9 і задана кількість входжень 16:9 (float і double)
function New-DemoBinary([string]$relative, [int]$size, [int]$floats, [int]$doubles) {
    $path = Join-Path $demoRoot $relative
    New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
    $rnd = [System.Random]::new(42)
    $data = [byte[]]::new($size)
    $rnd.NextBytes($data)
    for ($i = 0; $i -lt $size; $i++) { if ($data[$i] -eq 0x39 -or $data[$i] -eq 0x1C) { $data[$i] = 0 } }
    $f = [BitConverter]::GetBytes([single](16.0 / 9.0))
    $d = [BitConverter]::GetBytes([double](16.0 / 9.0))
    for ($k = 1; $k -le $floats; $k++) { [Array]::Copy($f, 0, $data, [int]($k * $size / ($floats + 2)) -band -4, 4) }
    for ($k = 1; $k -le $doubles; $k++) { [Array]::Copy($d, 0, $data, ([int]($k * $size / ($doubles + 2)) -band -8) + 8, 8) }
    [System.IO.File]::WriteAllBytes($path, $data)
}

New-DemoBinary 'DemoGame.exe' 300000 0 0
New-DemoBinary 'DemoGame\Binaries\Win64\DemoGame-Win64-Shipping.exe' 24000000 31 2
New-DemoBinary 'DemoGame\Binaries\Win64\DemoGameLauncher.exe' 2000000 4 0
New-DemoBinary 'DemoGame\Binaries\Win64\GameplayPlugin.dll' 3000000 3 0
New-DemoBinary 'Engine\Binaries\Win64\CrashReportClient.exe' 5000000 6 0

$state = @{
    version     = 1
    manualGames = @(@{ name = 'Demo Ultra Game (демо)'; path = $demoRoot })
    patches     = @()
} | ConvertTo-Json -Depth 5
[System.IO.File]::WriteAllText((Join-Path $demoHome 'state.json'), $state, [System.Text.UTF8Encoding]::new($false))

$modulePath = 'target\uwfix-1.0.0.jar;target\libs'
function Snap([string]$file, [string[]]$extra) {
    $javaArgs = @("-Duwfix.home=$demoHome", '--module-path', $modulePath, '-m', 'uwfix/ua.uwfix.Main',
              "--snapshot=$(Join-Path $shots $file)") + $extra
    & java @javaArgs
}

Snap '01-main.png' @()
Snap '02-analysis.png' @('--select=Demo')
Snap '03-fixed.png' @('--select=Demo', '--action=fix')
Snap '04-restored.png' @('--select=Demo', '--action=restore')
Write-Host "Знімки збережено в $shots" -ForegroundColor Green
