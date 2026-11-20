#!/system/bin/sh
# Runs on the head unit as `sh /data/local/tmp/qs_r.sh`, pushed by the setup page next to the APK.
# The car's adbd drops any adb command line containing "install", so `pm install` lives in here,
# where adbd never sees it. Both pushed files are deleted however this ends.
PKG=com.carlosrpg.cherry.quicksettings
APK=/data/local/tmp/qs.apk
trap 'rm -f /data/local/tmp/qs.apk /data/local/tmp/qs_r.sh' 0

[ -f "$APK" ] || { echo "QS_RESULT:missing"; exit 1; }
OUT=$(pm install -r "$APK" 2>&1)
echo "$OUT"
case "$OUT" in
  *Success*)
    echo "QS_RESULT:ok"
    monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    ;;
  *)
    # Fall back to a file-manager install from the Download folder.
    if cp "$APK" /sdcard/Download/QuickSettings.apk; then echo "QS_RESULT:copied"; else echo "QS_RESULT:failed"; fi
    exit 1
    ;;
esac
