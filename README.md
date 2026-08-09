# LavArtemis

An open source game streaming client for [Apollo](https://github.com/ClassicOldSong/Apollo) and
[Sunshine](https://github.com/LizardByte/Sunshine), for **Android and desktop**.

Stream your PC games to a phone, a tablet, a handheld, another PC, or a Steam Deck — at home or over
the internet. One release ships every platform, built from the same tag.

| Platform | Artifact |
|---|---|
| Android | `LavArtemis-<version>-android-arm64-v8a.apk` |
| Windows x64 / ARM64 | portable `.zip`, plus a combined `.exe` installer |
| Linux x86_64 | `.AppImage` (this is also how the Steam Deck runs it) |

## Downloads

* [All releases](https://github.com/Lavagnou/LavArtemis/releases)
* Android via Obtainium (recommended — auto-updates):
  * [LavArtemis](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.limelight.lav%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FLavagnou%2FLavArtemis%22%2C%22author%22%3A%22Lavagnou%22%2C%22name%22%3A%22LavArtemis%22%2C%22additionalSettings%22%3A%22%7B%5C%22apkFilterRegEx%5C%22%3A%5C%22%5ELavArtemis-v.*android.*%5B.%5Dapk%24%5C%22%2C%5C%22matchGroutToUse%5C%22%3A%5C%22%241%5C%22%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22v(.%2B)%5C%22%7D%22%7D)

The Android build installs alongside stock Moonlight — it uses its own application ID, so you can keep both.

## What this fork adds

### Streaming performance

This is where the LavArtemis-specific work sits, on both clients unless noted:

* **Frame pacing metrics** — a rolling 512-frame window exposing p50/p95/p99 of present-to-present
  intervals, so pacing changes can be judged on numbers instead of impressions. On Windows the
  timestamps come from the actual vertical blank (`GetFrameStatistics`), not from when the frame was
  handed off.
* **Performance CSV logging** — the same 11 columns on both clients, so an Android run and a desktop
  run against the same host compare directly.
* **Thread priority boosts** on the video depacketizer and audio paths.
* **Link-time optimisation** on the native streaming core, where FEC recovery and depacketization run
  per packet.
* **ADPF** (Android) — reports real frame durations so the CPU is not downclocked during calm scenes.
* **Configurable audio buffer cap**, and a fix for an audio effects bug.
* **AV1 in Auto mode** (Android) — only advertised when a whitelisted hardware decoder exists.
* **Sustained performance mode and thermal warnings** (Android).

### Inherited from Artemis

Custom virtual gamepads, multiple mouse modes, custom resolutions and bitrates, portrait mode,
external display support, settings profiles, `art://` shortcuts, keyboard macros, view pan/zoom,
Apollo virtual display, server commands, clipboard sync, OTP pairing, and SBS 3D for external
displays via MiDaS (Android only).

The desktop client carries the same profiles, keyboard macros, `art://` links and pan/zoom, using
**identical file formats** — a settings profile or a macro file works on both.

## Building

Both clients need their submodules first.

### Android

```sh
git submodule update --init --recursive app/src/main/jni/moonlight-core/moonlight-common-c
# add ndk.dir=<path to NDK 27.0.12077973> to local.properties, or let Android Studio handle it
./gradlew assembleNonRoot_gameRelease
```

### Desktop

Lives in the [`desktop/`](https://github.com/Lavagnou/LavArtemis-Qt) submodule. Needs Qt 6.11.

```sh
git submodule update --init --recursive desktop
cd desktop
powershell ./setup-deps.ps1        # Windows
scripts\build-arch.bat Release x64
```

On Linux: `qmake6 lavartemis.pro && make release`.

## Lineage and credits

LavArtemis stands on three projects, and is GPLv3 like all of them:

* **[Moonlight](https://github.com/moonlight-stream)** — the original client, by Cameron Gutman,
  Diego Waxemberg, Aaron Neyer and Andrew Hennessy. Started as a student project at
  [Case Western](http://case.edu) and [MHacks](http://mhacks.org). Both LavArtemis clients descend
  from it: [moonlight-android](https://github.com/moonlight-stream/moonlight-android) and
  [moonlight-qt](https://github.com/moonlight-stream/moonlight-qt).
* **[Artemis](https://github.com/ClassicOldSong/moonlight-android)** by ClassicOldSong (formerly
  Moonlight Noir) — the Android fork that added most of the features above, and
  [Apollo](https://github.com/ClassicOldSong/Apollo), the host it pairs best with.
* **[wjbeckett/artemis](https://github.com/wjbeckett/artemis)** — the port of the Artemis features to
  the Qt desktop client, which the LavArtemis desktop client forks.

Upstream fixes are still merged in from moonlight-qt and moonlight-android.

## Contributing

See [`.github/CONTRIBUTING.md`](.github/CONTRIBUTING.md). AI-assisted code is allowed but reviewed
line by line — and **do not trust AI-generated tests; test each change by hand.**
