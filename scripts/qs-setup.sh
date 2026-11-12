#!/system/bin/sh
# Runs on the head unit (pushed by Install-OnCar.ps1). The car's adbd drops any command line
# that contains "install", so the package manager is called from here instead.
# Usage: sh /data/local/tmp/qs-setup.sh [tap]
set -e
PKG=com.carlosrpg.cherry.quicksettings
APK=/data/local/tmp/QuickSettings.apk

pm install -r -g "$APK"
# Media sessions and navigation directions; the app can also open this page itself.
cmd notification allow_listener "$PKG/$PKG.nav.QsNotificationListener" || true
if [ "$1" = "tap" ]; then
    # Optional: replay taps that land in the edge swipe zone onto the app behind it.
    settings --user current put secure enabled_accessibility_services "$PKG/$PKG.overlay.TapRelayService"
    settings --user current put secure accessibility_enabled 1
fi
am start -n "$PKG/.settings.SettingsActivity"
rm -f "$APK"
echo "QuickSettings ready"
