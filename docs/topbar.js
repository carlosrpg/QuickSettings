// Draws the app's top info bar (ui/TopBar.kt) in HTML, from the same icon paths, colours and dp
// sizes, so the site shows it without the emulator's own status bar mixed in. 1px = 1dp.
// Values are the app's mock car (the emulator build). Fill <div class="topbar" data-variant="full|nav">.
const PT = document.documentElement.lang.startsWith("pt");
const ES = document.documentElement.lang.startsWith("es");
const L = PT
  ? { regen: "Recuperação de energia", out: "ext.", title: "Night Drive", artist: "The Mock Band", dist: "300 m", street: "Av. Paulista", eta: "12:58 · 8 km", level: "Médio" }
  : ES
  ? { regen: "Regeneración", out: "ext.", title: "Night Drive", artist: "The Mock Band", dist: "300 m", street: "Av. Reforma", eta: "12:58 · 8 km", level: "Media" }
  : { regen: "Regeneration", out: "out", title: "Night Drive", artist: "The Mock Band", dist: "300 m", street: "Main Street", eta: "12:58 · 5 mi", level: "Medium" };

const C = { text: "#EEF2F7", soft: "#C6D0DC", muted: "#9AA6B6", dim: "#6B7686", cool: "#2F6BFF", warm: "#FF7A2E", energy: "#3DDC97", fuel: "#F2B84B", route: "#4DA3FF", mode: "#3DDC97" };
const ICON = {
  batteryBolt: "M4 7h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2zM21 10v4 M11 9l-2 3h3l-2 3",
  fuel: "M4 21V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v16M3 21h12M14 9h2a2 2 0 0 1 2 2v5a1.5 1.5 0 0 0 3 0V8l-3-3M7 8h4",
  wind: "M3 8h10a3 3 0 1 0-3-3M3 12h15a3 3 0 1 1-3 3M3 16h7",
  thermometer: "M14 14.76V3.5a2 2 0 0 0-4 0v11.26a4 4 0 1 0 4 0zM12 10v5.5",
  turnRight: "M7 20v-6a4 4 0 0 1 4-4h7M14 6l4 4-4 4",
  pin: "M12 21s-7-6.5-7-11a7 7 0 0 1 14 0c0 4.5-7 11-7 11zM14.5 10a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0",
};
const REGEN_RIM = "M 13.835938 3.1835938 C 7.5579375 4.2335938 3 9.624 3 16 C 3 23.168 8.832 29 16 29 C 22.376 29 27.767406 24.442063 28.816406 18.164062 C 28.907406 17.619063 28.539141 17.104672 27.994141 17.013672 C 27.447141 16.922672 26.93575 17.291937 26.84375 17.835938 C 25.95475 23.146937 21.395 27 16 27 C 9.935 27 5 22.065 5 16 C 5 10.605 8.8530625 6.04425 14.164062 5.15625 C 14.709063 5.06525 15.077328 4.5508594 14.986328 4.0058594 C 14.896328 3.4618594 14.378937 3.0955937 13.835938 3.1835938 z";
const REGEN_BRAKE = "M 17.912109 2.1835938 C 17.472984 2.2054375 17.050234 2.3694531 16.708984 2.6582031 C 16.258984 3.0382031 16 3.5965469 16 4.1855469 L 16 8.265625 C 16 9.180625 16.607563 9.9724063 17.476562 10.191406 C 19.608563 10.731406 21.268594 12.390484 21.808594 14.521484 C 22.027594 15.391484 22.818375 16 23.734375 16 L 27.814453 16 C 28.404453 16 28.96175 15.740062 29.34375 15.289062 C 29.72875 14.833062 29.891016 14.234484 29.791016 13.646484 C 28.799016 7.7964844 24.203516 3.2029375 18.353516 2.2109375 C 18.205766 2.1856875 18.058484 2.1763125 17.912109 2.1835938 z M 18.019531 4.1816406 C 23.030531 5.0306406 26.968453 8.969 27.814453 14 L 23.748047 14.033203 C 23.027047 11.187203 20.812 8.973625 18 8.265625 L 18 4.1855469 L 18.019531 4.1816406 z M 21 6 C 20.448 6 20 6.448 20 7 C 20 7.552 20.448 8 21 8 C 21.552 8 22 7.552 22 7 C 22 6.448 21.552 6 21 6 z M 11.181641 7.4765625 C 10.792719 7.5184687 10.454437 7.7856406 10.335938 8.1816406 C 10.175937 8.7106406 10.475906 9.2677344 11.003906 9.4277344 L 11.300781 9.5175781 C 9.2627812 10.989578 8 13.365 8 16 C 8 20.411 11.589 24 16 24 C 19.569 24 22.649969 21.704156 23.667969 18.285156 C 23.825969 17.756156 23.523141 17.199969 22.994141 17.042969 C 22.463141 16.883969 21.908953 17.186844 21.751953 17.714844 C 20.987953 20.277844 18.677 22 16 22 C 12.691 22 10 19.309 10 16 C 10 14.07 10.907047 12.332656 12.373047 11.222656 L 12.226562 11.705078 C 12.066563 12.234078 12.366531 12.791172 12.894531 12.951172 C 12.991531 12.980172 13.088594 12.994141 13.183594 12.994141 C 13.613594 12.994141 14.010625 12.716203 14.140625 12.283203 L 14.957031 9.5742188 C 15.117031 9.0452188 14.817063 8.488125 14.289062 8.328125 L 11.580078 7.5136719 C 11.445828 7.4736719 11.311281 7.4625938 11.181641 7.4765625 z M 25 10 C 24.448 10 24 10.448 24 11 C 24 11.552 24.448 12 25 12 C 25.552 12 26 11.552 26 11 C 26 10.448 25.552 10 25 10 z M 17.210938 12.003906 C 17.143203 12.012953 17.075031 12.042813 17.019531 12.101562 L 13.070312 16.328125 C 12.907313 16.502125 13.03725 16.777344 13.28125 16.777344 L 15.154297 16.777344 L 14.558594 19.636719 C 14.494594 19.942719 14.897375 20.129578 15.109375 19.892578 L 18.931641 15.626953 C 19.088641 15.451953 18.957797 15.179688 18.716797 15.179688 L 16.826172 15.179688 L 17.564453 12.378906 C 17.625203 12.149406 17.414141 11.976766 17.210938 12.003906 z";

