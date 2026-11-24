// Generates the Windows installer window of LavArtemis-Qt (WiX 7 standard bootstrapper application),
// direction "A" validated on the prototype canvas (page V7, 2026-11-24): the whole window, title bar
// included, takes the background of the logo (night gradient for LavArtemis, blueprint grid for
// LavArtemis Dev), the logo on the left, Space Grotesk titles, Inter text, pill buttons.
//
// Run generate-installer-fonts.py first (it writes out/installer/fonts/). Then this script writes
// out/installer/<dev|stable>/: thm.xml (the theme, ThemeFile of Bundle.wxs), payloads.wxi (one Payload
// per image and font, included by Bundle.wxs) and the PNGs. Copy out/installer/{dev,stable,fonts} to
// LavArtemis-Qt wix/LavArtemisSetup/theme/. The labels' French strings are LavArtemis-Qt theme/thm.wxl;
// the strings drawn into images (buttons, checkbox, link, title bar) are in this file.
//
// How the WiX theme engine (thmutil) draws, and why the images look like this:
// - The window is a WS_POPUP: no Windows title bar (thmutil lets it be dragged by any non-control
//   point). The title bar of the mockup is drawn in the background, its buttons are image buttons;
//   minimize, the checkbox and grey text antialiasing come from LavArtemis-Qt's BAFunctions DLL.
// - The background is Theme/@ImageFile, drawn at its 100 % size and scaled by the engine on a high-DPI
//   screen (no alternate resolution). It must be one pixel wider and taller than the window: thmutil
//   refuses SourceX + Width equal to the image width.
// - Labels have no background colour: their text is drawn straight on that image, by GDI with the
//   fonts the DLL loads privately. Their text must not change while a page is shown: nothing repaints
//   the background behind it.
// - Image buttons, image controls and the logo are child windows: their PNG is drawn into the child
//   alone, so a transparent corner would show garbage. Every image carries the crop of the background
//   it sits on, rendered at 200 % (the engine scales it down to the screen's DPI). Button text is drawn
//   into the images too (thmutil would draw it with ClearType, coloured fringes on a pill).
// - The progress bar image is 4 px wide: left edge, filled, empty, right edge, stretched. The rounded
//   ends are two small image controls on each side of it.
import { Resvg } from '@resvg/resvg-js';
import fs from 'node:fs';
import path from 'node:path';

const FONT_DIR = path.resolve('out/installer/fonts');
if (!fs.existsSync(FONT_DIR)) throw new Error('Run generate-installer-fonts.py first (out/installer/fonts).');
const FONT_FILES = fs.readdirSync(FONT_DIR).filter((f) => f.endsWith('.ttf')).sort();
const FAM = { title: 'LavInstall Space Bold', text: 'LavInstall Inter', medium: 'LavInstall Inter Medium', bold: 'LavInstall Inter Bold' };

const W = 620; // window, as in the mockup: a 32 px title bar over a 620 x 388 body
const CAP = 32;
const H = CAP + 388;
const Y = (y) => CAP + y; // body coordinates to window coordinates
const SCALE = 2;

