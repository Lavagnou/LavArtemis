// Android vector drawables of the LavArtemis icon (adaptive layers + notification icon).
// Same shapes as gen.mjs, written as VectorDrawable paths.
import fs from 'node:fs';
import path from 'node:path';

const OUT = path.resolve('out/android');
fs.rmSync(OUT, { recursive: true, force: true });
const write = (rel, text) => {
  const p = path.join(OUT, rel);
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, text);
};

const circle = (cx, cy, r) => `M${cx - r},${cy} a${r},${r} 0 1,0 ${2 * r},0 a${r},${r} 0 1,0 ${-2 * r},0 Z`;
const SCREEN = 'M24,22 H76 A8,8 0 0,1 84,30 V62 A8,8 0 0,1 76,70 H24 A8,8 0 0,1 16,62 V30 A8,8 0 0,1 24,22 Z';
const STAND = 'M50,70 V81 M40,82 H60';
// Monochrome Dev layer: the pill is only an outline there, so the foot stops where the coloured pill's border hides it.
const STAND_MONO = 'M50,70 V81 M40,82 H51.5';
const CRESCENT = 'M49.62,18 A32,32 0 1,0 79.4,62.64 A27,27 0 1,1 49.62,18 Z';
const PLAY = 'M57,33 L57,51 L71,42 Z';
const STARS = `${circle(22, 24, 2.4)} ${circle(85, 30, 2)}`;
const PILL = 'M62.5,74 H81.5 A8.5,8.5 0 0,1 81.5,91 H62.5 A8.5,8.5 0 0,1 62.5,74 Z';
const DEV_LETTERS = 'M62.6,79 V86 H64.6 C66.8,86 67.6,84.5 67.6,82.5 C67.6,80.5 66.8,79 64.6,79 Z M74.4,79 H69.8 V86 H74.4 M69.8,82.5 H73.6 M76.2,79 L79,86 L81.8,79';

// Adaptive icon: 108 dp canvas = 100 viewport units. The mark is drawn the way the round
// icon of the board shows it (scaled 0.76 around 50,52), then fitted to the 72 dp visible
// area: one tile unit = 0.7937 viewport units. Net: scale 0.6032, translate 19.84,20.22.
const MARK_GROUP = 'android:scaleX="0.6032" android:scaleY="0.6032" android:translateX="19.84" android:translateY="20.22"';

const vector = (body, extraNs = '') => `<?xml version="1.0" encoding="utf-8"?>
<!-- Generated from LavArtemis/docs/branding (generate-android.mjs). Do not edit by hand. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"${extraNs}
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="100"
    android:viewportHeight="100">
${body}
</vector>
`;

const markBody = ({ s, p, badge }) => `    <group ${MARK_GROUP}>
        <path android:pathData="${SCREEN}" android:strokeColor="${s}" android:strokeWidth="5" android:strokeLineJoin="round" />
        <path android:pathData="${badge === 'mono' ? STAND_MONO : STAND}" android:strokeColor="${s}" android:strokeWidth="5" android:strokeLineCap="round" />
        <group android:scaleX="0.55" android:scaleY="0.55" android:translateX="22.5" android:translateY="18.5">
            <path android:pathData="${CRESCENT}" android:fillColor="${s}" />
            <path android:pathData="${PLAY}" android:fillColor="${p}" android:strokeColor="${p}" android:strokeWidth="3" android:strokeLineJoin="round" />
            <path android:pathData="${STARS}" android:fillColor="${s}" />
        </group>${badge === 'mono' ? `
        <path android:pathData="${PILL}" android:strokeColor="${s}" android:strokeWidth="2.2" />
        <path android:pathData="${DEV_LETTERS}" android:strokeColor="${s}" android:strokeWidth="1.9" android:strokeLineCap="round" android:strokeLineJoin="round" />` : badge ? `
        <path android:pathData="${PILL}" android:fillColor="#4ADE80" android:strokeColor="${badge}" android:strokeWidth="3" />
        <path android:pathData="${DEV_LETTERS}" android:strokeColor="#0E1222" android:strokeWidth="1.9" android:strokeLineCap="round" android:strokeLineJoin="round" />` : ''}
    </group>`;

// Tile coordinate t (0..100) -> background viewport coordinate.
const bg = (t) => +(50 + (t - 50) * 0.7937).toFixed(2);

// ---- stable (src/main/res)
write('main/drawable/ic_launcher_fg.xml', vector(markBody({ s: '#DDE6F5', p: '#8FB4FF' })));
write('main/drawable/ic_launcher_mono.xml', vector(markBody({ s: '#FFFFFF', p: '#FFFFFF' })));
write('main/drawable/ic_launcher_bg.xml', vector(`    <path android:pathData="M0,0 H100 V100 H0 Z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="${bg(0)}" android:startY="${bg(0)}" android:endX="${bg(30)}" android:endY="${bg(100)}">
                <item android:offset="0" android:color="#FF25325A" />
                <item android:offset="1" android:color="#FF0E1222" />
            </gradient>
        </aapt:attr>
    </path>`, '\n    xmlns:aapt="http://schemas.android.com/aapt"'));

// ---- Dev (src/dev/res): blueprint tile + grid, white drawing, green DEV pill.
let gridPath = '';
for (let t = -10; t <= 110; t += 10) {
  const v = bg(t);
  if (v > 0 && v < 100) gridPath += `M${v},0 V100 M0,${v} H100 `;
}
write('dev/drawable/ic_launcher_fg.xml', vector(markBody({ s: '#FFFFFF', p: '#9FD3FF', badge: '#1D4E89' })));
// Themed icons (Android 13+) draw only the monochrome layer, tinted: without its own, the Dev showed the
// stable mark there. Same mark + the DEV pill as an outline with its letters (a filled pill would hide them).
write('dev/drawable/ic_launcher_mono.xml', vector(markBody({ s: '#FFFFFF', p: '#FFFFFF', badge: 'mono' })));
write('dev/drawable/ic_launcher_bg.xml', vector(`    <path android:pathData="M0,0 H100 V100 H0 Z" android:fillColor="#1D4E89" />
    <path android:pathData="${gridPath.trim()}" android:strokeColor="#FFFFFF" android:strokeAlpha="0.18" android:strokeWidth="0.48" />`));

// ---- notification small icon: white silhouette of the mark, 24 dp.
write('main/drawable/app_icon.xml', `<?xml version="1.0" encoding="utf-8"?>
<!-- Generated from LavArtemis/docs/branding (generate-android.mjs). Notification icon: white silhouette. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="100"
    android:viewportHeight="100">
    <path android:pathData="${SCREEN}" android:strokeColor="#FFFFFFFF" android:strokeWidth="6" android:strokeLineJoin="round" />
    <path android:pathData="${STAND}" android:strokeColor="#FFFFFFFF" android:strokeWidth="6" android:strokeLineCap="round" />
    <group android:scaleX="0.55" android:scaleY="0.55" android:translateX="22.5" android:translateY="18.5">
        <path android:pathData="${CRESCENT}" android:fillColor="#FFFFFFFF" />
        <path android:pathData="${PLAY}" android:fillColor="#FFFFFFFF" android:strokeColor="#FFFFFFFF" android:strokeWidth="3" android:strokeLineJoin="round" />
    </group>
</vector>
`);

console.log('android ok');
