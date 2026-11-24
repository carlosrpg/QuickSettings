# QuickSettings

A widget drawer that floats over every app on the Jaecoo 7 head unit (Android Automotive 11).
Closed, it is a 6 dp hint on the screen edge. Touch it to open the drawer semi-open: small
square widgets you read at a glance. Open it fully for larger cards and full controls.
English, Brazilian Portuguese and Spanish.

The car's own back gesture takes any swipe that starts within 30 px of the left or right screen
edge, so the handle opens on touch. "Touch the edge handle to open" in settings (on by default)
switches to swipe-only. With the info bar on, a pull down just below the car's top bar opens the
full drawer, and **Two-finger swipe down** (off by default) opens it on the car's own
two-finger gesture, with no gesture app needed. A gesture app that launches QuickSettings by
package opens the full drawer too.

Style: **Night Glass**, the approved styleguide.

## Widgets

| Widget | Semi-open | Open |
|---|---|---|
| Media | Album art, play/pause. Press and drag: up/down volume, left/right previous/next | Source (an app, tap to open it, or one of the car's own: Bluetooth, USB, CarPlay, Android Auto, …), title, progress, transport, volume, voice search sent to the app that owns the session |
| Climate | Temperature × fan quadrant. Press and drag moves the marker: up/down temperature, left/right fan, set on release | The seat with airflow that follows temperature and fan, temperature and fan sliders set on release, and buttons that follow the car's state: climate on/off, automatic mode, zone sync, air recirculation and A/C. While the zones are split, the passenger temperature takes the top of the passenger-side slider column |
| Car mode | Battery-mode glyph and regen bars. Press and drag: up/down battery mode, left/right regen | Two buttons that show the state as a symbol and step to the next on tap: battery mode (marker with bolt = Initial, light bulb = Smart, charging battery = Forced) and regeneration (a brake disc whose rim lights one, two or three arcs for Low, Medium, High); hold-target slider for Forced |
| Autonomy | Battery ring (outer) and fuel ring (inner), electric km | Electric and fuel range, km and %, total |
| Navigation | Next manoeuvre and distance; with no route, a tap starts a voice destination search | Nav app (tap to open it), next turn, street, ETA, voice destination, and a button that shows the nav app on the instrument cluster or brings it back (only on a car with a cluster display) |
| Assist | Emergency braking, auto hold, lane departure prevention (status) | Same, with On/Off, plus forward collision warning (a tap cycles Off, Near, Standard, Far; Off only below 10 km/h); tap a row to switch it. The car's own rules apply: ignition on, emergency braking only below 10 km/h, and a second tap to turn emergency braking or lane prevention off |

Media volume: up, down and mute go through `AudioManager.adjustVolume`, the call the
steering-wheel keys end in. The car's audio service moves whichever source is playing by one step
and shows its own volume bar. The level itself can't be read by an app on the car, so there the
drawer shows only + and − (the number appears on a phone-style system and, simulated, in debug
builds).

## How it gets its data

- **Car**: CarInfo items through [jconfig-carbus](https://github.com/carlosrpg/jconfig-carbus), bundled as two
  prebuilt AARs in `app/libs/`: `carbus` (the Kotlin layer) and `vdbus-client` (the vendor client
  classes it talks through). Both must ship: the head unit has no copy of the vendor client that
  an app can load. CarInfo has no subscribe, and values also change from the stock settings, voice
  and buttons, so the app **polls**, on one heartbeat shared by car, media and the cluster sync:
  every 2 s while the drawer is out or a paired phone shows it, every 5 s while only the top bar
  shows, not at all while nothing does, and again right after each write.
- **Media**, from two feeds that never overlap:
  - Apps installed on the head unit: the active `MediaSession`, found through the notification
    listener.
  - The car's own sources (Bluetooth audio, USB, CarPlay, Android Auto, the built-in online
    apps): the vendor media service, through jconfig-carbus. These never publish a media
    session. Radio and DAB show only their name for now.

  An app that is playing wins; otherwise the car's current source is shown.
- **Navigation**: the navigation app's turn-by-turn notification (title, text, arrow). When
  an app draws its directions with a layout of its own and leaves the standard fields empty,
  the texts and the arrow are read from that layout.
- **Climate**: temperature and fan move one step per command, on the car's "set" items (HVAC
  76 and 77); the fan is never written on the item it is read from (12), because the car
  answers that by opening its own full-screen climate panel. As a fallback the app still
  watches for the panel's open request for about two seconds after a write and sends the
  panel's close request, unless the panel was already open.

