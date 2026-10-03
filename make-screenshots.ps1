<#
.SYNOPSIS
    Робить знімки екрана UWFix для документації (docs\screenshots\en, docs\screenshots\uk).

.DESCRIPTION
    Створює демо-ігри з синтетичними файлами в target\demo (справжні ігри не змінюються)
    і запускає програму в демо-режимі: --demo (лаунчери не опитуються, у списку лише демо-ігри),
    --snapshot, --select, --action. Стан програми для демо зберігається в target\demo-home.
#>
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

& .\mvnw.cmd -B -q package -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'Помилка збирання' }

$demoRoot = Join-Path $PSScriptRoot 'target\demo'
$demoHome = Join-Path $PSScriptRoot 'target\demo-home'

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

function New-DemoGames {
    if (Test-Path $demoRoot) { Remove-Item -Recurse -Force $demoRoot }
    # Unreal Engine 4/5 — головний приклад
    New-DemoBinary 'Demo Ultra Game\DemoGame.exe' 300000 0 0
    New-DemoBinary 'Demo Ultra Game\DemoGame\Binaries\Win64\DemoGame-Win64-Shipping.exe' 24000000 31 2
    New-DemoBinary 'Demo Ultra Game\DemoGame\Binaries\Win64\DemoGameLauncher.exe' 2000000 4 0
    New-DemoBinary 'Demo Ultra Game\DemoGame\Binaries\Win64\GameplayPlugin.dll' 3000000 3 0
    New-DemoBinary 'Demo Ultra Game\Engine\Binaries\Win64\CrashReportClient.exe' 5000000 6 0
    # Unity (IL2CPP)
    New-DemoBinary 'Unity Adventure\UnityAdventure.exe' 650000 0 0
    New-DemoBinary 'Unity Adventure\UnityPlayer.dll' 9000000 2 0
    New-DemoBinary 'Unity Adventure\GameAssembly.dll' 7000000 5 0
    # Рушій на кшталт REDengine
    New-Item -ItemType Directory -Force (Join-Path $demoRoot 'Red Saga\content') | Out-Null
    New-DemoBinary 'Red Saga\bin\x64\redsaga.exe' 12000000 18 0
    New-DemoBinary 'Red Saga\bin\x64_dx12\redsaga.exe' 13000000 19 0
    # Онлайн-гра з античитом
    New-Item -ItemType Directory -Force (Join-Path $demoRoot 'Arena Shooter\EasyAntiCheat') | Out-Null
    New-DemoBinary 'Arena Shooter\Arena\Binaries\Win64\Arena-Win64-Shipping.exe' 8000000 7 0
}

function Reset-DemoState([string]$language) {
    if (Test-Path $demoHome) { Remove-Item -Recurse -Force $demoHome }
    New-Item -ItemType Directory -Force $demoHome | Out-Null
    $games = @('Demo Ultra Game', 'Unity Adventure', 'Red Saga', 'Arena Shooter') |
        ForEach-Object { @{ name = $_; path = (Join-Path $demoRoot $_) } }
    $state = @{ version = 1; language = $language; targetWidth = 3440; targetHeight = 1440;
                manualGames = $games; patches = @() } | ConvertTo-Json -Depth 5
    [System.IO.File]::WriteAllText((Join-Path $demoHome 'state.json'), $state, [System.Text.UTF8Encoding]::new($false))
}

function Snap([string]$file, [string[]]$extra) {
    $version = ([xml](Get-Content -Raw -Encoding UTF8 pom.xml)).project.version
    $javaArgs = @("-Duwfix.home=$demoHome", '--module-path', "target\uwfix-$version.jar;target\libs",
                  '-m', 'uwfix/ua.uwfix.Main', '--demo', "--snapshot=$file") + $extra
    & java @javaArgs
}

foreach ($language in 'en', 'uk') {
    $shots = Join-Path $PSScriptRoot "docs\screenshots\$language"
    New-Item -ItemType Directory -Force $shots | Out-Null
    New-DemoGames
    Reset-DemoState $language
    Snap (Join-Path $shots '01-main.png') @()
    Snap (Join-Path $shots '02-analysis.png') @('--select=Demo Ultra')
    Snap (Join-Path $shots '03-fixed.png') @('--select=Demo Ultra', '--action=fix')
    Snap (Join-Path $shots '04-restored.png') @('--select=Demo Ultra', '--action=restore')
    Snap (Join-Path $shots '05-anticheat.png') @('--select=Arena')
}
Write-Host 'Знімки збережено в docs\screenshots' -ForegroundColor Green