const CHANNELS = {
  dev: {
    name: 'LavArtemis Dev',
    folder: 'C:\\Program Files\\LavArtemis Dev',
    logo: 'svg/lavartemis-dev.svg',
    body: `<rect y="${CAP}" width="${W + 1}" height="${H + 1 - CAP}" fill="#1D4E89"/>` + grid(),
    cap: '#173F70', capText: '#DCE8F7',
    text: '#FFFFFF', sub: '#CFE0F5',
    primary: { fill: '#FFFFFF', hover: '#E6EEF9', pressed: '#CFDDF0', text: '#1D4E89' },
    go: { fill: '#4ADE80', hover: '#6AE896', pressed: '#3BC46F', text: '#0E1222' },
    ghostBorder: 'rgba(255,255,255,0.45)',
    ring: '#FFFFFF',
    check: { box: '#FFFFFF', hover: '#E6EEF9', mark: '#1D4E89', empty: 'rgba(255,255,255,0.7)' },
    bar: { fill: '#FFFFFF', track: '#4F75A3' }, // track = 22 % white over #1D4E89
    field: '#346095', // 10 % white over #1D4E89, opaque: the edit box paints it behind its text
  },
  stable: {
    name: 'LavArtemis',
    folder: 'C:\\Program Files\\LavArtemis Game Streaming',
    logo: 'svg/lavartemis.svg',
    body: `<defs><linearGradient id="bg" gradientUnits="userSpaceOnUse" x1="0" y1="${CAP}" x2="${W * 0.36}" y2="${H}">
<stop offset="0" stop-color="#25325A"/><stop offset="0.85" stop-color="#0E1222"/></linearGradient></defs>
<rect y="${CAP}" width="${W + 1}" height="${H + 1 - CAP}" fill="url(#bg)"/>`,
    cap: '#141C33', capText: '#C9D2EA',
    text: '#FFFFFF', sub: '#AEB8D6',
    primary: { fill: '#DDE6F5', hover: '#EEF3FB', pressed: '#C5D2EA', text: '#0E1222' },
    go: { fill: '#8FB4FF', hover: '#A8C5FF', pressed: '#7AA2F0', text: '#0E1222' },
    ghostBorder: 'rgba(255,255,255,0.35)',
    ring: '#DDE6F5',
    check: { box: '#8FB4FF', hover: '#A8C5FF', mark: '#0E1222', empty: '#8FB4FF' },
    bar: { fill: '#A9C4FF', track: '#2A3150' },
    field: '#2E3858',
  },
};

function grid() {
  let d = '';
  for (let x = 24; x < W; x += 24) d += `M${x + 0.5} ${CAP} V${H + 1} `;
  for (let y = CAP + 24; y <= H; y += 24) d += `M0 ${y + 0.5} H${W + 1} `;
  return `<path d="${d.trim()}" stroke="#FFFFFF" stroke-opacity="0.09" stroke-width="1" fill="none"/>`;
}

const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const resvgOptions = { font: { fontFiles: FONT_FILES.map((f) => path.join(FONT_DIR, f)), loadSystemFonts: false, defaultFontFamily: FAM.text } };

function render(svgBody, view, scale) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${view.x} ${view.y} ${view.w} ${view.h}" width="${Math.round(view.w * scale)}" height="${Math.round(view.h * scale)}">${svgBody}</svg>`;
  return new Resvg(svg, resvgOptions).render().asPng();
}

