// Generates the LavArtemis / LavApollo icon masters (SVG) and their PNG / ICO renders,
// from the shapes validated on the "Planche finale" board (piste 5c / 5d).
import { Resvg } from '@resvg/resvg-js';
import fs from 'node:fs';
import path from 'node:path';

const OUT = path.resolve('out');
fs.rmSync(OUT, { recursive: true, force: true });
fs.mkdirSync(OUT, { recursive: true });

// ---------------------------------------------------------------- shapes (100 x 100)
const CRESCENT = 'M49.62 18 A32 32 0 1 0 79.4 62.64 A27 27 0 1 1 49.62 18 Z';
const SCREEN = '<rect x="16" y="22" width="68" height="48" rx="8"';
const STAND = 'M50 70 V81 M40 82 H60';
const RAYS = 'M77 50 H87 M69.1 69.1 L76.2 76.2 M50 77 V87 M30.9 69.1 L23.8 76.2 M23 50 H13 M30.9 30.9 L23.8 23.8 M50 23 V13 M69.1 30.9 L76.2 23.8';
const MOON_T = 'translate(50 46) scale(0.55) translate(-50 -50)';
const SUN_T = 'translate(50 46) scale(0.5) translate(-50 -50)';

const defs = `<defs>
<linearGradient id="night" x1="0" y1="0" x2="0.3" y2="1"><stop offset="0" stop-color="#25325A"/><stop offset="1" stop-color="#0E1222"/></linearGradient>
<linearGradient id="sun" x1="0" y1="0" x2="0.3" y2="1"><stop offset="0" stop-color="#FFD866"/><stop offset="1" stop-color="#F2AE1E"/></linearGradient>
</defs>`;

const tile = (fill) => `<rect width="100" height="100" rx="22" fill="${fill}"/>`;
// Grid lines of the Dev tile, each cut to the rounded tile (no clipPath: QtSvg and Android
// vector drawables handle plain paths best).
export const GRID_PATH = (() => {
  const r = 22;
  const inset = (t) => { // how far a line at coordinate t starts inside the rounded corner
    const dd = t < r ? r - t : t > 100 - r ? t - (100 - r) : 0;
    return +(r - Math.sqrt(r * r - dd * dd)).toFixed(2);
  };
  let d = '';
  for (let i = 10; i < 100; i += 10) {
    const a = inset(i);
    d += `M${i} ${a} V${+(100 - a).toFixed(2)} M${a} ${i} H${+(100 - a).toFixed(2)} `;
  }
  return d.trim();
})();
const grid = () => `<path d="${GRID_PATH}" stroke="#FFFFFF" stroke-width="0.6" stroke-opacity="0.18" fill="none"/>`;

// Moon content of the client logo (crescent, play in its hollow, two stars).
const moon = (moonFill, playFill) => `<g transform="${MOON_T}">
<path d="${CRESCENT}" fill="${moonFill}"/>
<path d="M57 33 L57 51 L71 42 Z" fill="${playFill}" stroke="${playFill}" stroke-width="3" stroke-linejoin="round"/>
<circle cx="22" cy="24" r="2.4" fill="${moonFill}"/>
<circle cx="85" cy="30" r="2" fill="${moonFill}"/>
</g>`;

const screenOutline = (c) => `<g fill="none" stroke="${c}" stroke-linecap="round" stroke-linejoin="round">
${SCREEN} stroke-width="5"/>
<path d="${STAND}" stroke-width="5"/>
</g>`;
const screenLit = (c) => `${SCREEN} fill="${c}" stroke="${c}" stroke-width="5" stroke-linejoin="round"/>
<path d="${STAND}" fill="none" stroke="${c}" stroke-width="5" stroke-linecap="round"/>`;

// Client marks
const artemisOff = (s, p) => screenOutline(s) + moon(s, p);
const artemisOn = (s, m, p) => screenLit(s) + moon(m, p);

