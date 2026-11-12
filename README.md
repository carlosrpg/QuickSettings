# QuickSettings

A widget drawer that floats over every app on the Jaecoo 7 head unit (Android Automotive 11).
Closed, it is a 6 dp hint on the screen edge. Swipe inward from the edge to open it semi-open:
small square widgets you read at a glance. Open it fully for larger cards and full controls.

Style: **Night Glass**, the approved styleguide.

## Widgets

| Widget | Semi-open | Open |
|---|---|---|
| Media | Album art, play/pause. Hold and drag: up/down volume, left/right previous/next | Source app (tap to open it), title, progress, transport, mute, volume, voice search sent to the app that owns the session |
| Climate | Temperature × fan quadrant | ± temperature on the vertical axis, ± fan on the horizontal one |
| Car mode | Battery-mode glyph and regen bars. Hold and drag: up/down battery mode, left/right regen | Initial / Smart / Forced, hold-target slider when Forced, Low / Medium / High regen |
| Autonomy | Battery ring (outer) and fuel ring (inner), electric km | Electric and fuel range, km and %, total |
| Navigation | Next manoeuvre and distance; with no route, a tap starts a voice destination search | Nav app (tap to open it), next turn, street, ETA, voice destination |
| Assist | Emergency braking, auto hold, lane keeping (read only) | Same, with On/Off |

## How it gets its data

- **Car**: CarInfo items through [jconfig-carbus](https://github.com/carlosrpg/jconfig-carbus). CarInfo has no
  subscribe, and values also change from the stock settings, voice and buttons, so the app **polls**
  (every 1.5 s while the drawer is visible, and once more right after each write). Ids and
  encodings come from `jconfig-carbus/docs/carinfo-items.md`.
- **Media**: the active `MediaSession`, found through the notification listener.
- **Navigation**: the navigation app's ongoing notification (title, text, large-icon arrow).

Nothing writes to the car on its own. Every write comes from a tap, a slider release or a
hold-drag release. Regen and battery mode aren't gated on speed: the stock settings app doesn't
gate them either.

## Build

Requires JDK 21 and the Android SDK (`local.properties` with `sdk.dir`). The build uses a
`jconfig-carbus` checkout from `../jconfig-carbus` (or `./jconfig-carbus`) when one is present.

```bash
./gradlew app:testDebugUnitTest app:assembleDebug        # emulator build, in-memory car
./gradlew app:assembleRelease -Pqs.mock=false            # head-unit build, signed
```

Release signing reads `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and
`RELEASE_KEY_PASSWORD` from Gradle properties, `local.properties` or the environment.

## Permissions

Grant them on first run from the app's settings screen. Car builds often hide these pages, so
use the adb fallbacks:

```bash
adb shell appops set com.carlosrpg.cherry.quicksettings SYSTEM_ALERT_WINDOW allow
adb shell cmd notification allow_listener com.carlosrpg.cherry.quicksettings/com.carlosrpg.cherry.quicksettings.nav.QsNotificationListener
adb shell pm grant --user current com.carlosrpg.cherry.quicksettings android.permission.RECORD_AUDIO
# optional: replay taps that land in the edge swipe zone onto the app behind it
adb shell settings put secure enabled_accessibility_services com.carlosrpg.cherry.quicksettings/com.carlosrpg.cherry.quicksettings.overlay.TapRelayService
```

On Android Automotive the app runs as the driver user (10), hence `--user current`. The head unit's adbd drops any command line that contains `install`. Push the APK and install
it from an on-device script.

## CI

`.github/workflows/ci.yml` runs unit tests and lint and builds a signed release APK on every pull
request (signed with a throwaway key) and on `main` (signed with the release key, then published
as a GitHub release). Secrets: `CARBUS_REPO_TOKEN` (read access to jconfig-carbus),
`RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
