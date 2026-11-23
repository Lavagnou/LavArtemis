# LavArtemis / LavApollo icons

The icons of the two apps come from this folder. Board validated on 2026-11-23 ("Planche finale",
piste 5c / 5d).

- **The idea**: the same computer screen for both apps, with the moon for the client (LavArtemis,
  a nod to Moonlight) and the sun for the host (LavApollo, a nod to Sunshine).
- **Main logo (5c)**: the screen drawn as an outline.
- **"Session in progress" variant (5d)**: the lit screen. It is used wherever an icon can change during
  a stream.
- **"Lav"** comes from a family name, not from lava: no lava, ember or volcano imagery.

| File (`svg/`) | Used for |
|---|---|
| `lavartemis.svg` | client logo (night-blue tile, silver screen and moon, blue play) |
| `lavartemis-session.svg` | client stream window (lit screen) |
| `lavartemis-dev.svg`, `lavartemis-dev-session.svg` | LavArtemis Dev: blueprint tile `#1D4E89` + light grid, white drawing, green `DEV` pill |
| `lavapollo.svg` | host logo and idle tray icon (gold tile, navy screen and sun) |
| `lavapollo-playing.svg` | tray: a client is streaming (lit screen, play) |
| `lavapollo-pausing.svg` | tray: stream paused, the app still runs (lit screen, pause bars) |
| `lavapollo-locked.svg` | tray notification of a pairing request (padlock badge) |
| `lavartemis-silhouette.svg` | white silhouette (Android notification icon) |

## Regenerating

```sh
cd docs/branding
npm install            # @resvg/resvg-js, prebuilt binaries
npm run icons          # writes out/ (SVG, PNG 16..1024, ICO) and out/android/ (vector drawables)
python -I generate-banners.py out <LavArtemis-Qt>/app/res/fonts/SpaceGrotesk.ttf   # Android TV banners
```

Shapes and colours live in `generate-icons.mjs` (and their VectorDrawable copy in
`generate-android.mjs`): change them there, never in a rendered file. Then copy the outputs:

| Output | Destination |
|---|---|
| `out/android/main/drawable/*.xml` | `app/src/main/res/drawable/` (`ic_launcher_bg/fg/mono`, `app_icon`) |
| `out/android/dev/drawable/*.xml` | `app/src/dev/res/drawable/` |
| `out/lavartemis-{48,72,96,144,192}.png` | `app/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png` (legacy icon, API < 26); the `-dev-` ones to `app/src/dev/res/` |
| `out/atv_banner.png`, `out/atv_banner_dev.png` | `drawable-xhdpi/atv_banner.png` (main, dev) |
| `lavartemis*.svg`, `lavartemis.ico`, `lavartemis-dev.ico`, `lavartemis.icns`, `lavartemis-64.png` / `-dev-64.png` (installer logo `artemis_wix*.png`), `lavartemis-128.png` (`artemis.png`) | LavArtemis-Qt `app/res/`, `app/` |
| `lavapollo.{svg,ico,icns}`, `lavapollo-1024.png` | LavApollo root `apollo.*` |
| `lavapollo*.{svg,ico}`, `-1024/-16/-45.png` | LavApollo `src_assets/common/assets/web{,-v2}/public/images/` as `apollo-<state>.*`, `logo-apollo*.*`, `apollo.ico` |
| `lavapollo-128.png` | LavApollo `tools/playnite/icon.png` (the LavApollo Link extension in Playnite) |
| `lavartemis-braise.svg`, `lavartemis-affiche-{light,dark}.svg` | LavArtemis-Qt `app/res/` (logo of the v2 header per theme; Ludique keeps `lavartemis.svg`) |

## Format choices

- **ICO**: classic 32-bit DIB entries up to 128 px and PNG at 256 px. PNG-only icons are refused by some
  tools (older NSIS, windres). Sizes 16, 20, 24, 32, 40, 48, 64, 128, 256: the tray and taskbar pick
  20 / 40 at 125 % and 200 % scaling.
- **No `clipPath`**: the Dev grid lines are cut to the rounded tile by hand. Android vector drawables
  have no clip on paths, and QtSvg renders plain paths most reliably.
- **Android adaptive icon**: 108 dp canvas = 100 viewport units. The mark is shrunk as on the round icon
  of the board (0.76 around 50,52) and fitted to the 72 dp visible area: group scale 0.6032, translate
  19.84, 20.22. The Dev pill stays on the foreground layer. The themed (monochrome) icon has no pill,
  because a one-colour pill becomes a blot.
- **Android TV banner**: Space Grotesk Bold (OFL, embedded in LavArtemis-Qt) for the wordmark.
