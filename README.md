# LavArtemis

An open source game streaming client for [LavApollo](https://github.com/Lavagnou/LavApollo) (and
[Apollo](https://github.com/ClassicOldSong/Apollo) / [Sunshine](https://github.com/LizardByte/Sunshine)
hosts), for **Android and Windows**.

Stream your PC games to a phone, a tablet, a TV box or another PC, at home or over the internet.
The Android client lives in this repository; the desktop client is the
[`desktop/`](https://github.com/Lavagnou/LavArtemis-Qt) submodule. One release ships both, from the
same version.

## Two apps: LavArtemis and LavArtemis Dev

| | LavArtemis (stable) | LavArtemis Dev |
|---|---|---|
| Releases | `vX.Y.Z` | `vX.Y.Z-dev.N`, marked *pre-release* |
| Android package | `com.limelight.lav` | `com.limelight.lav.dev` |
| Interface | the classic Artemis screens | the redesigned interface (below) |

The two install side by side, on Android and on Windows, with their own settings. All new work lands
in **Dev** first; the stable app only moves once a Dev version has been used for a good while. Dev
does not share pairings with the stable app on Android (pair the PC again); on Windows its first
launch copies the stable app's settings once.

### The redesigned interface (Dev)

* **Home**: one PC at a time, with the running session staged on top (Resume / Quit), a *Desktop*
  card, a *Playnite Fullscreen* card and the PC's Playnite library, most recently played first.
  Games without a cover show their name; hidden games can be shown again.
* **My PCs**: add (from the network or by address), pair with a PIN, rename, forget, wake, and
  benchmark a PC.
* **Settings** grouped in pages, with an **expert mode** that reveals the tuning options.
* Three themes, light and dark, a themed in-game menu, and touch, gamepad and keyboard navigation.
* Two touchscreen modes, **Multitouch** and **Trackpad** (long press to drag, two-finger tap for the
  right click, two fingers to scroll), switchable from the in-game menu.
* French first: every screen is translated, and the French texts say « tu ».

Playnite games come from the **LavApollo Link** Playnite extension on the host: picking one starts it
inside Playnite, and *Quit the game* in the in-game menu closes only that game and goes back to
Playnite.

## Downloads and updates

* [All releases](https://github.com/Lavagnou/LavArtemis/releases) (Dev versions are the pre-releases)

| Platform | File |
|---|---|
| Android | `LavArtemis-<version>-android-arm64-v8a.apk`, or `-armeabi-v7a.apk` on 32-bit devices |
| Windows x64 | `-win-installer.exe`, or the portable `-win-x64.zip` |

Releases are built on the maintainer's PC, so they carry Windows x64 only; older stable releases
(up to 21.7.1) also had Windows ARM64 and a Linux AppImage.

Both apps **update themselves**: they check GitHub for the newest release of their own channel and
offer to install it (Android downloads the APK for the device, checks it is signed by the installed
app's key, then hands it to the system installer; Windows downloads and runs the installer). *Skip
this version* silences one version until a manual check.

### Version numbers

Up to 21.x (stable 21.7.1, Dev 21.8.0-dev.9) the version number came from Moonlight. LavArtemis now
counts on its own: Dev restarts at **0.7.0-dev.1**, and the stable app will become **1.0.0**. Because
the new numbers look *lower*, the 21.x apps do not offer them as updates: install the first 0.7 (and
later the first 1.0) by hand, once. On Android it installs over the old app as usual. On Windows the
installer replaces the 21.x version and keeps your settings and pairings (no need to uninstall); the
old 21.x entry stays once in *Installed apps* and can be uninstalled safely. From then on, an
installer older than the installed version refuses to run.

### Which Android APK?

Take `arm64-v8a` unless the device is 32-bit. Android TV hardware often is, even on 64-bit silicon:
a Fire TV Stick 4K Max reports `armeabi-v7a` and nothing else, so the arm64 APK refuses to install
there with `INSTALL_FAILED_NO_MATCHING_ABIS`. When in doubt, `adb shell getprop
ro.product.cpu.abilist` tells you what the device accepts. The in-app updater picks the right one.

LavArtemis uses its own application IDs, so it installs alongside stock Moonlight or Artemis.

## What this fork adds

### Streaming

On both clients unless noted:

* **VRR pacing** — frames are presented at the time their host capture timestamp asks for, so the
  stream follows the game's cadence on adaptive-refresh screens.
* **PyroWave** — a GPU wavelet codec for wired networks (hundreds of Mbit/s, sub-millisecond
  decoding); arm64 Android and Windows.
* **Settings benchmark** — streams the same test scene with several settings and scores latency and
  smoothness, then applies the best ones.
* **Microphone passthrough** — the client's microphone becomes a virtual microphone on the host.
* **Stream at the host display's mode** when joining a session or streaming without a virtual
  display, instead of letterboxing inside the video.
* **Frame pacing metrics** (p50/p95/p99 over 512 frames) and a **performance CSV log** with the same
  columns on both clients.
* Thread priority boosts, link-time optimisation of the native core, a configurable audio buffer cap.
* Android: ADPF performance hints, AV1 in Auto mode only on whitelisted hardware decoders, sustained
  performance mode and thermal warnings.

### Android only

* A **voice push-to-talk button** that holds a key on the host while pressed (for dictation tools).
* **Pinch to zoom** in Trackpad mode, with the view following the cursor.
* The stream can **follow the phone's rotation** while the PC's screen stays as it is.

### Desktop only

* **Multi-screen**: LavApollo emulates one virtual display per monitor of the client PC.
* A **gamepad mapper** on a clickable controller diagram, and a guessed layout for gamepads SDL does
  not know.

### Inherited from Artemis

Custom virtual gamepads, custom resolutions and bitrates, portrait mode, external display support,
settings profiles, `art://` shortcuts, keyboard macros, view pan/zoom, Apollo virtual display, server
commands, clipboard sync, OTP pairing, and SBS 3D for external displays via MiDaS (Android only).
Profiles, keyboard macros and `art://` links use **identical file formats** on both clients.

## Building

Both clients need their submodules first.

### Android

```sh
git submodule update --init --recursive app/src/main/jni/moonlight-core/moonlight-common-c
# add ndk.dir=<path to NDK 27.0.12077973> to local.properties, or let Android Studio handle it
./gradlew assembleNonRoot_gameRelease
```

A version containing `-dev.` (`lavVersionName` at the top of `app/build.gradle`) builds LavArtemis Dev.

### Desktop

Lives in the [`desktop/`](https://github.com/Lavagnou/LavArtemis-Qt) submodule. Needs Qt 6.11.

```sh
git submodule update --init --recursive desktop
cd desktop
powershell ./setup-deps.ps1        # Windows
scripts\build-arch.bat Release x64
```

On Linux: `qmake6 lavartemis.pro && make release`.

### Releases

`scripts/local-release.ps1` builds the signed APKs and the Windows x64 installer on the maintainer's
PC and publishes the GitHub release. The GitHub Actions release workflow is kept for manual runs only.

## Lineage and credits

LavArtemis stands on three projects, and is GPLv3 like all of them:

* **[Moonlight](https://github.com/moonlight-stream)** — the original client, by Cameron Gutman,
  Diego Waxemberg, Aaron Neyer and Andrew Hennessy. Started as a student project at
  [Case Western](http://case.edu) and [MHacks](http://mhacks.org). Both LavArtemis clients descend
  from it: [moonlight-android](https://github.com/moonlight-stream/moonlight-android) and
  [moonlight-qt](https://github.com/moonlight-stream/moonlight-qt).
* **[Artemis](https://github.com/ClassicOldSong/moonlight-android)** by ClassicOldSong (formerly
  Moonlight Noir) — the Android fork that added most of the inherited features above, and
  [Apollo](https://github.com/ClassicOldSong/Apollo), the host LavApollo forks.
* **[wjbeckett/artemis](https://github.com/wjbeckett/artemis)** — the port of the Artemis features to
  the Qt desktop client, which the LavArtemis desktop client forks.

PyroWave is by [Themaister](https://github.com/Themaister/pyrowave); its streaming protocol and the
desktop VRR pacer come from [Nonary](https://github.com/Nonary)'s Vibepollo and moonlight-qt fork.
Upstream fixes are still merged in from moonlight-qt and moonlight-android.

## Contributing

See [`.github/CONTRIBUTING.md`](.github/CONTRIBUTING.md). AI-assisted code is allowed but reviewed
line by line — and **do not trust AI-generated tests; test each change by hand.**
