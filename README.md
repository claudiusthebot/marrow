# Marrow

*to the marrow of your devices*

[![License: MIT](https://img.shields.io/badge/License-MIT-orange.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/claudiusthebot/marrow?color=orange)](https://github.com/claudiusthebot/marrow/releases)
[![Platform](https://img.shields.io/badge/platform-Wear%20OS%206%20%7C%20Android%2016%2B-orange)](https://developer.android.com/wear)

A device info app for Android phone and Wear OS, in Material 3 Expressive.
Inspired by [SebaUbuntu/Athena](https://github.com/SebaUbuntu/Athena), but
redesigned around the new M3 Expressive component set and the Wear OS 6
recipe (`AppScaffold` + `EdgeButton` + `TransformingLazyColumn`).

The phone module and watch module ship in the same package, so installing one
auto-installs the other through Play. The two halves talk over the Wear OS
Data Layer: the phone's *Watch* tab asks the watch for its info and renders
the response with the same UI it uses for itself.

## Features

- **Device** — manufacturer, brand, model, codename, board, hardware,
  fingerprint.
- **System** — Android version, SDK level, security patch, build type/tags,
  build user/host, kernel string from `/proc/version`.
- **Battery** — level, status, health, plug type, voltage, temperature,
  technology, capacity counter, current (now / avg), energy counter.
- **CPU** — ABI list, core count, per-core current/min/max frequency from
  `/sys/devices/system/cpu`, scaling governor.
- **Memory** — total, available, low-memory threshold.
- **Storage** — internal and external partition totals.
- **Display** — pixel resolution, density, scaled density, refresh rate,
  available modes, HDR capability set.
- **Network** — active transport, validated/internet capability, link speeds,
  Wi-Fi RSSI / link speed / frequency / SSID, carrier name, MCC/MNC, IPs.
- **Sensors** — full SensorManager.getSensorList() dump with vendor, type,
  range, power.
- **Cameras** — per-camera ID, facing, max JPEG resolution, sensor pixel array
  size, focal lengths.
- **Build flags** — radio version, Treble, VNDK version, first API level,
  verified boot state.

The Wear app is a swipeable list of section cards with a detail screen per
section, an `EdgeButton` to ping the phone (a debug action — the phone shows a
toast), rotary-crown scrollable, with the focal-edge transform from
`rememberTransformationSpec()`.

## Screenshots

Coming after first install on hardware.

## Install

Grab the two debug APKs from the [latest release](https://github.com/claudiusthebot/marrow/releases/latest):

```
adb install marrow-phone-v0.1.0-debug.apk         # on your phone
adb -s <watch-serial> install marrow-wear-v0.1.0-debug.apk  # on your Wear OS device
```

Both modules use the same `applicationId = rocks.talon.marrow`, so once the
Play Store auto-pair is enabled they install together. Sideloading both
manually works too — they pair via the Data Layer regardless.

## Architecture

```
shared/   ← DeviceInfoCollector + wire format (kotlinx.serialization)
phone/    ← Material 3 Expressive bottom-nav app, three tabs
wear/     ← Wear Compose Material 3 app with edge button + rotary list
```

`shared` is an Android library module — both apps depend on it, so the
collector code (the load-bearing part) is written once. The wire format is a
plain `@Serializable data class DeviceInfoSnapshot` shipped over MessageClient
on path `/marrow/device_info`.

The phone caches the last received snapshot in DataStore so the Watch tab
shows last-known data immediately when reopened, then refreshes in the
background.

## Building from source

Requirements: **JDK 17** (not 21 — the Gradle daemon currently dies on 21 in
some Android Gradle Plugin pairings), Android SDK 36, the Gradle wrapper
brings everything else.

```sh
./gradlew :phone:assembleDebug
./gradlew :wear:assembleDebug
```

Outputs land in `phone/build/outputs/apk/debug/` and
`wear/build/outputs/apk/debug/`.

Stack: Gradle 8.11.1, AGP 8.9.2, Kotlin 2.1.0, Compose BOM 2025.04.01,
`material3:1.5.0-alpha18` (the alpha exposes `MotionScheme.expressive` and
the rest of the Expressive API), `compose-material3:1.5.6` on Wear.

## Credits

Inspired by [SebaUbuntu/Athena](https://github.com/SebaUbuntu/Athena) — the
canonical Android device info app. Marrow is a redesign, not a port; the data
collection touches a similar API surface but the UI and the watch
companion are written from scratch on top of the Material 3 Expressive
component set.

Sibling apps in the same M3 Expressive series:

- [hydrate-pixel-watch](https://github.com/claudiusthebot/hydrate-pixel-watch)
  — water-intake tracker (Wear OS + phone)
- [claude-watch-buddy](https://github.com/claudiusthebot/claude-watch-buddy)
  — BLE buddy for Wear OS

## License

MIT — see [LICENSE](LICENSE).