function textWidth(s, family, size) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="3000" height="200"><text x="10" y="100" font-family="${family}" font-size="${size}">${esc(s)}</text></svg>`;
  const box = new Resvg(svg, resvgOptions).getBBox();
  return box ? box.width : 0;
}

const text = (x, y, s, family, size, fill, anchor = 'start') =>
  `<text x="${x}" y="${y}" font-family="${family}" font-size="${size}" fill="${fill}" text-anchor="${anchor}">${esc(s)}</text>`;

// ------------------------------------------------------------------ controls
// A button is a pill of the mockup (48 px high, padding 26 / 22 px) inside a control 4 px larger on
// every side, room for the focus ring. Width follows the text, rows are right-aligned at x = 580.
const PILL_H = 48;
const BTN_FONT = 16;
const PLAY = 14; // play icon of the "go" button, then a 10 px gap

function pillWidth(kind, label) {
  const pad = kind === 'ghost' ? 24 : 26;
  return Math.ceil(textWidth(label, FAM.bold, BTN_FONT) + 2 * pad + (kind === 'go' ? PLAY + 10 : 0));
}

// Places buttons left to right, the last one's right edge at 580; yBody = top of the pills.
function row(yBody, specs, right = 580) {
  const out = [];
  for (const s of [...specs].reverse()) {
    const pw = pillWidth(s.kind, s.label);
    const px = right - pw;
    out.unshift({ ...s, x: px - 4, y: Y(yBody) - 4, w: pw + 8, h: PILL_H + 8 });
    right = px - 12;
  }
  return out;
}

function buttonSvg(b, state, c) {
  const x = b.x + 4, y = b.y + 4, w = b.w - 8, h = b.h - 8, rx = h / 2;
  let s = '';
  let fg;
  if (b.kind === 'ghost') {
    const fill = state === 'h' ? 0.12 : state === 's' ? 0.2 : 0;
    const border = state === 'f' ? c.ring : c.ghostBorder;
    s += `<rect x="${x + 1}" y="${y + 1}" width="${w - 2}" height="${h - 2}" rx="${rx - 1}" fill="#FFFFFF" fill-opacity="${fill}" stroke="${border}" stroke-width="2"/>`;
    fg = c.text;
  } else {
    const p = b.kind === 'go' ? c.go : c.primary;
    const fill = state === 'h' ? p.hover : state === 's' ? p.pressed : p.fill;
    s += `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}" fill="${fill}"/>`;
    if (state === 'f') {
      s += `<rect x="${b.x + 1}" y="${b.y + 1}" width="${b.w - 2}" height="${b.h - 2}" rx="${(b.h - 2) / 2}" fill="none" stroke="${c.ring}" stroke-width="1.6"/>`;
    }
    fg = p.text;
  }
  const base = y + h / 2 + BTN_FONT * 0.36;
  if (b.kind === 'go') {
    const tw = textWidth(b.label, FAM.bold, BTN_FONT);
    const start = x + (w - (PLAY + 10 + tw)) / 2;
    const cy = y + h / 2;
    s += `<path d="M${start + 2.5} ${cy - 6} L${start + 12.5} ${cy} L${start + 2.5} ${cy + 6} Z" fill="${fg}" stroke="${fg}" stroke-width="1.5" stroke-linejoin="round"/>`;
    s += text(start + PLAY + 10, base, b.label, FAM.bold, BTN_FONT, fg);
  } else {
    s += text(x + w / 2, base, b.label, FAM.bold, BTN_FONT, fg, 'middle');
  }
  return s;
}

// Title bar buttons (46 x 32, Windows 11 metrics).
const MIN = { x: W - 92, y: 0, w: 46, h: CAP };
const CLOSE = { x: W - 46, y: 0, w: 46, h: CAP };
function capButtonSvg(r, isClose, state, c) {
  let s = '';
  let fg = c.capText;
  if (state === 'h' || state === 's') {
    const fill = isClose ? (state === 's' ? '#A82518' : '#C42B1C') : '#FFFFFF';
    const opacity = isClose ? 1 : state === 's' ? 0.18 : 0.1;
    s += `<rect x="${r.x}" y="${r.y}" width="${r.w}" height="${r.h}" fill="${fill}" fill-opacity="${opacity}"/>`;
    if (isClose) fg = '#FFFFFF';
  }
  const cx = r.x + r.w / 2, cy = r.h / 2;
  s += isClose
    ? `<path d="M${cx - 5} ${cy - 5} L${cx + 5} ${cy + 5} M${cx + 5} ${cy - 5} L${cx - 5} ${cy + 5}" stroke="${fg}" stroke-width="1.1" stroke-linecap="round"/>`
    : `<path d="M${cx - 5} ${cy + 0.5} H${cx + 5}" stroke="${fg}" stroke-width="1.1" stroke-linecap="round"/>`;
  return s;
}

// Checkbox of the mockup (box 20 px, radius 6, gap 10, Inter 500 14 px): two buttons, checked and
// unchecked, swapped by the DLL; the whole row is clickable.
const CHECK_LABEL = 'Créer un raccourci sur le Bureau';
function checkRect() {
  const tw = textWidth(CHECK_LABEL, FAM.medium, 14);
  return { x: 36, y: Y(210), w: Math.ceil(4 + 20 + 10 + tw + 8), h: 28 };
}
function checkSvg(r, on, state, c) {
  const bx = r.x + 4, by = r.y + 4;
  let s = '';
  if (on) {
    s += `<rect x="${bx}" y="${by}" width="20" height="20" rx="6" fill="${state === 'h' || state === 's' ? c.check.hover : c.check.box}"/>`;
    s += `<path d="M${bx + 5.4} ${by + 10.2} l3.6 3.6 l6.4 -7.2" fill="none" stroke="${c.check.mark}" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>`;
  } else {
    const fill = state === 'h' ? 0.12 : state === 's' ? 0.2 : 0;
    s += `<rect x="${bx + 1}" y="${by + 1}" width="18" height="18" rx="5" fill="#FFFFFF" fill-opacity="${fill}" stroke="${c.check.empty}" stroke-width="2"/>`;
  }
  if (state === 'f') s += `<rect x="${bx - 2.5}" y="${by - 2.5}" width="25" height="25" rx="8" fill="none" stroke="${c.ring}" stroke-width="1.4"/>`;
  s += text(bx + 30, by + 10 + 14 * 0.36, CHECK_LABEL, FAM.medium, 14, c.text);
  return s;
}

