#!/usr/bin/env bash
# Rebuilds the jconfig-carbus AAR from a checkout and replaces app/libs/carbus-*.aar.
# Usage: scripts/update-carbus.sh [path-to-jconfig-carbus]   (default: ../jconfig-carbus)
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
carbus="$(cd "${1:-$root/../jconfig-carbus}" && pwd)"
version="$(grep '^VERSION_NAME=' "$carbus/gradle.properties" | cut -d= -f2 | tr -d '\r')"

(cd "$carbus" && ./gradlew :carbus:assembleRelease -q)

rm -f "$root"/app/libs/carbus-*.aar
cp "$carbus/carbus/build/outputs/aar/carbus-release.aar" "$root/app/libs/carbus-$version.aar"
sed -i -E "s#libs/carbus-[^\"]+\.aar#libs/carbus-$version.aar#" "$root/app/build.gradle.kts"

commit="$(git -C "$carbus" rev-parse --short HEAD)"
echo "app/libs/carbus-$version.aar built from jconfig-carbus $commit"
