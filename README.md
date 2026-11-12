# QuickSettings

A widget drawer that floats over every app on the Jaecoo 7 head unit (Android Automotive 11).
Closed, it is a 6 dp hint on the screen edge. Swipe inward from the edge to open it semi-open:
small square widgets you read at a glance. Open it fully for larger cards and full controls.
English and Brazilian Portuguese.

Style: **Night Glass**, the approved styleguide.

## Widgets

| Widget | Semi-open | Open |
|---|---|---|
| Media | Album art, play/pause. Press and drag left/right: previous/next | Source app (tap to open it), title, progress, transport, voice search sent to the app that owns the session |
| Climate | Temperature × fan quadrant | ± temperature on the vertical axis, ± fan on the horizontal one |
| Car mode | Battery-mode glyph and regen bars. Press and drag: up/down battery mode, left/right regen | Initial / Smart / Forced, hold-target slider when Forced, Low / Medium / High regen |
| Autonomy | Battery ring (outer) and fuel ring (inner), electric km | Electric and fuel range, km and %, total |
| Navigation | Next manoeuvre and distance; with no route, a tap starts a voice destination search | Nav app (tap to open it), next turn, street, ETA, voice destination |
| Assist | Emergency braking, auto hold, lane keeping (read only) | Same, with On/Off |

Media volume: Android Automotive uses fixed volume, and the car's volume groups need a
privileged permission, so the app hides its volume controls there and points to the car's own.

## How it gets its data

- **Car**: CarInfo items through [jconfig-carbus](https://github.com/carlosrpg/jconfig-carbus), bundled as a
  prebuilt AAR in `app/libs/` (no vendor classes in it; the head unit supplies those at runtime).
  CarInfo has no subscribe, and values also change from the stock settings, voice and buttons, so
  the app **polls**: every 1.5 s while the drawer is visible, and again right after each write.
- **Media**: the active `MediaSession`, found through the notification listener.
- **Navigation**: the navigation app's turn-by-turn notification (title, text, arrow).

Nothing writes to the car on its own. Every write comes from a tap, a slider release or a
drag release. Regen and battery mode aren't gated on speed: the stock settings app doesn't gate
them either.

## Install on the car

Download the signed APK from the latest [release](../../releases), then:

```powershell
.\scripts\Install-OnCar.ps1 -Apk .\QuickSettings-<version>.apk
```

The head unit's adbd drops any command line containing the word "install", so the script pushes
the APK plus `scripts/qs-setup.sh` and runs the install on the device. It also turns on
notification access. Add `-TapPassThrough` to enable the optional tap relay too.

### Permissions

| Permission | How it's granted |
|---|---|
| Draw over other apps | At install (the app targets API 22, where this permission is granted to the app) |
| Microphone (voice search) | At install; Android shows a one-time review screen on first launch, so tap **Continue** |
| Notification access (media + navigation) | The install script, or the app's **Grant** button, which opens the car's notification-access page |
| Tap pass-through (optional) | `-TapPassThrough`, or `adb shell settings --user current put secure enabled_accessibility_services com.carlosrpg.cherry.quicksettings/com.carlosrpg.cherry.quicksettings.overlay.TapRelayService` |

On first launch Android also shows a one-time "built for an older version of Android" notice;
that's the cost of the install-time grants. Setting `targetSdk = 30` in `app/build.gradle.kts`
removes it, and then the overlay needs
`adb shell appops set com.carlosrpg.cherry.quicksettings SYSTEM_ALERT_WINDOW allow`.

## Build

Requires JDK 21 and the Android SDK (`local.properties` with `sdk.dir`).

```bash
./gradlew app:testDebugUnitTest app:lintDebug app:assembleDebug   # emulator build, in-memory car
./gradlew app:assembleRelease -Pqs.mock=false                       # head-unit build, signed
```

Release signing reads `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and
`RELEASE_KEY_PASSWORD` from Gradle properties, `local.properties` or the environment.

To update the bundled library from a jconfig-carbus checkout:

```bash
scripts/update-carbus.sh ../jconfig-carbus
```

## CI

`.github/workflows/ci.yml` runs unit tests and lint and builds a signed release APK on every pull
request (signed with a throwaway key) and on `main` (signed with the release key, then published
as a GitHub release). Secrets: `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