**Always on.** The drawer runs as a foreground service that starts at boot, and again whenever
the app's process starts for any reason. If the process is killed, the system re-binds the
notification listener, which restarts the process and with it the drawer, so this depends on
notification access being granted.

The **Status** panel at the bottom of the settings screen shows, live, whether the car-data
link, the car-media link and notification access are up. Its **Debug log** button prints, as text to
photograph, the navigation state, every notification posted right now and the last ones the
listener was handed. A navigation app that is guiding and has no line there is not posting one.
Its **drawer** section lists the last 40 attempts to open the drawer: each touch on the handle or
the top strip and what came of it, each gesture the car reported (a two-finger swipe the car read
as a pinch or a long press shows up as such), each launch from a gesture app, and when a touch
zone was hidden or briefly untouchable, and why. A touch with no line never reached the app.

Nothing writes to the car on its own. Every write comes from a tap, a slider release or a
drag release. Regen and battery mode aren't gated on speed: the stock settings app doesn't gate
them either.

## Phone control

Everyone in the car can use the drawer from their phone. Off by default: **Settings → Phones →
Phone control** starts a small web server on the car (`http://<car>:8080`) that answers only
devices on a local network, and only phones someone at the head unit allowed.

1. **Install the phone app.** QS Companion for Android is on the
   [site](https://carlosrpg.github.io/QuickSettings/#phone) and in every
   [release](../../releases) (`QSCompanion-<version>.apk`). On an iPhone, or without the app, the
   phone uses the same page in its browser.
2. **Pair.** Put the phone on the car's Wi-Fi (or the car on the phone's hotspot) and tap **Add
   phone** on the car. It shows a QR code and a six-digit code, both valid for five minutes and
   one attempt. The app finds the car by itself and asks for the code; a browser opens the QR
   code's link. Keep the settings screen open: the car asks **Allow <name>?** there.
3. **Use it.** The page shows the drawer's cards in the open-drawer order, in English,
   Portuguese or Spanish, with a status line: connected over Wi-Fi, over the internet, car offline (last
   seen), or no connection. **Remove** on the car takes a phone off at once.

Commands go through the drawer's own calls and gates: ignition and speed rules for driver
assist, and a second tap before emergency braking or lane keeping goes off. A phone counts as
watching while it polls, and the car is then read every 2 s as with the drawer open.

**Over the internet (optional).** `relay/` is a Cloudflare Worker with one Durable Object per car.
The car keeps one outbound WebSocket to it and the relay forwards each phone request; the car still
checks every token. Pairing works only on the car's Wi-Fi, and through the relay driver assist is
read-only unless **Settings → Phones → Driver assist over the internet** is on (it asks first).
Releases built by CI have no relay. To run your own:

```bash
cd relay
npx wrangler deploy                    # Free plan is enough; prints https://qs-relay.<you>.workers.dev
npx wrangler secret put RELAY_KEY      # a long random string
```

Then put the same two values in `local.properties` and build both APKs yourself:

```
qs.relayUrl=https://qs-relay.<you>.workers.dev
qs.relayKey=<the RELAY_KEY value>
```

Design, API and what differs from the first draft: [docs/companion-api.md](docs/companion-api.md).

## Install on the car

