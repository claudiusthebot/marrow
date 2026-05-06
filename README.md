# Marrow

*to the marrow of your devices*

[![License: MIT](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/claudiusthebot/marrow?color=orange)](https://github.com/claudiusthebot/marrow/releases)
[![Platform](https://img.shields.io/badge/platform-Wear%20OS%206%20%7C%20Android%2016%2B-orange)](https://developer.android.com/wear)

A device info app for Android phone and Wear OS, in Material 3 Expressive.
Inspired by [SebaUbuntu/Athena](https://github.com/SebaUbuntu/Athena), but
redesigned around the new M3 Expressive component set, the Wear OS 6
recipe, and a custom hand-drawn glyph set.

The phone module and watch module ship in the same package, so installing
one auto-installs the other through Play. The two halves talk over the Wear
OS Data Layer: the phone's *Watch* tab asks the watch for its info and
renders the response with the same UI it uses for itself.

## What's new in v0.2.0

The phone module is now a showcase-grade Material 3 Expressive app:

- **Hero header** — large display-typography device name, manufacturer · SoC ·
  Android version subtitle, gradient surface, hand-drawn bone glyph mascot.
- **Live stats strip** — animated chips for Battery %, RAM used %, CPU avg
  MHz, Storage used %. Tap a chip to jump to the corresponding section.
- **12 section cards** — Battery, CPU, Memory, Storage, Display, Network,
  Sensors, Cameras, Build flags, System, Device, Software. Each card shows
  a custom-drawn glyph (no Material Icons defaults), live preview value,
  and animates on press with a haptic kick.
- **Bespoke per-section heroes** on every detail screen:
  - **Battery**: animated circular gauge tinted by level (red → amber →
    green), `charging` state, voltage / temp / plug type strip.
  - **CPU**: live per-core frequency bars, governor chip, ABI chip row.
  - **Memory**: segmented gradient bar with used/free legend.
  - **Storage**: per-volume progress bar list.
  - **Display**: phone-shape preview rectangle, HDR badge, density.
  - **Network**: transport headline + carrier/SSID + RSSI chip.
  - **Sensors**: live readings (accel XYZ bars, light lux, proximity,
    pressure, ambient temperature) — listeners registered only while
    composed via `DisposableEffect`, no background drain.
  - **Cameras / Build / Device / System / Software**: contextual headlines.
- **Section detail screens** with `LargeTopAppBar` collapsing scroll,
  pull-to-refresh, tap-to-copy on every row, share action.
- **Quick actions** — Copy report (Markdown), Share, Refresh.
- **Settings** — theme picker (Dynamic / Brand / System), refresh-interval
  slider (1–30 s), persisted via DataStore.
- **Adaptive layout** — single column < 600dp, 2 cols at 600dp, 3 cols at
  840dp+ (foldables / large screens).
- **Edge-to-edge** with `enableEdgeToEdge()` + `WindowCompat`, status &
  navigation bar insets handled via `windowInsetsPadding`.
- **Material 3 Expressive motion scheme** wired through the theme.

The watch module gets matching polish:

- **Custom-drawn section icons** in a Wear-tuned weight (2 dp stroke,
  18 dp viewport in cards), same family as the phone.
- **Live battery/memory** on the Battery and Memory detail screens — 10 s
  polling registered via `DisposableEffect`, unregistered on swipe-back.
- **Contextual EdgeButton** — `Refresh` when phone unreachable, `Ping
  phone` when connected. Detail screens always show `Refresh`.
- **Disconnected state** — list shows `Phone disconnected · Showing cached
  data` instead of failing silently.

## Features (data points)

- **Device** — manufacturer, brand, model, codename, board, hardware,
  fingerprint.
- **System** — Android version, SDK level, security patch, build type/tags,
  build user/host, kernel string from `/proc/version`.
- **Battery** — level, status, health, plug type, voltage, temperature,
  technology, capacity counter, current (now / avg), energy counter.
- **CPU** — ABI list, core count, per-core current/min/max frequency from
  `/sys/devices/system/cpu`, scaling governor.
- **Memory** — total, available, low-memory threshold; live used %.
- **Storage** — internal and external partition totals; per-volume bars.
- **Display** — pixel resolution, density, scaled density, refresh rate,
  available modes, HDR capability set.
- **Network** — active transport, validated/internet capability, link speeds,
  Wi-Fi RSSI / link speed / frequency / SSID, carrier name, MCC/MNC, IPs.
- **Sensors** — full SensorManager.getSensorList() dump with vendor, type,
  range, power; live readings for accel/light/proximity/pressure/temp.
- **Cameras** — per-camera ID, facing, max JPEG resolution, sensor pixel
  array size, focal lengths.
- **Build flags** — radio version, Treble, VNDK version, first API level,
  verified-boot state.
- **Software** — Java VM, runtime, locale, time zone, installed app counts,
  heap (max/total/free), class file version.

## Screenshots

Screenshots will land here after first install. The design intent: hero
device name dominating the top of the screen, a chip strip of live numbers
underneath, then a tappable grid of section cards with custom glyphs that
read as a family.

## Install

Grab the two debug APKs from the [latest
release](https://github.com/claudiusthebot/marrow/releases/latest):

```
adb install marrow-phone-v0.2.0-debug.apk         # on your phone
adb -s <watch-serial> install marrow-wear-v0.2.0-debug.apk  # on your Wear OS device
```

Both modules use the same `applicationId = rocks.talon.marrow`, so once the
Play Store auto-pair is enabled they install together. Sideloading both
manually works too — they pair via the Data Layer regardless.

## Architecture

```
shared/   ← DeviceInfoCollector + LiveStats primitives + wire format
phone/    ← Material 3 Expressive nav-host app, three tabs + detail/settings
wear/     ← Wear Compose Material 3 app with edge button + rotary list
```

`shared` is an Android library module — both apps depend on it, so the
collector code (the load-bearing part) is written once. The wire format is
a plain `@Serializable data class DeviceInfoSnapshot` shipped over
MessageClient on path `/marrow/device_info`.

The phone caches the last received snapshot in DataStore so the Watch tab
shows last-known data immediately when reopened, then refreshes in the
background.

The phone's per-section detail screens reach into a small set of structured
"live primitives" (`LiveStats.Battery`, `LiveStats.Memory`, `LiveStats.CpuCore`,
`LiveStats.Volume`) that are polled every refresh-interval seconds — that's
where the animated battery ring and per-core CPU bars get their numbers.

## Building from source

Requirements: **JDK 17** (not 21 — the Gradle daemon currently dies on 21
in some Android Gradle Plugin pairings), Android SDK 36, the Gradle wrapper
brings everything else.

```sh
./gradlew :phone:assembleDebug
./gradlew :wear:assembleDebug
```

Outputs land in `phone/build/outputs/apk/debug/` and
`wear/build/outputs/apk/debug/`.

Stack: Gradle 8.11.1, AGP 8.9.2, Kotlin 2.1.0, Compose BOM 2025.04.01,
`material3:1.5.0-alpha18` (the alpha exposes `MotionScheme.expressive` and
the rest of the Expressive API), `compose-material3:1.5.6` on Wear,
`navigation-compose:2.8.5`, `material3-window-size-class:1.3.1`,
`datastore-preferences:1.1.1`.

## Credits

Inspired by [SebaUbuntu/Athena](https://github.com/SebaUbuntu/Athena) — the
canonical Android device info app. Marrow is a redesign, not a port; the
data collection touches a similar API surface but the UI and the watch
companion are written from scratch on top of the Material 3 Expressive
component set.

Sibling apps in the same M3 Expressive series:

- [hydrate-pixel-watch](https://github.com/claudiusthebot/hydrate-pixel-watch)
  — water-intake tracker (Wear OS + phone)
- [claude-watch-buddy](https://github.com/claudiusthebot/claude-watch-buddy)
  — BLE buddy for Wear OS

## License

MIT — see [LICENSE](LICENSE).