const icon = (d, color, size, stroke = 2) =>
  `<svg viewBox="0 0 24 24" style="width:${size}px;height:${size}px;flex:none" fill="none" stroke="${color}" stroke-width="${stroke}" stroke-linecap="round" stroke-linejoin="round"><path d="${d}"/></svg>`;

// Brake disc with the rim cut in three wedges (Tiles.kt RegenDial); `lit` wedges in the mode colour.
let uid = 0;
function regenDial(lit, size) {
  const pt = (deg, r = 48) => { const a = (deg * Math.PI) / 180; return `${16 + r * Math.cos(a)} ${16 + r * Math.sin(a)}`; };
  const start = 9.6, section = 250.8 / 3, gap = 7;
  let parts = "";
  for (let i = 0; i < 3; i++) {
    const a1 = start + i * section + gap / 2, a2 = a1 + section - gap, id = `rw${uid++}`;
    parts += `<clipPath id="${id}"><path d="M16 16L${pt(a1)}A48 48 0 0 1 ${pt(a2)}Z"/></clipPath>`
      + `<path d="${REGEN_RIM}" fill="${i < lit ? C.mode : "rgba(255,255,255,.2)"}" clip-path="url(#${id})"/>`;
  }
  return `<svg viewBox="0 0 32 32" style="width:${size}px;height:${size}px;flex:none"><path d="${REGEN_BRAKE}" fill="${lit ? C.mode : C.dim}" fill-rule="evenodd"/>${parts}</svg>`;
}

// Two half rings (MirrorRange.kt MirrorRing): electric up the left, fuel up the right.
function mirrorRing(ev, fuel, size, stroke) {
  const r = 16 - stroke / 2, gap = 7;
  const arc = (from, sweep) => {
    const p = (deg) => { const a = (deg * Math.PI) / 180; return `${16 + r * Math.cos(a)} ${16 + r * Math.sin(a)}`; };
    return `M${p(from)}A${r} ${r} 0 0 ${sweep > 0 ? 1 : 0} ${p(from + sweep)}`;
  };
  const span = 180 - 2 * gap;
  const seg = (from, dir, f, color) => `<path d="${arc(from, dir * span * f)}" stroke="${color}"/>`;
  return `<svg viewBox="0 0 32 32" style="position:absolute;inset:0;width:100%;height:100%" fill="none" stroke-width="${stroke}" stroke-linecap="round">`
    + seg(90 + gap, 1, 1, "rgba(255,255,255,.12)") + seg(90 - gap, -1, 1, "rgba(255,255,255,.12)")
    + seg(90 + gap, 1, ev, C.energy) + seg(90 - gap, -1, fuel, C.fuel) + `</svg>`;
}