**From the browser:** open the
[setup page](https://carlosrpg.github.io/QuickSettings/#setup-section)
([em português](https://carlosrpg.github.io/QuickSettings/pt/#setup-section),
[en español](https://carlosrpg.github.io/QuickSettings/es/#setup-section)) in Chrome or Edge, plug the
car in with a data cable and click Connect. **Install latest** installs the newest release on the
car: the page pushes the APK and `docs/car-setup.sh` to `/data/local/tmp` and runs the script. The
car refuses a plain `pm install` from the adb-shell uid, so the script installs the APK through a
`PackageInstaller` session opened by a small embedded helper run with `app_process`, opens the app
and deletes the pushed files (if that session is refused too, it copies the APK to the Download
folder for a file-manager install). Then it shows which of the grants below are missing and applies them
over WebUSB, with the same commands. The page lives in `docs/`; CI deploys it to GitHub Pages
with the released APK under `latest/` (Settings → Pages → Source: GitHub Actions).

**Easy path (Windows):** download `QuickSettings-FirstInstall.zip` from the latest
[release](../../releases), unzip it, enable ADB on the car, connect the cable and double-click
`First-Install.cmd`. It fetches adb if needed, copies the APK to the car's Download folder when
the app is missing (you install it from a file manager there), then applies every grant below
that is still missing. The manual steps follow.

1. Download the signed APK from the latest [release](../../releases) and copy it to a USB stick.
2. Install it on the car with a file manager.
3. Open QuickSettings. Its settings screen lists what is still missing and updates by itself
   as each grant lands.
4. From a PC connected over adb, type the two commands below. The car has no settings page for
   either.
5. Tap **Grant** next to Microphone and allow it (voice search only).

**Draw over other apps** (the drawer itself). Required:

```
adb shell appops set com.carlosrpg.cherry.quicksettings SYSTEM_ALERT_WINDOW allow
```

**Notification access** (media apps and navigation). Required:

```
adb shell cmd notification allow_listener com.carlosrpg.cherry.quicksettings/.nav.QsNotificationListener
```

The app targets the head unit's own Android version (API 30), so there is no "built for an older
version" notice; the cost is that the overlay is no longer granted at install.

**Foreground app** (optional, for "Info bar at the top of the screen"; one of the signals that
tells the bar a full-screen app such as Android Auto is up, so it steps aside):

```
adb shell appops set com.carlosrpg.cherry.quicksettings GET_USAGE_STATS allow
```

**Tap pass-through** (optional; needed with "Touch the edge handle to open" turned off, and for
the info bar, whose pull-down zone replays taps and whose full-screen check reads the window
list). The first command appends to the existing list, so the car's own accessibility services
stay on:

```
adb shell 'settings --user current put secure enabled_accessibility_services "$(settings --user current get secure enabled_accessibility_services):com.carlosrpg.cherry.quicksettings/.overlay.TapRelayService"'
adb shell settings --user current put secure accessibility_enabled 1
```

**Wi-Fi and ADB automation** (for the Wi-Fi and ADB buttons in settings). Lets the app switch
its own accessibility services (the automation and the tap pass-through) on, now and after every
force-stop, which takes them off Android's list:

```
adb shell pm grant com.carlosrpg.cherry.quicksettings android.permission.WRITE_SECURE_SETTINGS
```

Until a permission is granted, the app's settings screen lists it with the same command.

## Build

Requires JDK 21 and the Android SDK (`local.properties` with `sdk.dir`).

```bash
./gradlew app:testDebugUnitTest app:lintDebug app:assembleDebug   # emulator build, in-memory car
./gradlew app:assembleRelease -Pqs.mock=false                       # head-unit build, signed
./gradlew companion:testDebugUnitTest companion:assembleRelease     # QS Companion phone app, signed
```

Versioning follows AABrowser-Motion: CI passes `-PappVersionName`; local builds take the next
patch after the latest `vX.Y.Z` tag with `-dev` (e.g. `0.1.1-dev`), and versionCode is
`major*1000000 + minor*1000 + patch`, so CI and local builds (same key) always install over older
ones. Builds are copied to `app/build/renamedApks/<variant>/QuickSettings-<version>[_debug].apk`.

Release signing reads `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and
`RELEASE_KEY_PASSWORD` from Gradle properties, `local.properties` or the environment.

To update the two bundled AARs from a jconfig-carbus checkout:

```bash
scripts/update-carbus.sh ../jconfig-carbus
```

## CI

`.github/workflows/ci.yml` runs unit tests and lint and builds a signed release APK on every pull
request (signed with a throwaway key) and on `main` (signed with the release key, then published
as a GitHub release). It builds the QS Companion phone app alongside, under the same version, and
the Pages deploy serves both APKs from `latest/` (`QuickSettings.apk`, `QSCompanion.apk`). Secrets: `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
