# Phone control (draft v2)

Everyone in the car controls it from their phone: climate, energy, driver assist, media,
navigation. QuickSettings on the head unit is the only thing that talks to the car and the
only thing that decides who may use it. Phones reach it directly on the same Wi-Fi, or through
a small relay on the internet when they can't.

Status: steps 1 and 2 of section 8 are built (LAN only: server, pairing, Settings → Phones, the
page). The relay, the internet assist setting and the Android wrapper are not.

## 1. Pieces

```
 same Wi-Fi (works with no signal)        anywhere with internet
 ┌─────────────┐                          ┌─────────────┐
 │ companion   │──http, direct──┐         │ companion   │──https──┐
 │ app         │                │         │ app or page │         │
 └─────────────┘                ▼         └─────────────┘         ▼
                    ┌───────────────────────┐            ┌──────────────────┐
                    │ QuickSettings (car)   │◄──outbound──│ relay            │
                    │  PhoneServer :8080    │  WebSocket  │ Cloudflare Worker│
                    │  page in assets/web/  │             │ 1 Durable Object │
                    │  paired phones list   │             │ per car          │
                    └───────────────────────┘             └──────────────────┘
```

| Piece | What it is |
|---|---|
| **Page** | Plain HTML/CSS/JS drawer in `app/src/main/assets/web/`, English and pt-BR. Served by the car on the LAN and by the relay over https, same files. |
| **PhoneServer** | Small HTTP/1.1 server on `java.net.ServerSocket` inside `OverlayService`, port 8080, Wi-Fi interface only. No new dependency. |
| **Relay** | Cloudflare Worker + one Durable Object per car (`relay/` in this repo). Forwards state and commands by car id. Stores nothing but the car's last state and when it was last seen. |
| **Companion app** | An Android WebView wrapper around the page (iOS dropped for now; an iPhone uses the page in its browser). It adds what a browser can't: finding the car on the LAN, plain-http access to it, and switching between LAN and relay. |

## 2. Reaching the car, and showing whether it can be reached

The app tries, in order, and re-checks every 10 s and whenever the phone's network changes:

1. **Direct:** the car's last LAN address, then an mDNS lookup of `_qscar._tcp` (Android
   `NsdManager`). `GET http://<car>:8080/api/ping` with a 1.5 s timeout.
2. **Relay:** `GET https://<relay>/c/<carId>/api/ping`, which answers for the car from the
   relay's side (below).

Status shown at the top of the page, always visible:

| Status | When | Controls |
|---|---|---|
| **Connected · Wi-Fi** | direct ping answered | all the phone is allowed |
| **Connected · Internet** | relay answered and says the car is online | all allowed, minus driver assist unless the car allows it (section 4) |
| **Car offline** · "last seen 14 min ago" | relay answered, car not connected to it | greyed out; the last state is shown with its time |
| **No connection** | neither answered (phone offline, wrong network) | greyed out; last state shown with its time |

How the relay knows the car is online: the car keeps one WebSocket to its Durable Object and
pings it every 30 s. Pings use the hibernation auto-response, so they cost nothing and don't
wake the object; the object reads the last ping time (`getWebSocketAutoResponseTimestamp`) and
counts the car offline after 75 s without one, or at once when the socket closes.

The car reconnects to the relay with backoff (2 s doubling to 5 min) and immediately when
Android reports the network back.

Only the LAN works without internet; the companion app keeps working on a trip with no signal,
a browser on the relay page does not.

## 3. Pairing: the car accepts and removes phones