const halo = "text-shadow:0 1px 3px #000";
const txt = (s, size, color = C.text, weight = 400, extra = "") =>
  `<div style="font-size:${size}px;line-height:1.15;color:${color};font-weight:${weight};white-space:nowrap;overflow:hidden;text-overflow:ellipsis;${extra}">${s}</div>`;

const nowPlaying = () =>
  `<div style="display:flex;align-items:center;flex:1;min-width:0">
     <div style="width:48px;height:48px;border-radius:12px;flex:none;background:radial-gradient(circle at 40% 40%,#ff9a6a,#e0457b 45%,#5b3bd1 90%)"></div>
     <div style="margin-left:10px;min-width:0;${halo}">${txt(L.title, 20, C.text, 600)}${txt(L.artist, 15, C.muted)}</div>
   </div>`;

const stat = (d, color, value, sub) =>
  `<div style="display:flex;align-items:center">${icon(d, color, 24)}<div style="margin-left:5px">${txt(value, 20, color, 600)}${txt(sub, 14, C.muted)}</div></div>`;

const climate = () =>
  `<div style="display:flex;align-items:center">${icon(ICON.thermometer, C.warm, 24)}
     <div style="margin-left:4px;${halo}">
       <div style="display:flex;align-items:center">${txt("22°", 20, C.text, 600)}<span style="width:6px"></span>${icon(ICON.wind, C.cool, 20)}<span style="width:4px"></span>${txt("3", 18, C.text, 600)}</div>
       ${txt(`${L.out} 14°`, 14, C.muted)}
     </div></div>`;

function fullBar() {
  return `<div style="display:flex;align-items:center;gap:8px;height:100%">${nowPlaying()}
    ${stat(ICON.batteryBolt, C.energy, "72%", "58 km")}
    ${stat(ICON.fuel, C.fuel, "45%", "554 km")}
    <div style="display:flex;align-items:center">${regenDial(2, 30)}<div style="margin-left:5px">${txt(L.level, 17, C.energy, 600)}${txt(L.regen, 13, C.muted)}</div></div>
    ${climate()}</div>`;
}

function navBar() {
  const tile = 44;
  return `<div style="display:flex;align-items:center;gap:14px;height:100%">${nowPlaying()}
    <div style="display:flex;align-items:center;max-width:50%;min-width:0">
      <div style="width:${tile}px;height:${tile}px;border-radius:${tile * 0.26}px;background:${C.route};display:grid;place-items:center;flex:none;box-shadow:0 2px 4px rgba(0,0,0,.5);border:1px solid rgba(0,0,0,.35)">${icon(ICON.turnRight, "#fff", tile * 0.64, 2.6)}</div>
      <div style="margin-left:10px;min-width:0;${halo}">${txt(L.dist, 18, C.text, 700)}${txt(L.street, 14, C.soft, 500)}
        <div style="display:flex;align-items:center;gap:3px">${icon(ICON.pin, C.muted, 12)}${txt(L.eta, 13, C.muted, 600)}</div></div>
    </div>
    <div style="position:relative;width:45px;height:45px;flex:none;display:grid;place-items:center">${mirrorRing(0.72, 0.45, 45, 4 * 32 / 45)}
      <div style="text-align:center">${txt("72%", 14, C.energy, 700)}${txt("45%", 14, C.fuel, 700)}</div></div>
    ${regenDial(2, 34)}
    <div style="display:grid;gap:2px;${halo}">
      <div style="display:flex;align-items:center;gap:4px">${icon(ICON.thermometer, C.warm, 20)}${txt("22°", 16, C.text, 600)}</div>
      <div style="display:flex;align-items:center;gap:4px">${icon(ICON.wind, C.cool, 20)}${txt("3", 16, C.text, 600)}</div>
    </div></div>`;
}

for (const el of document.querySelectorAll(".topbar")) {
  el.innerHTML = el.dataset.variant === "nav" ? navBar() : fullBar();
}
// The bar is laid out at 600 dp (px); zoom scales it to the slide's width.
for (const box of document.querySelectorAll(".bars")) {
  new ResizeObserver(() => {
    for (const bar of box.querySelectorAll(".topbar")) bar.style.zoom = box.clientWidth / 600;
  }).observe(box);
}
