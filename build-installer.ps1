<#
.SYNOPSIS
    Збирає UWFix для поширення.

.DESCRIPTION
    Результат у папці dist\:
      UWFix\                       портативна версія: UWFix.exe + вбудована Java (нічого встановлювати не треба)
      UWFix-<версія>-portable.zip  та сама папка в архіві
      UWFix-<версія>.exe           інсталятор Windows (потрібен WiX Toolset 3 у tools\wix або в PATH)

    Кроки:
      1. Maven: компіляція, тести, jar і копіювання залежностей у target\libs
      2. Іконка .ico малюється самою програмою (--render-icon)
      3. jpackage (входить у JDK) збирає мінімальне середовище Java через jlink
         лише з потрібних модулів і створює UWFix.exe
      4. jpackage + WiX створюють інсталятор

.EXAMPLE
    .\build-installer.ps1
    .\build-installer.ps1 -SkipTests -PortableOnly
#>
param(
    [switch]$SkipTests,
    [switch]$PortableOnly
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$version = '1.0.0'
$name = 'UWFix'
# Постійний ідентифікатор: завдяки йому нова версія інсталятора оновлює стару, а не ставиться поруч
$upgradeUuid = '6f3c2a7e-9b1d-4e52-8a47-2c1f0d9e5b31'

function Step($text) { Write-Host "`n=== $text ===" -ForegroundColor Cyan }

Step 'Maven: збирання і тести'
$mvnArgs = @('-B', 'clean', 'package')
if ($SkipTests) { $mvnArgs += '-DskipTests' }
& .\mvnw.cmd @mvnArgs
if ($LASTEXITCODE -ne 0) { throw 'Помилка збирання Maven' }

Step 'Підготовка модулів для jlink'
# jlink приймає лише справжні модулі: беремо jar програми, JavaFX для Windows і Gson
$mods = 'target\jpackage-modules'
New-Item -ItemType Directory -Force $mods | Out-Null
Copy-Item "target\uwfix-$version.jar" $mods
Get-ChildItem target\libs -Filter 'javafx-*-win.jar' | Copy-Item -Destination $mods
Get-ChildItem target\libs -Filter 'gson-*.jar' | Copy-Item -Destination $mods

Step 'Іконка'
$ico = 'target\UWFix.ico'
& java --module-path $mods -m uwfix/ua.uwfix.Main "--render-icon=$ico"
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $ico)) { throw 'Не вдалося створити іконку' }

Step 'jpackage: портативна версія'
if (Test-Path dist) { Remove-Item -Recurse -Force dist }
$appArgs = @(
    '--type', 'app-image',
    '--name', $name,
    '--app-version', $version,
    '--vendor', 'UWFix',
    '--description', 'Виправлення чорних смуг у катсценах на ультрашироких моніторах',
    '--icon', $ico,
    '--module-path', $mods,
    '--module', 'uwfix/ua.uwfix.Main',
    # jdk.localedata — правильне сортування українських назв
    '--add-modules', 'jdk.localedata',
    '--jlink-options', '--strip-native-commands --strip-debug --no-man-pages --no-header-files --include-locales=en,uk',
    '--java-options', '-Dfile.encoding=UTF-8',
    '--dest', 'dist'
)
& jpackage @appArgs
if ($LASTEXITCODE -ne 0) { throw 'Помилка jpackage (app-image)' }
Compress-Archive -Path "dist\$name" -DestinationPath "dist\$name-$version-portable.zip" -Force
Write-Host "Портативна версія: dist\$name\$name.exe" -ForegroundColor Green

if ($PortableOnly) { return }

Step 'jpackage: інсталятор'
$wix = Join-Path $PSScriptRoot 'tools\wix'
if (Test-Path (Join-Path $wix 'candle.exe')) {
    $env:PATH = "$wix;$env:PATH"
}
if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
    Write-Warning 'WiX Toolset 3 не знайдено — інсталятор пропущено. Розпакуй wix314-binaries.zip у tools\wix.'
    return
}
$installerArgs = @(
    '--type', 'exe',
    '--name', $name,
    '--app-version', $version,
    '--vendor', 'UWFix',
    # База MSI має кодову сторінку 1252, тож опис інсталятора — латиницею (помилка WiX LGHT0311)
    '--description', 'Ultrawide cutscene fix for games',
    '--app-image', "dist\$name",
    '--win-upgrade-uuid', $upgradeUuid,
    '--win-per-user-install',
    '--win-dir-chooser',
    '--win-menu',
    '--win-menu-group', $name,
    '--win-shortcut',
    '--dest', 'dist'
)
& jpackage @installerArgs
if ($LASTEXITCODE -ne 0) { throw 'Помилка jpackage (інсталятор)' }
Write-Host "Інсталятор: dist\$name-$version.exe" -ForegroundColor Green