0. **Settings → Phones → Phone control** turns the server on (off by default: it opens port
   8080 on the car's network). Only local addresses (private ranges, link-local, loopback) are
   served.
1. **Car → Settings → Phones → Add phone** shows a QR code:
   `https://<relay>/pair#car=<carId>&code=<one-time code>&lan=<ip>:8080` (code valid 5 min,
   one use). Without a relay configured the QR holds the LAN address only:
   `http://<ip>:8080/#pair=<code>`. The Allow prompt shows on that same settings screen, so keep
   it open until the phone is added.
2. **Phone scans it.** The companion app opens the pairing screen; a phone without the app
   opens the page in its browser. The phone sends `{code, name: "Ana's iPhone"}` over the LAN if
   it can, otherwise through the relay.
3. **The head unit asks "Allow Ana's iPhone?"**. Only someone in the car can say yes.
4. **The car issues that phone a random 32-byte token**, stores its hash with the name and date,
   and returns it. The app keeps it in the Android Keystore, the page in `localStorage`.
5. Every request carries `Authorization: Bearer <token>`, on both paths. **The car checks it**;
   the relay only forwards. Unknown or removed token: HTTP 401, the page shows "This phone was
   removed from the car" and offers to pair again.
6. **Settings → Phones** lists each phone (name, added, last used, Wi-Fi or Internet) with
   **Remove**, effective at once on both paths.

TLS ends at Cloudflare, so the relay could read state and commands. Acceptable for now;
end-to-end encryption with a key carried in the QR code can be added later without changing
the pairing flow.

## 4. Driver assist over the internet: off unless the car allows it

Over the relay the car cannot know whether the person is in it. So:

- Settings → Phones → **Allow driver-assist changes over the internet**, **off by default**.
- While off, `assist.toggle` and `assist.cycleCollisionWarning` arriving through the relay are
  rejected `REMOTE_DISABLED`; the page shows those rows read-only with "Only on the car's Wi-Fi".
  On the LAN they work as in the drawer.
- Turning it on shows, on the head unit:

  > **Allow driver assist from anywhere?**
  > Paired phones will be able to switch emergency braking, lane keeping, auto hold and
  > collision warning from outside the car, over the internet, while someone else is driving.
  > The car still refuses emergency braking above 10 km/h and still asks before a safety
  > function goes off, but the person changing it may not see the road or know the car is
  > moving. Anyone holding a paired phone can do this.
  > [Cancel] [Allow]

  The default button is Cancel. Turning it off needs no confirmation.
- Every other action (climate, energy, media, navigation) works over the relay for any paired
  phone.

The car's own gates apply on both paths, against a fresh read: ignition on, emergency braking
below 10 km/h, `confirm: true` before AEB or lane keeping goes off.

## 5. API

Same on the LAN (`http://<car>:8080/api/...`) and through the relay
(`https://<relay>/c/<carId>/api/...`). JSON throughout.

| Route | Auth | Purpose |
|---|---|---|
| `GET /api/ping` | none | `{carId, online, lastSeenMs, path: "lan"\|"relay"}` |
| `POST /api/pair` | pairing code | `{code, name}` → waits for Allow on the car → `{token}` or 403 |
| `GET /api/state` | token | the state below |
| `POST /api/cmd` | token | `{action, args}` → `{ok}` or `{ok:false, blocked}` |
| `GET /img/<key>` | token, or `?t=<token>` (an `<img>` can't send a header) | cover, app icon or turn arrow, cached a day |

The page polls `/api/state` every 2 s while visible and stops when hidden. Through the relay it
gets the Durable Object's copy, which the car refreshes **only while a phone is watching**
(the object tells the car the viewer count) and **only when something changed**; the page
moves the progress bar itself from `positionAtMs`.

### State

```jsonc
{
  "connected": true,                     // car service bound; false = every car value is null
  "path": "relay", "asOfMs": 1732190000000,
  "permissions": {"assist": false},      // what this phone may change on this path
  "layout": {"open": ["MEDIA", "CLIMATE", "CAR_MODE", "ASSIST"], "side": "LEFT"},
  "car": {
    "speedKmh": 0,
    "climate": {
      "on": true, "ac": true, "auto": false, "recirculation": false,
      "sync": false, "driverTempC": 22, "passengerTempC": 20, "fanLevel": 3, "outsideTempC": 14,
      "applying": {"driverTempC": null, "passengerTempC": null, "fanLevel": null}
    },
    "energy": {
      "batterySoc": 72, "fuelPercent": 45, "electricRangeKm": 58, "fuelRangeKm": 554,
      "chargeLimit": 90, "regen": "MEDIUM", "batteryMode": "SMART", "holdTarget": 50
    },
    "assist": {"aeb": true, "autoHold": true, "laneKeep": false, "collisionWarning": "STANDARD"}
  },
  "media": {
    "source": "app", "appLabel": "Spotify", "appIcon": "/img/a1b2", "title": "…", "artist": "…",
    "art": "/img/c3d4", "playing": true, "positionMs": 61000, "positionAtMs": 1732190000000,
    "durationMs": 215000,
    "volume": {"media": {"value": 12, "max": 39}, "navigation": {"value": 20, "max": 39}},
    "recent": [{"packageName": "…", "label": "…", "icon": "/img/e5f6"}]
  },
  "nav": {"appLabel": "Waze", "title": "350 m", "text": "Turn right onto …", "subText": "12:41", "maneuver": "/img/…"},
  "cluster": {"navOnCluster": false},
  "weather": {"tempC": 18, "kind": "CLOUDY", "isDay": true, "place": "…", "daily": [{"date": "2026-11-21", "minC": 12, "maxC": 21, "kind": "RAIN"}]}
}
```

### Commands

Each maps to one call the drawer already makes; the server adds nothing between them.

| `action` | `args` | Calls | Gates (on the car) |
|---|---|---|---|
| `climate.setDriverTemp` | `c` 16..30 | `CarRepository.setCabinTemp` | none (stepped, debounced) |
| `climate.setPassengerTemp` | `c` 16..30 | `setPassengerTemp` | `NOT_SPLIT` while synced |
| `climate.setFan` | `level` 1..10 | `setFan` | none |
| `climate.toggle` | `switch`: `POWER` `AC` `AUTO` `RECIRCULATION` `SYNC` | `toggleClimate` | none |
| `energy.setRegen` | `level`: `LOW` `MEDIUM` `HIGH` | `setRegen` | none |
| `energy.setBatteryMode` | `mode`: `INITIAL` `SMART` `FORCED` | `setBatteryMode` | none |
| `energy.setHoldTarget` | `percent` 30..80 | `setHoldTarget` | none |
| `assist.toggle` | `assist`: `AEB` `AUTO_HOLD` `LANE`, `confirm` | `toggleAssist` | relay: section 4; ignition; AEB < 10 km/h; `confirm` to switch AEB/LANE off |
| `assist.cycleCollisionWarning` | | `cycleCollisionWarning` | relay: section 4; ignition; to/from Off < 10 km/h |
| `media.playPause` / `next` / `previous` | | `MediaRepository` | none |
| `media.playRecent` | `packageName` from `recent` | `playApp` | must be in `recent` |
| `media.search` | `query` | `playFromSearch` | none (phone dictation) |
| `media.setVolume` | `channel`: `MEDIA` `NAVIGATION`, `value` | `moveLevel` | clamped |
| `media.toggleMute` | | `toggleMute` | none |
| `nav.search` | `query` | `NavStore.searchDestination` | none |
| `nav.toggleCluster` | | `DrawerActions.toggleNavOnCluster` | none |
| `app.bringToFront` | `which`: `MEDIA` `NAV` | `openMediaApp` / `openNavApp` | none |

Not offered: opening or closing the drawer, Settings, the EngMode automation.

`blocked`: `IGNITION_OFF`, `TOO_FAST`, `UNAVAILABLE` (the car's `AssistBlock`, or the app refused
the search / open), `NEEDS_CONFIRM`, `NOT_SPLIT`, `NOT_CONNECTED`, `REMOTE_DISABLED`, `BAD_ARGS`,
`UNKNOWN_ACTION`, `RATE_LIMIT` (HTTP 429), `CAR_OFFLINE` (relay, car not connected).
`ok: true` means sent, not done; the next state is the truth. 10 commands/s per phone, then 429.
Commands from several phones go through the same steppers: two people dragging the temperature
move one target.

## 6. Relay, cost and several cars

- One Worker, one Durable Object per car id (random, made on the car's first start). Cars
  never see each other's traffic; each car keeps its own phone list.
- The car opens its relay connection with a **relay key** (a Worker secret) entered in
  Settings with the relay URL. An APK without them, anyone else's install of this public repo,
  stays LAN-only and never touches your quota. Later, a "Deploy to Cloudflare" button can let
  other owners run their own relay.
- Free plan (per account, per day): 100,000 Worker requests, 100,000 Durable Object requests,
  13,000 GB-s. Incoming WebSocket messages count 20:1, outgoing and pings are free, idle
  hibernating objects cost no compute. Estimate: about 200 requests and 2 GB-s per car per two
  hours of viewing, so roughly 2% of the free tier for ten cars. Limits fail hard until
  00:00 UTC; the paid plan ($5/month) is the way out if that ever matters.
- The relay must stay hibernatable: Hibernation API WebSockets, no timers, no pending fetches.

## 7. Testing

- **Car side:** JVM tests start `PhoneServer` on a free port over `MockCarBus`: state JSON,
  commands reaching the mock, pairing (allow, deny, expired code), 401 after removal,
  `REMOTE_DISABLED` for relay assist commands.
- **Emulator:** `adb forward tcp:8080 tcp:8080`, then `http://localhost:8080` in a desktop
  browser or `curl`.
- **Relay:** `wrangler dev` locally with the emulator connecting to it; unit tests with
  Vitest and the Workers pool.
- **Apps:** the page is the same, so only discovery and LAN/relay switching need testing on
  devices.

## 8. Order of work

1. `PhoneServer`, state mapper, command table, pairing, Settings → Phones (LAN only). Usable
   from a desktop browser against the emulator.
2. The page in `assets/web/`, including the status bar of section 2.
3. The relay (`relay/`), the car's relay connection, the assist toggle and its confirmation.
4. The Android WebView wrapper with LAN discovery. (iOS: not planned for now.)