// "Changer…" link after the folder line (Inter 500 13 px, underlined).
const LINK_LABEL = 'Changer…';
function linkSvg(r, state, c) {
  const fg = state === 'h' || state === 's' ? c.sub : c.text;
  const tw = textWidth(LINK_LABEL, FAM.medium, 13);
  const base = r.y + r.h / 2 + 13 * 0.36;
  let s = text(r.x + 3, base, LINK_LABEL, FAM.medium, 13, fg);
  s += `<path d="M${r.x + 3} ${base + 2.5} H${r.x + 3 + tw}" stroke="${fg}" stroke-width="1"/>`;
  if (state === 'f') s += `<rect x="${r.x + 0.5}" y="${r.y + 0.5}" width="${r.w - 1}" height="${r.h - 1}" rx="4" fill="none" stroke="${c.ring}" stroke-width="1"/>`;
  return s;
}

// ------------------------------------------------------------------ layout
const LOGO = { x: 40, y: Y(44), w: 112, h: 112 };
const TEXT_X = 176;
const TEXT_W = W - TEXT_X - 40;
const BAR = { x: 40, y: Y(316), w: W - 80, h: 8 };
const BUTTONS_Y = 308; // body: 32 px above the bottom, as the mockup

const LAYOUT = {
  install: row(BUTTONS_Y, [{ kind: 'ghost', label: 'Annuler' }, { kind: 'primary', label: 'Installer' }]),
  optionsBrowse: row(164, [{ kind: 'ghost', label: 'Parcourir…' }]), // under the folder field
  options: row(BUTTONS_Y, [{ kind: 'ghost', label: 'Annuler' }, { kind: 'primary', label: 'Valider' }]),
  modify: row(BUTTONS_Y, [{ kind: 'ghost', label: 'Désinstaller' }, { kind: 'ghost', label: 'Réparer' }, { kind: 'primary', label: 'Fermer' }]),
  success: row(BUTTONS_Y, [{ kind: 'ghost', label: 'Fermer' }, { kind: 'go', label: 'Lancer LavArtemis' }]),
  successRestart: row(BUTTONS_Y, [{ kind: 'go', label: 'Redémarrer' }]),
  closeOnly: row(BUTTONS_Y, [{ kind: 'primary', label: 'Fermer' }]),
  failure: row(BUTTONS_Y, [{ kind: 'ghost', label: 'Redémarrer' }, { kind: 'primary', label: 'Fermer' }]),
};

// thmutil colours are BGR hex.
const bgr = (hex) => hex.slice(5, 7) + hex.slice(3, 5) + hex.slice(1, 3);

