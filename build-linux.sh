#!/usr/bin/env bash
# Збирає UWFix для Linux.
#
# Результат у папці dist/:
#   UWFix/                              програма: bin/UWFix + вбудована Java (нічого встановлювати не треба)
#   UWFix-<версія>-linux-x64.tar.gz     та сама папка в архіві (з нього працює самооновлення)
#   uwfix_<версія>_amd64.deb            пакет для Debian/Ubuntu (лише з --deb, потрібні dpkg-deb і fakeroot)
#
# Кроки ті самі, що й у build-installer.ps1 для Windows:
#   1. Maven: компіляція, тести, jar і копіювання залежностей у target/libs
#   2. jpackage (входить у JDK 17+) збирає мінімальне середовище Java через jlink
#   3. tar.gz зберігає права на запуск bin/UWFix (zip їх губить)
#
# Приклади:
#   ./build-linux.sh
#   ./build-linux.sh --skip-tests --deb
set -euo pipefail
cd "$(dirname "$0")"

skip_tests=0
deb=0
for arg in "$@"; do
    case "$arg" in
        --skip-tests) skip_tests=1 ;;
        --deb) deb=1 ;;
        *) echo "Невідомий параметр: $arg" >&2; exit 2 ;;
    esac
done

# Єдине джерело версії — pom.xml (перший тег <version> належить самому проєкту)
version=$(sed -n 's:^    <version>\(.*\)</version>.*:\1:p' pom.xml | head -n 1)
name=UWFix
step() { printf '\n=== %s ===\n' "$1"; }

step "Maven: збирання і тести"
mvn_args=(-B clean package)
if [ "$skip_tests" = 1 ]; then mvn_args+=(-DskipTests); fi
./mvnw "${mvn_args[@]}"

step "Підготовка модулів для jlink"
# jlink приймає лише справжні модулі: jar програми, JavaFX для Linux і Gson
mods=target/jpackage-modules
mkdir -p "$mods"
cp "target/uwfix-$version.jar" "$mods/"
cp target/libs/javafx-*-linux.jar target/libs/gson-*.jar "$mods/"

step "jpackage: програма"
rm -rf dist
jpackage \
    --type app-image \
    --name "$name" \
    --app-version "$version" \
    --vendor UWFix \
    --description "Ultrawide cutscene fix for games" \
    --icon src/main/resources/ua/uwfix/ui/icon.png \
    --module-path "$mods" \
    --module uwfix/ua.uwfix.Main \
    --add-modules jdk.localedata,jdk.crypto.ec \
    --jlink-options "--strip-native-commands --strip-debug --no-man-pages --no-header-files --include-locales=en,uk" \
    --java-options -Dfile.encoding=UTF-8 \
    --dest dist

tar -C dist --owner=0 --group=0 --numeric-owner -czf "dist/$name-$version-linux-x64.tar.gz" "$name"
echo "Програма: dist/$name/bin/$name"
echo "Архів:    dist/$name-$version-linux-x64.tar.gz"

if [ "$deb" = 1 ]; then
    step "jpackage: пакет .deb"
    jpackage \
        --type deb \
        --name "$name" \
        --app-version "$version" \
        --vendor UWFix \
        --description "Ultrawide cutscene fix for games" \
        --app-image "dist/$name" \
        --icon src/main/resources/ua/uwfix/ui/icon.png \
        --linux-package-name uwfix \
        --linux-app-category games \
        --linux-menu-group Game \
        --linux-shortcut \
        --dest dist
    ls dist/*.deb
fi
