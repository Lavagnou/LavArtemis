// Generates the Windows installer theme of LavArtemis-Qt (WiX 7 standard bootstrapper application),
// direction "A" validated on the prototype canvas (page V7, 2026-11-24): the whole window takes the
// background of the logo (night gradient for LavArtemis, blueprint grid for LavArtemis Dev), the logo on
// the left, light text, pill buttons.
//
// Writes out/installer/<dev|stable>/: thm.xml (the theme, ThemeFile of Bundle.wxs), payloads.wxi (one
// Payload per image, included by Bundle.wxs) and the PNGs. Copy each folder to LavArtemis-Qt wix/LavArtemisSetup/theme/<channel>/.
// The French strings (thm.wxl) are hand-written in LavArtemis-Qt, they are not a render.
//
// How the WiX theme engine (thmutil) draws, and why the images look like this:
// - The window background is Theme/@ImageFile, drawn at its 100 % size and scaled by the engine on a
//   high-DPI screen (it takes no alternate resolution). Grid lines sit on half pixels to be crisp at 100 %.
// - Labels have no background colour, so their text is drawn straight on that image. Their text must not
//   change while a page is shown: nothing repaints the background behind it (ghosting).
// - Image buttons and the logo are child windows: their PNG is drawn into the child alone, so a
//   transparent corner would show garbage. Every button image therefore carries the crop of the
//   background it sits on, rendered at 200 % (the engine scales it down to the screen's DPI).
// - The progress bar image is 4 px wide: left edge, filled, empty, right edge. Opaque only: the engine
//   redraws it over itself at every step.
import { Resvg } from '@resvg/resvg-js';
import fs from 'node:fs';
import path from 'node:path';

const W = 620; // client area at 100 %, as in the mockup
const H = 388;
const SCALE = 2; // button and logo images

const CHANNELS = {
  dev: {
    logo: 'svg/lavartemis-dev.svg',
    background: `<rect width="${W + 1}" height="${H + 1}" fill="#1D4E89"/>` + grid(),
    caption: 'LavArtemis Dev',
    text: '#FFFFFF', sub: '#CFE0F5',
    primary: { fill: '#FFFFFF', hover: '#E6EEF9', pressed: '#CFDDF0', text: '#1D4E89' },
    go: { fill: '#4ADE80', hover: '#6AE896', pressed: '#3BC46F', text: '#0E1222' },
    ghostBorder: 'rgba(255,255,255,0.45)',
    ring: '#FFFFFF',
    bar: { fill: '#FFFFFF', track: '#4F75A3' }, // track = 22 % white over #1D4E89
  },
  stable: {
    logo: 'svg/lavartemis.svg',
    background: `<defs><linearGradient id="bg" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="${W * 0.36}" y2="${H}">
<stop offset="0" stop-color="#25325A"/><stop offset="0.85" stop-color="#0E1222"/></linearGradient></defs>
<rect width="${W + 1}" height="${H + 1}" fill="url(#bg)"/>`,
    caption: 'LavArtemis',
    text: '#FFFFFF', sub: '#AEB8D6',
    primary: { fill: '#DDE6F5', hover: '#EEF3FB', pressed: '#C5D2EA', text: '#0E1222' },
    go: { fill: '#8FB4FF', hover: '#A8C5FF', pressed: '#7AA2F0', text: '#0E1222' },
    ghostBorder: 'rgba(255,255,255,0.35)',
    ring: '#DDE6F5',
    bar: { fill: '#A9C4FF', track: '#2A3352' },
  },
};

function grid() {
  let d = '';
  for (let x = 24; x < W; x += 24) d += `M${x + 0.5} 0 V${H} `;
  for (let y = 24; y < H; y += 24) d += `M0 ${y + 0.5} H${W} `;
  return `<path d="${d.trim()}" stroke="#FFFFFF" stroke-opacity="0.09" stroke-width="1" fill="none"/>`;
}