function logoSvg(file) {
  const src = fs.readFileSync(file, 'utf8');
  const inner = src.replace(/^[\s\S]*?<svg[^>]*>/, '').replace(/<\/svg>\s*$/, '')
    .replace(/id="(\w+)"/g, 'id="logo-$1"').replace(/url\(#(\w+)\)/g, 'url(#logo-$1)');
  return `<g transform="translate(${LOGO.x} ${LOGO.y}) scale(${LOGO.w / 100})">${inner}</g>`;
}

const slug = (s) => s.normalize('NFD').replace(/[^\w]/g, '').toLowerCase();

for (const [channel, c] of Object.entries(CHANNELS)) {
  const out = path.resolve('out/installer', channel);
  fs.rmSync(out, { recursive: true, force: true });
  fs.mkdirSync(out, { recursive: true });
  const files = [];
  const write = (name, data) => { if (!files.includes(name)) { fs.writeFileSync(path.join(out, name), data); files.push(name); } };

  const captionText = text(12, CAP / 2 + 12 * 0.36, `${c.name} — Installation`, FAM.text, 12, c.capText);
  // Every image starts from the whole background and is cropped by its viewBox.
  const background = `<rect width="${W + 1}" height="${CAP}" fill="${c.cap}"/>` + captionText + c.body;
  write('lav-bg.png', render(background, { x: 0, y: 0, w: W + 1, h: H + 1 }, 1));
  write('lav-logo.png', render(background + logoSvg(c.logo), LOGO, SCALE));

  // The 4 states of a control drawn by draw(state) inside rectangle r.
  const states = (prefix, r, draw) => {
    for (const st of ['n', 'h', 's', 'f']) write(`${prefix}-${st}.png`, render(background + draw(st), r, SCALE));
    return prefix;
  };
  const imagesOf = (prefix) => `<ButtonImage ImageFile="${prefix}-n.png" /><ButtonHoverImage ImageFile="${prefix}-h.png" /><ButtonSelectedImage ImageFile="${prefix}-s.png" /><ButtonFocusImage ImageFile="${prefix}-f.png" />`;
  const btn = (b, attrs, inner = '') => {
    const prefix = states(`lav-b-${b.kind}-${slug(b.label)}-${b.x}-${b.y}`, b, (st) => buttonSvg(b, st, c));
    return `            <Button ${attrs} X="${b.x}" Y="${b.y}" Width="${b.w}" Height="${b.h}" TabStop="yes" FontId="6">
                ${imagesOf(prefix)}${inner}
            </Button>`;
  };

  const minPrefix = states('lav-cap-min', MIN, (st) => capButtonSvg(MIN, false, st, c));
  const closePrefix = states('lav-cap-close', CLOSE, (st) => capButtonSvg(CLOSE, true, st, c));
  const ck = checkRect();
  const onPrefix = states('lav-check-on', ck, (st) => checkSvg(ck, true, st, c));
  const offPrefix = states('lav-check-off', ck, (st) => checkSvg(ck, false, st, c));
  // The folder label is drawn by GDI from "Dossier : [InstallFolder] ·"; the link sits right after the
  // default folder (a longer folder chosen by hand is cut by the label's width).
  const folderW = Math.ceil(textWidth(`Dossier : ${c.folder} ·`, FAM.medium, 13));
  const link = { x: 40 + folderW + 3, y: Y(244), w: Math.ceil(textWidth(LINK_LABEL, FAM.medium, 13)) + 8, h: 24 };
  const linkPrefix = states('lav-link', link, (st) => linkSvg(link, st, c));

  write('lav-bar.png', render([c.bar.fill, c.bar.fill, c.bar.track, c.bar.track]
    .map((col, i) => `<rect x="${i}" y="0" width="1" height="8" fill="${col}"/>`).join(''), { x: 0, y: 0, w: 4, h: 8 }, 1));
  const capL = { x: BAR.x, y: BAR.y, w: 4, h: BAR.h };
  const capR = { x: BAR.x + BAR.w - 4, y: BAR.y, w: 4, h: BAR.h };
  write('lav-bar-left.png', render(background + `<path d="M${capL.x + 4} ${capL.y} A4 4 0 0 0 ${capL.x + 4} ${capL.y + 8} Z" fill="${c.bar.fill}"/>`, capL, SCALE));
  write('lav-bar-right.png', render(background + `<path d="M${capR.x} ${capR.y} A4 4 0 0 1 ${capR.x} ${capR.y + 8} Z" fill="${c.bar.track}"/>`, capR, SCALE));

  // Folder field of the Options page: the frame is an image (same height, border and corners family as
  // the ghost buttons), the edit box sits inside it without its 3D frame (removed by the DLL), on the
  // same opaque colour. A Windows edit box cannot draw rounded corners or a translucent fill itself.
  const field = { x: TEXT_X, y: Y(104), w: W - 40 - TEXT_X, h: PILL_H }; // full width: the whole path shows
  write('lav-field.png', render(background + `<rect x="${field.x + 1}" y="${field.y + 1}" width="${field.w - 2}" height="${field.h - 2}" rx="13" fill="${c.field}" stroke="${c.ghostBorder}" stroke-width="2"/>`, field, SCALE));

  const ctx = { c, btn, field, minPrefix, closePrefix, onPrefix, offPrefix, ck, link, folderW, linkPrefix, imagesOf, capL, capR };
  fs.writeFileSync(path.join(out, 'thm.xml'), theme(ctx));
  fs.writeFileSync(path.join(out, 'payloads.wxi'), `<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by LavArtemis docs/branding/generate-installer.mjs: do not edit, regenerate. -->
<Include xmlns="http://wixtoolset.org/schemas/v4/wxs">
${files.map((f) => `  <Payload Name="${f}" SourceFile="theme\\${channel}\\${f}" Compressed="yes" />`).join('\n')}
${FONT_FILES.map((f) => `  <Payload Name="${f}" SourceFile="theme\\fonts\\${f}" Compressed="yes" />`).join('\n')}
</Include>
`);
  console.log(channel, files.length, 'images');
}

// ------------------------------------------------------------------ theme
function theme(x) {
  const { c, btn } = x;
  const F = { body: 0, title: 1, sub: 2, small: 3, edit: 4, check: 5, button: 6 };
  const font = (id, family, h, fg) => `    <Font Id="${id}" Height="-${h}" Weight="400" Foreground="${fg}">${family}</Font>`;
  const title = (inner) => `            <Label X="${TEXT_X}" Y="${Y(46)}" Width="${TEXT_W}" Height="48" FontId="${F.title}" DisablePrefix="yes">${inner}</Label>`;
  const sub = (inner) => `            <Label X="${TEXT_X}" Y="${Y(100)}" Width="${TEXT_W}" Height="48" FontId="${F.sub}" DisablePrefix="yes">${inner}</Label>`;
  const byAction = (base, a = {}) => `<Text>#(loc.${base})</Text>` + Object.entries(a)
    .map(([n, id]) => `<Text Condition="WixBundleAction = ${n}">#(loc.${id})</Text>`).join('');
  const close = '\n                <CloseWindowAction />';
  const UNINSTALL = 'WixBundleAction = 3 OR WixBundleAction = 4';
  const L = LAYOUT;
  const bar = (name) => `            <ImageControl X="${x.capL.x}" Y="${x.capL.y}" Width="${x.capL.w}" Height="${x.capL.h}" ImageFile="lav-bar-left.png" />
            <Progressbar Name="${name}" X="${BAR.x + 4}" Y="${BAR.y}" Width="${BAR.w - 8}" Height="${BAR.h}">
                <ProgressbarImage ImageFile="lav-bar.png" />
            </Progressbar>
            <ImageControl X="${x.capR.x}" Y="${x.capR.y}" Width="${x.capR.w}" Height="${x.capR.h}" ImageFile="lav-bar-right.png" />`;
  const progressTitles = { 3: 'ProgressUninstallHeader', 4: 'ProgressUninstallHeader', 8: 'ProgressRepairHeader' };
  const ck = x.ck;
  const browse = L.optionsBrowse[0];

  return `<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by LavArtemis docs/branding/generate-installer.mjs: do not edit, regenerate.
     Direction "A" of the prototype canvas (page V7). Colours are BGR hex. The fonts are the
     theme/fonts files, loaded by the BAFunctions DLL (LavArtemis-Qt wix/LavArtemisSetup/bafunctions):
     each one is its own family, at weight 400. -->
<Theme xmlns="http://wixtoolset.org/schemas/v4/thmutil" ImageFile="lav-bg.png">
${font(F.body, FAM.text, 14, bgr(c.text))}
${font(F.title, FAM.title, 34, bgr(c.text))}
${font(F.sub, FAM.text, 15, bgr(c.sub))}
${font(F.small, FAM.medium, 13, bgr(c.sub))}
    <Font Id="${F.edit}" Height="-15" Weight="400" Foreground="${bgr(c.text)}" Background="${bgr(c.field)}">${FAM.text}</Font>
${font(F.check, FAM.medium, 14, bgr(c.text))}
${font(F.button, FAM.bold, 16, bgr(c.text))}

    <!-- WS_POPUP | WS_VISIBLE | WS_MINIMIZEBOX: no Windows title bar, dragged by any point outside a
         control. Not WS_SYSMENU: thmutil (DpiuAdjustWindowRect) then sizes the window as if it had a
         caption, and the client area grows past the background (black bands right and bottom).
         The background (Theme/@ImageFile) needs a window font without Background. IconFile must match
         a Payload name of Bundle.wxs. -->
    <Window Width="${W}" Height="${H}" HexStyle="90020000" FontId="${F.body}" Caption="#(loc.Caption)" IconFile="lavartemis.ico" SourceX="0" SourceY="0">
        <ImageControl X="${LOGO.x}" Y="${LOGO.y}" Width="${LOGO.w}" Height="${LOGO.h}" ImageFile="lav-logo.png" Visible="yes" />
        <Button Name="LavMinimize" X="${MIN.x}" Y="${MIN.y}" Width="${MIN.w}" Height="${MIN.h}" Visible="yes" FontId="${F.button}">
            ${x.imagesOf(x.minPrefix)}
        </Button>
        <Button X="${CLOSE.x}" Y="${CLOSE.y}" Width="${CLOSE.w}" Height="${CLOSE.h}" Visible="yes" FontId="${F.button}">
            ${x.imagesOf(x.closePrefix)}
            <CloseWindowAction />
        </Button>

        <Page Name="Help">
${title('#(loc.HelpHeader)')}
            <Label X="40" Y="${Y(168)}" Width="${W - 80}" Height="128" FontId="${F.small}" DisablePrefix="yes">#(loc.HelpText)</Label>
${btn(L.closeOnly[0], 'Name="HelpCloseButton"', close)}
        </Page>
        <Page Name="Loading">
            <Label Name="CheckingForUpdatesLabel" X="${TEXT_X}" Y="${Y(46)}" Width="${TEXT_W}" Height="48" FontId="${F.title}" DisablePrefix="yes" Visible="no" />
        </Page>
        <Page Name="Install">
${title('#(loc.Title)')}
${sub('#(loc.InstallMessage)')}
            <!-- The first control of a page that takes the focus gets it when the page opens: on every page
                 the highlighted button comes first (Enter runs it). -->
${btn(L.install[1], 'Name="InstallButton"')}
            <!-- The checkbox of the mockup: two image buttons, the DLL swaps them and sets the variable. -->
            <Button Name="LavShortcutOn" X="${ck.x}" Y="${ck.y}" Width="${ck.w}" Height="${ck.h}" TabStop="yes" FontId="${F.button}" VisibleCondition="AddDesktopShortcutCheckbox = 1">
                ${x.imagesOf(x.onPrefix)}
            </Button>
            <Button Name="LavShortcutOff" X="${ck.x}" Y="${ck.y}" Width="${ck.w}" Height="${ck.h}" TabStop="yes" FontId="${F.button}" VisibleCondition="NOT AddDesktopShortcutCheckbox = 1">
                ${x.imagesOf(x.offPrefix)}
            </Button>
            <Label X="40" Y="${Y(247)}" Width="${x.folderW + 2}" Height="20" FontId="${F.small}" DisablePrefix="yes">#(loc.InstallFolderLabel)</Label>
            <Button Name="OptionsButton" X="${x.link.x}" Y="${x.link.y}" Width="${x.link.w}" Height="${x.link.h}" TabStop="yes" FontId="${F.button}" VisibleCondition="NOT WixStdBASuppressOptionsUI">
                ${x.imagesOf(x.linkPrefix)}
                <ChangePageAction Page="Options" />
            </Button>
${btn(L.install[0], 'Name="InstallCancelButton"', close)}
        </Page>
        <Page Name="Options">
${title('#(loc.OptionsHeader)')}
            <ImageControl X="${x.field.x}" Y="${x.field.y}" Width="${x.field.w}" Height="${x.field.h}" ImageFile="lav-field.png" />
            <Editbox Name="InstallFolder" X="${x.field.x + 18}" Y="${x.field.y + 14}" Width="${x.field.w - 36}" Height="20" TabStop="yes" FontId="${F.edit}" FileSystemAutoComplete="yes" />
${btn(L.options[1], 'Name="OptionsOkButton"', '\n                <ChangePageAction Page="Install" />')}
${btn(browse, 'Name="BrowseButton"', '\n                <BrowseDirectoryAction VariableName="InstallFolder" />')}
${btn(L.options[0], 'Name="OptionsCancelButton"', '\n                <ChangePageAction Page="Install" Cancel="yes" />')}
        </Page>
        <Page Name="Progress">
${title(byAction('ProgressHeader', progressTitles))}
${sub(byAction('ProgressMessage', { 3: 'ProgressUninstallMessage', 4: 'ProgressUninstallMessage' }))}
${bar('OverallCalculatedProgressbar')}
        </Page>
        <Page Name="ProgressPassive">
${title(byAction('ProgressPassiveHeader', progressTitles))}
${sub(byAction('ProgressPassiveMessage', { 3: 'ProgressUninstallMessage', 4: 'ProgressUninstallMessage' }))}
            <!-- WixStdBA drives one control per name: the passive page takes the engine's overall progress. -->
${bar('OverallProgressbar')}
        </Page>
        <Page Name="Modify">
${title('#(loc.Title)')}
${sub('#(loc.ModifyMessage)')}
${btn(L.modify[2], '', close)}
${btn(L.modify[0], 'Name="UninstallButton"')}
${btn(L.modify[1], 'Name="RepairButton" HideWhenDisabled="yes"')}
        </Page>
        <Page Name="Success">
${title(byAction('SuccessHeader', { 3: 'SuccessUninstallHeader', 4: 'SuccessUninstallHeader', 8: 'SuccessRepairHeader' }))}
${sub(byAction('SuccessMessage', { 3: 'SuccessUninstallMessage', 4: 'SuccessUninstallMessage' }))}
            <Label X="40" Y="${Y(200)}" Width="${W - 80}" Height="40" FontId="${F.check}" DisablePrefix="yes" VisibleCondition="WixStdBARestartRequired">
                <Text>#(loc.SuccessRestartText)</Text>
                <Text Condition="WixBundleAction = 3">#(loc.SuccessUninstallRestartText)</Text>
            </Label>
${btn(L.success[1], 'Name="LaunchButton" HideWhenDisabled="yes"')}
${btn(L.successRestart[0], 'Name="SuccessRestartButton" HideWhenDisabled="yes"')}
${btn(L.success[0], `VisibleCondition="NOT (${UNINSTALL})"`, close)}
${btn(L.closeOnly[0], `VisibleCondition="${UNINSTALL}"`, close)}
        </Page>
        <Page Name="Failure">
${title(byAction('FailureHeader', { 3: 'FailureUninstallHeader', 4: 'FailureUninstallHeader', 8: 'FailureRepairHeader' }))}
            <Hypertext Name="FailureLogFileLink" X="${TEXT_X}" Y="${Y(100)}" Width="${TEXT_W}" Height="60" FontId="${F.sub}" TabStop="yes" HideWhenDisabled="yes">#(loc.FailureHyperlinkLogText)</Hypertext>
            <Hypertext Name="FailureMessageText" X="40" Y="${Y(176)}" Width="${W - 80}" Height="64" FontId="${F.small}" TabStop="yes" HideWhenDisabled="yes" />
            <Label X="40" Y="${Y(244)}" Width="${W - 80}" Height="40" FontId="${F.check}" DisablePrefix="yes" VisibleCondition="WixStdBARestartRequired">#(loc.FailureRestartText)</Label>
${btn(L.failure[1], 'Name="FailureCloseButton"', close)}
${btn(L.failure[0], 'Name="FailureRestartButton" HideWhenDisabled="yes"')}
        </Page>
    </Window>
</Theme>
`;
}