// Host marks
const sun = (c) => `<g transform="${SUN_T}">
<circle cx="50" cy="50" r="19" fill="${c}"/>
<path d="${RAYS}" fill="none" stroke="${c}" stroke-width="6.5" stroke-linecap="round"/>
</g>`;
const sunPlay = (c) => `<g transform="${SUN_T}"><path d="M45 41 L45 59 L60 50 Z" fill="${c}" stroke="${c}" stroke-width="2.5" stroke-linejoin="round"/></g>`;
const sunPause = (c) => `<g transform="${SUN_T}" fill="${c}"><rect x="42.5" y="41" width="5.5" height="18" rx="1.5"/><rect x="52" y="41" width="5.5" height="18" rx="1.5"/></g>`;
const apolloOff = (s, p) => screenOutline(s) + sun(s) + sunPlay(p);
const apolloOn = (s, m, p) => screenLit(s) + sun(m) + sunPlay(p);
const apolloPaused = (s, m, p) => screenLit(s) + sun(m) + sunPause(p);
const lock = () => `<circle cx="78" cy="78" r="17" fill="#141A2E" stroke="#FFFFFF" stroke-width="3"/>
<path d="M73 77 V73.5 A5 5 0 0 1 83 73.5 V77" fill="none" stroke="#FFFFFF" stroke-width="2.8" stroke-linecap="round"/>
<rect x="70.5" y="76.5" width="15" height="11" rx="2.5" fill="#FFFFFF"/>`;

// Green "DEV" pill, letters drawn as strokes (no font needed).
const devBadge = (ring) => `<rect x="54" y="74" width="36" height="17" rx="8.5" fill="#4ADE80" stroke="${ring}" stroke-width="3"/>
<path d="M62.6 79 V86 H64.6 C66.8 86 67.6 84.5 67.6 82.5 C67.6 80.5 66.8 79 64.6 79 Z M74.4 79 H69.8 V86 H74.4 M69.8 82.5 H73.6 M76.2 79 L79 86 L81.8 79" fill="none" stroke="#0E1222" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>`;

