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

## Current versions

- **Phone** — `v1.3.0` (versionCode 16)
- **Wear** — `v0.7.0` (versionCode 9)

## What's new since v0.2.0

The phone module gained an entire live-telemetry layer on top of the
showcase Material 3 Expressive shell. Headline additions:

- **GPU section** (v1.1.0 → v1.3.0) — `LiveStats.Gpu` sysfs snapshot
  (vendor / renderer / frequency / utilisation) with a `GpuHero`
  composable matching the rest of the section heroes. GPU also shows up
  as the fourth tile in the home `LiveStatsStrip`.
- **CPU section depth** — live utilisation bar in `CpuHero` (v0.7.0), bars
  grouped by cluster topology (v0.8.0), thermal-zone overview pulled from
  `/sys/class/thermal` (v0.9.0).
- **Live current draw** in `BatteryHero` (v0.5.2) from
  `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW`, sign-aware (charge vs
  discharge), animated.
- **Memory pressure level + zRAM bar** in `MemoryHero` (v1.0.0).
- **Live storage I/O read/write rates** in `StorageHero` (v0.5.3) sampled
  from `/proc/diskstats`.
- **Live network throughput** in the Network detail hero (v0.4.0) — RX/TX
  bytes/s with a rolling sparkline, sampled from `TrafficStats`.
- **System uptime** in the hero strip (v0.6.0).
- **Collapsible `LargeTopAppBar`** wired through detail screens (v0.3.0
  scroll UX), squircle shapes throughout (v0.3.0 design polish).

The watch module went from "data dump" to "at-a-glance":

- **v0.4.0** — visual gauges + home stats row matching the phone's
  LiveStatsStrip vocabulary.
- **v0.5.0** — live CPU frequency bar in the CPU detail screen, per-cluster.
- **v0.6.0** — live GPU frequency card in a new GPU detail screen,
  utilisation-preferred fill colour.
- **v0.7.0** — **Stats Tile** for the watch face: a 2×2 coloured-pill grid
  showing battery / memory / CPU / GPU, 30 s refresh, comfort-band tints
  (primary < 40 %, tertiary 40–70 %, error > 70 %). Tap launches the app.
  Non-interactive — sits on the watch face, no app-open round-trip needed.

Carried over from v0.2.0 and still load-bearing:

- 12 phone section cards with custom glyphs, hero header, animated
  `LiveStatsStrip`, M3 Expressive motion scheme, adaptive layout (1 / 2 /
  3 cols), edge-to-edge insets, tap-to-copy rows, theme picker,
  refresh-interval slider.
- Wear: custom-drawn Wear-tuned glyphs, `DisposableEffect`-gated polling
  on detail screens, contextual EdgeButton (`Ping phone` ↔ `Refresh`),
  disconnected-state messaging.

## Features (data points)

- **Device** — manufacturer, brand, model, codename, board, hardware,
  fingerprint.
- **System** — Android version, SDK level, security patch, build type/tags,
  build user/host, kernel string from `/proc/version`.
- **Battery** — level, status, health, plug type, voltage, temperature,
  technology, capacity counter, current (now / avg), energy counter.
- **CPU** — ABI list, core count, per-core current/min/max frequency from
  `/sys/devices/system/cpu`, scaling governor.
- **Memory** — total, available, low-memory threshold; live used %; pressure
  level and zRAM swap usage.
- **GPU** — vendor / renderer / version strings (`GLES20.glGetString`),
  current / min / max frequency from `/sys/class/kgsl/kgsl-3d0/devfreq/*`,
  utilisation percent when the driver exposes it (`gpu_busy_percentage`).
- **Storage** — internal and external partition totals; per-volume bars;
  live read/write throughput from `/proc/diskstats`.
- **Display** — pixel resolution, density, scaled density, refresh rate,
  available modes, HDR capability set.
- **Network** — active transport, validated/internet capability, link speeds,
  Wi-Fi RSSI / link speed / frequency / SSID, carrier name, MCC/MNC, IPs;
  live RX/TX throughput via `TrafficStats`.
- **Thermal** — `/sys/class/thermal/thermal_zone*` type and temperature.
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
adb install marrow-phone-v1.3.0-debug.apk        # on your phone
adb -s <watch-serial> install marrow-wear-v0.7.0-debug.apk  # on your Wear OS device
```

Both modules use the same `applicationId = rocks.talon.marrow`, so once the
Play Store auto-pair is enabled they install together. Sideloading both
manually works too — they pair via the Data Layer regardless.

After installing the watch APK, long-press the watch face → **Tiles** →
add **Marrow** to put the live 2×2 stats grid one swipe away from the
clock face.

## Architecture

```
shared/   ← DeviceInfoCollector + LiveStats primitives + wire format
phone/    ← Material 3 Expressive nav-host app, three tabs + detail/settings
wear/     ← Wear Compose Material 3 app with edge button + rotary list
            + StatsTileService (the 2×2 watch-face tile, v0.7.0+)
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
`LiveStats.Gpu`, `LiveStats.Volume`, `LiveStats.NetworkSpeed`, `LiveStats.ThermalZone`)
that are polled every refresh-interval seconds — that's where the animated
battery ring, per-core CPU bars, GPU utilisation strip, throughput sparkline
and thermal zones get their numbers. The same primitives feed the Wear
tile's 2×2 cells.

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
