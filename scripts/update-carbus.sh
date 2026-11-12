#!/usr/bin/env bash
# Rebuilds the two jconfig-carbus AARs from a checkout and replaces the ones in app/libs:
# carbus (the Kotlin layer) and vdbus-client (the vendor client classes it talks through).
# Both must ship: the head unit has no copy of the vendor client for apps to load.
# Usage: scripts/update-carbus.sh [path-to-jconfig-carbus]   (default: ../jconfig-carbus)
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
carbus="$(cd "${1:-$root/../jconfig-carbus}" && pwd)"
version="$(grep '^VERSION_NAME=' "$carbus/gradle.properties" | cut -d= -f2 | tr -d '\r')"

(cd "$carbus" && ./gradlew :carbus:assembleRelease :vdbus-client:assembleRelease -q)

for module in carbus vdbus-client; do
    rm -f "$root"/app/libs/$module-*.aar
    cp "$carbus/$module/build/outputs/aar/$module-release.aar" "$root/app/libs/$module-$version.aar"
    sed -i -E "s#libs/$module-[^\"]+\.aar#libs/$module-$version.aar#" "$root/app/build.gradle.kts"
done

commit="$(git -C "$carbus" rev-parse --short HEAD)"
echo "app/libs/{carbus,vdbus-client}-$version.aar built from jconfig-carbus $commit"