// ------------------------------------------------------------------ layout (100 %, client area)
const LOGO = { x: 40, y: 40, w: 112, h: 112 };
const TEXT_X = 176;
const TEXT_W = W - TEXT_X - 40;
const BAR = { x: 40, y: 268, w: W - 80, h: 8 };
// Buttons: one image set per rectangle; controls sharing a rectangle share its images.
const R = {
  primary: { x: 466, y: 308, w: 114, h: 48, kind: 'primary' },
  ghost: { x: 344, y: 308, w: 110, h: 48, kind: 'ghost' },
  ghostRight: { x: 470, y: 308, w: 110, h: 48, kind: 'ghost' },
  link: { x: 36, y: 250, w: 78, h: 26, kind: 'link' },
  browse: { x: 456, y: 102, w: 124, h: 40, kind: 'ghost' },
  // Modify page: uninstalling is not the highlighted action, closing is.
  modUninstall: { x: 186, y: 308, w: 136, h: 48, kind: 'ghost' },
  modRepair: { x: 334, y: 308, w: 120, h: 48, kind: 'ghost' },
  okClose: { x: 256, y: 308, w: 110, h: 48, kind: 'ghost' },
  launch: { x: 378, y: 308, w: 202, h: 48, kind: 'go' },
};
const STATES = ['n', 'h', 's', 'f']; // normal, hover, selected (pressed), focus

function pill(r, kind, state, c) {
  if (kind === 'link') return '';
  const inset = 4;
  const x = r.x + inset, y = r.y + inset, w = r.w - 2 * inset, h = r.h - 2 * inset, rx = h / 2;
  let s = '';
  if (kind === 'ghost') {
    // Focus: the border turns solid instead of a second ring (two outlines read as a double border).
    const fill = state === 'h' ? 0.12 : state === 's' ? 0.2 : 0;
    const border = state === 'f' ? c.ring : c.ghostBorder;
    s += `<rect x="${x + 1}" y="${y + 1}" width="${w - 2}" height="${h - 2}" rx="${rx - 1}" fill="#FFFFFF" fill-opacity="${fill}" stroke="${border}" stroke-width="2"/>`;
    return s;
  } else {
    const p = kind === 'go' ? c.go : c.primary;
    const fill = state === 'h' ? p.hover : state === 's' ? p.pressed : p.fill;
    s += `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}" fill="${fill}"/>`;
  }
  if (state === 'f') {
    s += `<rect x="${r.x + 1}" y="${r.y + 1}" width="${r.w - 2}" height="${r.h - 2}" rx="${(r.h - 2) / 2}" fill="none" stroke="${c.ring}" stroke-width="1.6"/>`;
  }
  return s;
}

function render(svgBody, view, scale) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${view.x} ${view.y} ${view.w} ${view.h}" width="${view.w * scale}" height="${view.h * scale}">${svgBody}</svg>`;
  return new Resvg(svg, { fitTo: { mode: 'zoom', value: 1 }, font: { loadSystemFonts: false } }).render().asPng();
}