const svg = (body) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="1024" height="1024">${defs}${body}</svg>`;

const DEV_TILE = '#1D4E89';
export const ICONS = {
  // LavArtemis (client)
  'lavartemis': svg(tile('url(#night)') + artemisOff('#DDE6F5', '#8FB4FF')),
  'lavartemis-session': svg(tile('url(#night)') + artemisOn('#DDE6F5', '#172036', '#3F6FD8')),
  'lavartemis-dev': svg(tile(DEV_TILE) + grid() + artemisOff('#FFFFFF', '#9FD3FF') + devBadge(DEV_TILE)),
  'lavartemis-dev-session': svg(tile(DEV_TILE) + grid() + artemisOn('#FFFFFF', DEV_TILE, '#2F7FD8') + devBadge(DEV_TILE)),
  // LavApollo (host)
  'lavapollo': svg(tile('url(#sun)') + apolloOff('#141A2E', '#F8BC40')),
  'lavapollo-playing': svg(tile('url(#sun)') + apolloOn('#141A2E', '#F8BC40', '#141A2E')),
  'lavapollo-pausing': svg(tile('url(#sun)') + apolloPaused('#141A2E', '#F8BC40', '#141A2E')),
  'lavapollo-locked': svg(tile('url(#sun)') + apolloOff('#141A2E', '#F8BC40') + lock()),
};

// ---------------------------------------------------------------- rendering
function png(svgText, size) {
  const r = new Resvg(svgText, { fitTo: { mode: 'width', value: size }, background: 'rgba(0,0,0,0)' });
  return r.render().asPng();
}

// Classic 32-bit DIB entry (BITMAPINFOHEADER + bottom-up BGRA + AND mask), read by every tool
// that handles .ico (rc.exe, windres, NSIS, WiX).
function dib(svgText, s) {
  const img = new Resvg(svgText, { fitTo: { mode: 'width', value: s } }).render();
  const rgba = img.pixels;
  const maskRow = Math.ceil(s / 32) * 4;
  const buf = Buffer.alloc(40 + s * s * 4 + maskRow * s);
  buf.writeUInt32LE(40, 0);
  buf.writeInt32LE(s, 4);
  buf.writeInt32LE(s * 2, 8);
  buf.writeUInt16LE(1, 12);
  buf.writeUInt16LE(32, 14);
  buf.writeUInt32LE(0, 16);
  buf.writeUInt32LE(s * s * 4 + maskRow * s, 20);
  let o = 40;
  for (let y = s - 1; y >= 0; y--) {
    for (let x = 0; x < s; x++) {
      const i = (y * s + x) * 4;
      buf[o++] = rgba[i + 2];
      buf[o++] = rgba[i + 1];
      buf[o++] = rgba[i];
      buf[o++] = rgba[i + 3];
    }
  }
  return buf; // AND mask left at zero: the alpha channel decides transparency
}

// ICO: DIB entries up to 128 px, PNG for 256 px (the usual layout since Windows Vista).
function ico(svgText, sizes) {
  const images = sizes.map((s) => ({ s, data: s >= 256 ? png(svgText, s) : dib(svgText, s) }));
  const header = Buffer.alloc(6);
  header.writeUInt16LE(0, 0);
  header.writeUInt16LE(1, 2);
  header.writeUInt16LE(images.length, 4);
  const entries = [];
  let offset = 6 + 16 * images.length;
  for (const { s, data } of images) {
    const e = Buffer.alloc(16);
    e.writeUInt8(s >= 256 ? 0 : s, 0);
    e.writeUInt8(s >= 256 ? 0 : s, 1);
    e.writeUInt8(0, 2);
    e.writeUInt8(0, 3);
    e.writeUInt16LE(1, 4);
    e.writeUInt16LE(32, 6);
    e.writeUInt32LE(data.length, 8);
    e.writeUInt32LE(offset, 12);
    offset += data.length;
    entries.push(e);
  }
  return Buffer.concat([header, ...entries, ...images.map((i) => i.data)]);
}

const ICO_SIZES = [16, 20, 24, 32, 40, 48, 64, 128, 256];
for (const [name, text] of Object.entries(ICONS)) {
  fs.writeFileSync(path.join(OUT, `${name}.svg`), text + '\n');
  fs.writeFileSync(path.join(OUT, `${name}.ico`), ico(text, ICO_SIZES));
  for (const s of [16, 45, 48, 64, 72, 96, 128, 144, 192, 256, 512, 1024]) {
    fs.writeFileSync(path.join(OUT, `${name}-${s}.png`), png(text, s));
  }
}

// Notification small icon (Android): white silhouette of the client mark, no tile.
const silhouette = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="1024" height="1024">${artemisOff('#FFFFFF', '#FFFFFF')}</svg>`;
fs.writeFileSync(path.join(OUT, 'lavartemis-silhouette.svg'), silhouette + '\n');

// Banner backgrounds (Android TV, 320 x 180), the wordmark is added by banner.py.
const bannerBg = (fill, withGrid) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 180" width="320" height="180">
<defs><linearGradient id="night" x1="0" y1="0" x2="0.3" y2="1"><stop offset="0" stop-color="#25325A"/><stop offset="1" stop-color="#0E1222"/></linearGradient></defs>
<rect width="320" height="180" fill="${fill}"/>${withGrid ? (() => { let d = ''; for (let x = 16; x < 320; x += 16) d += `M${x} 0 V180 `; for (let y = 10; y < 180; y += 16) d += `M0 ${y} H320 `; return `<path d="${d}" stroke="#FFFFFF" stroke-width="0.8" opacity="0.14" fill="none"/>`; })() : ''}</svg>`;
fs.writeFileSync(path.join(OUT, 'banner-bg.png'), new Resvg(bannerBg('url(#night)', false)).render().asPng());
fs.writeFileSync(path.join(OUT, 'banner-bg-dev.png'), new Resvg(bannerBg(DEV_TILE, true)).render().asPng());

console.log('ok', fs.readdirSync(OUT).length, 'files');