// Logo SVG placed in the LOGO rectangle (its own 0..100 viewBox, ids made unique).
function logoSvg(file) {
  const src = fs.readFileSync(file, 'utf8');
  const inner = src.replace(/^[\s\S]*?<svg[^>]*>/, '').replace(/<\/svg>\s*$/, '')
    .replace(/id="(\w+)"/g, 'id="logo-$1"').replace(/url\(#(\w+)\)/g, 'url(#logo-$1)');
  return `<g transform="translate(${LOGO.x} ${LOGO.y}) scale(${LOGO.w / 100})">${inner}</g>`;
}

// thmutil colours are BGR hex.
const bgr = (hex) => hex.slice(5, 7) + hex.slice(3, 5) + hex.slice(1, 3);

function png4(colors) { // 4 x 8 opaque PNG for the progress bar
  const rects = colors.map((c, i) => `<rect x="${i}" y="0" width="1" height="8" fill="${c}"/>`).join('');
  return render(rects, { x: 0, y: 0, w: 4, h: 8 }, 1);
}

for (const [channel, c] of Object.entries(CHANNELS)) {
  const out = path.resolve('out/installer', channel);
  fs.rmSync(out, { recursive: true, force: true });
  fs.mkdirSync(out, { recursive: true });
  const files = [];
  const write = (name, data) => { fs.writeFileSync(path.join(out, name), data); files.push(name); };

  // One pixel wider and taller than the window: thmutil refuses SourceX + Width equal to the image
  // width ("must be less than the image width"), an off-by-one of its check.
  write('lav-bg.png', render(c.background, { x: 0, y: 0, w: W + 1, h: H + 1 }, 1));
  write('lav-logo.png', render(c.background + logoSvg(c.logo), LOGO, SCALE));
  write('lav-bar.png', png4([c.bar.fill, c.bar.fill, c.bar.track, c.bar.track]));
  for (const [name, r] of Object.entries(R)) {
    for (const st of STATES) {
      write(`lav-${name}-${st}.png`, render(c.background + pill(r, r.kind, st, c), r, SCALE));
    }
  }

  fs.writeFileSync(path.join(out, 'thm.xml'), theme(c));
  fs.writeFileSync(path.join(out, 'payloads.wxi'), `<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by LavArtemis docs/branding/generate-installer.mjs: do not edit, regenerate. -->
<Include xmlns="http://wixtoolset.org/schemas/v4/wxs">
${files.map((f) => `  <Payload Name="${f}" SourceFile="theme\\${channel}\\${f}" Compressed="yes" />`).join('\n')}
</Include>
`);
  console.log(channel, files.length + 2, 'files');
}

// ------------------------------------------------------------------ theme
function theme(c) {
  const F = { body: 0, title: 1, sub: 2, primary: 3, ghost: 4, go: 5, link: 6, linkHover: 7, edit: 8, small: 9, check: 10 };
  const font = (id, h, w, fg, extra = '') => `    <Font Id="${id}" Height="-${h}" Weight="${w}" Foreground="${bgr(fg)}"${extra}>Segoe UI</Font>`;
  const fontOf = { primary: F.primary, ghost: F.ghost, go: F.go, link: F.link };
  const btn = (rect, attrs, text, inner = '') => {
    const r = R[rect];
    const img = (el, st) => `<${el} ImageFile="lav-${rect}-${st}.png" />`;
    const hover = r.kind === 'link' ? ` HoverFontId="${F.linkHover}"` : '';
    return `            <Button ${attrs} X="${r.x}" Y="${r.y}" Width="${r.w}" Height="${r.h}" TabStop="yes" FontId="${fontOf[r.kind]}"${hover}>
                ${img('ButtonImage', 'n')}${img('ButtonHoverImage', 'h')}${img('ButtonSelectedImage', 's')}${img('ButtonFocusImage', 'f')}
                <Text>${text}</Text>${inner}
            </Button>`;
  };
  const title = (inner) => `            <Label X="${TEXT_X}" Y="44" Width="${TEXT_W}" Height="44" FontId="${F.title}" DisablePrefix="yes">${inner}</Label>`;
  const sub = (inner, y = 92, h = 60) => `            <Label X="${TEXT_X}" Y="${y}" Width="${TEXT_W}" Height="${h}" FontId="${F.sub}" DisablePrefix="yes">${inner}</Label>`;
  const byAction = (base, a = {}) => `<Text>#(loc.${base})</Text>` + Object.entries(a)
    .map(([n, id]) => `<Text Condition="WixBundleAction = ${n}">#(loc.${id})</Text>`).join('');
  const close = '<CloseWindowAction />';
  const UNINSTALL = 'WixBundleAction = 3 OR WixBundleAction = 4';

  return `<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by LavArtemis docs/branding/generate-installer.mjs: do not edit, regenerate.
     Direction "A" of the prototype canvas (page V7). Colours are BGR hex. -->
<Theme xmlns="http://wixtoolset.org/schemas/v4/thmutil" ImageFile="lav-bg.png">
${font(F.body, 14, 400, c.text)}
${font(F.title, 32, 700, c.text)}
${font(F.sub, 15, 400, c.sub)}
${font(F.primary, 15, 700, c.primary.text)}
${font(F.ghost, 15, 700, c.text)}
${font(F.go, 15, 700, c.go.text)}
${font(F.link, 13, 600, c.text, ' Underline="yes"')}
${font(F.linkHover, 13, 600, c.sub, ' Underline="yes"')}
    <Font Id="${F.edit}" Height="-14" Weight="400" Foreground="windowtext" Background="window">Segoe UI</Font>
${font(F.small, 13, 400, c.sub)}
${font(F.check, 14, 600, c.text)}

    <!-- The background (Theme/@ImageFile) needs a window font without Background: a background brush
         would paint over it. IconFile must match a Payload name of Bundle.wxs. -->
    <Window Width="${W}" Height="${H}" HexStyle="100a0000" FontId="${F.body}" Caption="#(loc.Caption)" IconFile="lavartemis.ico" SourceX="0" SourceY="0">
        <ImageControl X="${LOGO.x}" Y="${LOGO.y}" Width="${LOGO.w}" Height="${LOGO.h}" ImageFile="lav-logo.png" Visible="yes" />

        <Page Name="Help">
${title('#(loc.HelpHeader)')}
            <Label X="40" Y="164" Width="${W - 80}" Height="132" FontId="${F.small}" DisablePrefix="yes">#(loc.HelpText)</Label>
${btn('primary', 'Name="HelpCloseButton"', '#(loc.CloseButton)', close)}
        </Page>
        <Page Name="Loading">
            <Label Name="CheckingForUpdatesLabel" X="${TEXT_X}" Y="44" Width="${TEXT_W}" Height="44" FontId="${F.title}" DisablePrefix="yes" Visible="no" />
        </Page>
        <Page Name="Install">
${title('#(loc.Title)')}
${sub('#(loc.InstallMessage)')}
            <!-- The first control of a page that takes the focus gets it when the page opens: on every page
                 the highlighted button comes first (Enter runs it, the focus ring sits on it). -->
${btn('primary', 'Name="InstallButton"', '#(loc.InstallInstallButton)')}
            <!-- A themed checkbox ignores its text colour (black on blue): the box alone, its text in a label. -->
            <Checkbox Name="AddDesktopShortcutCheckbox" X="40" Y="196" Width="18" Height="22" TabStop="yes" FontId="${F.check}" />
            <Label X="64" Y="196" Width="${W - 104}" Height="22" FontId="${F.check}" DisablePrefix="yes">#(loc.InstallShortcutLabel)</Label>
            <Label X="40" Y="228" Width="${W - 80}" Height="20" FontId="${F.small}" DisablePrefix="yes">#(loc.InstallFolderLabel)</Label>
${btn('link', 'Name="OptionsButton" VisibleCondition="NOT WixStdBASuppressOptionsUI"', '#(loc.InstallOptionsButton)', '\n                <ChangePageAction Page="Options" />')}
${btn('ghost', 'Name="InstallCancelButton"', '#(loc.CancelButton)', close)}
        </Page>
        <Page Name="Options">
${title('#(loc.OptionsHeader)')}
            <Editbox Name="InstallFolder" X="${TEXT_X}" Y="106" Width="268" Height="32" TabStop="yes" FontId="${F.edit}" FileSystemAutoComplete="yes" />
${btn('browse', 'Name="BrowseButton"', '#(loc.OptionsBrowseButton)', '\n                <BrowseDirectoryAction VariableName="InstallFolder" />')}
${btn('ghost', 'Name="OptionsCancelButton"', '#(loc.CancelButton)', '\n                <ChangePageAction Page="Install" Cancel="yes" />')}
${btn('primary', 'Name="OptionsOkButton"', '#(loc.OptionsOkButton)', '\n                <ChangePageAction Page="Install" />')}
        </Page>
        <Page Name="Progress">
${title(byAction('ProgressHeader', { 3: 'ProgressUninstallHeader', 4: 'ProgressUninstallHeader', 8: 'ProgressRepairHeader' }))}
${sub(byAction('ProgressMessage', { 3: 'ProgressUninstallMessage', 4: 'ProgressUninstallMessage' }))}
            <Progressbar Name="OverallCalculatedProgressbar" X="${BAR.x}" Y="${BAR.y}" Width="${BAR.w}" Height="${BAR.h}">
                <ProgressbarImage ImageFile="lav-bar.png" />
            </Progressbar>
${btn('ghostRight', 'Name="ProgressCancelButton"', '#(loc.CancelButton)')}
        </Page>
        <Page Name="ProgressPassive">
${title(byAction('ProgressPassiveHeader', { 3: 'ProgressUninstallHeader', 4: 'ProgressUninstallHeader', 8: 'ProgressRepairHeader' }))}
${sub(byAction('ProgressPassiveMessage', { 3: 'ProgressUninstallMessage', 4: 'ProgressUninstallMessage' }))}
            <!-- WixStdBA drives one control per name: the passive page takes the engine's overall progress. -->
            <Progressbar Name="OverallProgressbar" X="${BAR.x}" Y="${BAR.y + 40}" Width="${BAR.w}" Height="${BAR.h}">
                <ProgressbarImage ImageFile="lav-bar.png" />
            </Progressbar>
        </Page>
        <Page Name="Modify">
${title('#(loc.Title)')}
${sub('#(loc.ModifyMessage)')}
${btn('primary', '', '#(loc.CloseButton)', close)}
${btn('modUninstall', 'Name="UninstallButton"', '#(loc.ModifyUninstallButton)')}
${btn('modRepair', 'Name="RepairButton" HideWhenDisabled="yes"', '#(loc.ModifyRepairButton)')}
        </Page>
        <Page Name="Success">
${title(byAction('SuccessHeader', { 3: 'SuccessUninstallHeader', 4: 'SuccessUninstallHeader', 8: 'SuccessRepairHeader' }))}
${sub(byAction('SuccessMessage', { 3: 'SuccessUninstallMessage', 4: 'SuccessUninstallMessage' }))}
            <Label X="40" Y="200" Width="${W - 80}" Height="40" FontId="${F.check}" DisablePrefix="yes" VisibleCondition="WixStdBARestartRequired">
                <Text>#(loc.SuccessRestartText)</Text>
                <Text Condition="WixBundleAction = 3">#(loc.SuccessUninstallRestartText)</Text>
            </Label>
${btn('launch', 'Name="LaunchButton" HideWhenDisabled="yes"', '#(loc.SuccessLaunchButton)')}
${btn('launch', 'Name="SuccessRestartButton" HideWhenDisabled="yes"', '#(loc.RestartButton)')}
${btn('okClose', `VisibleCondition="NOT (${UNINSTALL})"`, '#(loc.CloseButton)', close)}
${btn('ghostRight', `VisibleCondition="${UNINSTALL}"`, '#(loc.CloseButton)', close)}
        </Page>
        <Page Name="Failure">
${title(byAction('FailureHeader', { 3: 'FailureUninstallHeader', 4: 'FailureUninstallHeader', 8: 'FailureRepairHeader' }))}
            <Hypertext Name="FailureLogFileLink" X="${TEXT_X}" Y="92" Width="${TEXT_W}" Height="60" FontId="${F.sub}" TabStop="yes" HideWhenDisabled="yes">#(loc.FailureHyperlinkLogText)</Hypertext>
            <Hypertext Name="FailureMessageText" X="40" Y="168" Width="${W - 80}" Height="64" FontId="${F.small}" TabStop="yes" HideWhenDisabled="yes" />
            <Label X="40" Y="240" Width="${W - 80}" Height="40" FontId="${F.check}" DisablePrefix="yes" VisibleCondition="WixStdBARestartRequired">#(loc.FailureRestartText)</Label>
${btn('primary', 'Name="FailureCloseButton"', '#(loc.CloseButton)', close)}
${btn('ghost', 'Name="FailureRestartButton" HideWhenDisabled="yes"', '#(loc.RestartButton)')}
        </Page>
    </Window>
</Theme>
`;
}
