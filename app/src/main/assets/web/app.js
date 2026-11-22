// QuickSettings phone page: the drawer, served by the car. Plain JS, no build step.
// API: docs/companion-api.md. The car decides everything; this page only shows and asks.
"use strict";

// ?lang=en or ?lang=pt overrides the phone's language.
const pt = (new URLSearchParams(location.search).get("lang") || navigator.language || "").toLowerCase().startsWith("pt");
const T = pt ? {
  wifi: "Conectado · Wi-Fi", internet: "Conectado · Internet", carOffline: "Carro offline", lastSeen: "visto há %s",
  offline: "Sem conexão com o carro", seen: "atualizado há %s",
  pinHint: "Código de 6 dígitos mostrado no carro", lanOnly: "Para parear, conecte este celular ao Wi-Fi do carro.",
  notPaired: "Este celular não está pareado", removed: "Este celular foi removido do carro",
  howPair: "No carro: QuickSettings → Configurações → Celulares → Adicionar celular. Leia o QR com a câmera ou digite o código abaixo.",
  nameTitle: "Parear com o carro", nameHint: "Nome deste celular", pair: "Parear", waiting: "Aguardando alguém no carro tocar em Permitir…",
  denied: "O carro recusou este celular.", badCode: "Código vencido ou já usado. Gere outro no carro.", timeout: "Ninguém respondeu no carro.",
  media: "Mídia", climate: "Clima", carMode: "Modo do carro", autonomy: "Autonomia", nav: "Navegação", assist: "Assistência", weather: "Tempo",
  nothing: "Nada tocando", driver: "Motorista", passenger: "Passageiro", fan: "Ventilação", outside: "Externa",
  power: "Ligar", ac: "A/C", auto: "Auto", recirc: "Recircular", sync: "Sincronizar",
  regen: "Regeneração", low: "Baixa", medium: "Média", high: "Alta", battery: "Bateria", initial: "Inicial", smart: "Inteligente", forced: "Forçado",
  hold: "Manter carga em", electric: "Elétrica", fuel: "Combustível", total: "Total",
  searchMedia: "Buscar música…", searchNav: "Buscar destino…", go: "Ir", openCar: "Abrir no carro", cluster: "No painel", noRoute: "Sem rota",
  aeb: "Frenagem de emergência", autoHold: "Auto hold", lane: "Prevenção de saída de faixa", fcw: "Alerta de colisão",
  on: "Ligado", off: "Desligado", near: "Perto", standard: "Padrão", far: "Longe", confirmOff: "Toque de novo para desligar",
  mediaVol: "Volume da mídia", navVol: "Voz da navegação", mute: "Mudo",
  blocked: { IGNITION_OFF: "Ligue a ignição", TOO_FAST: "Só abaixo de 10 km/h", UNAVAILABLE: "Indisponível agora", NOT_SPLIT: "Zonas sincronizadas",
    NOT_CONNECTED: "Carro sem dados", REMOTE_DISABLED: "Só no Wi-Fi do carro", RATE_LIMIT: "Devagar", BAD_ARGS: "Valor inválido" },
} : {
  wifi: "Connected · Wi-Fi", internet: "Connected · Internet", carOffline: "Car offline", lastSeen: "last seen %s ago",
  offline: "No connection to the car", seen: "updated %s ago",
  pinHint: "6-digit code shown on the car", lanOnly: "To pair, connect this phone to the car's Wi-Fi.",
  notPaired: "This phone isn't paired", removed: "This phone was removed from the car",
  howPair: "On the car: QuickSettings → Settings → Phones → Add phone. Scan the QR code with the camera, or type the code below.",
  nameTitle: "Pair with the car", nameHint: "This phone's name", pair: "Pair", waiting: "Waiting for someone in the car to tap Allow…",
  denied: "The car refused this phone.", badCode: "The code expired or was used. Make a new one on the car.", timeout: "Nobody answered in the car.",
  media: "Media", climate: "Climate", carMode: "Car mode", autonomy: "Range", nav: "Navigation", assist: "Assist", weather: "Weather",
  nothing: "Nothing playing", driver: "Driver", passenger: "Passenger", fan: "Fan", outside: "Outside",
  power: "Power", ac: "A/C", auto: "Auto", recirc: "Recirculate", sync: "Sync",
  regen: "Regeneration", low: "Low", medium: "Medium", high: "High", battery: "Battery", initial: "Initial", smart: "Smart", forced: "Forced",
  hold: "Hold charge at", electric: "Electric", fuel: "Fuel", total: "Total",
  searchMedia: "Search music…", searchNav: "Search destination…", go: "Go", openCar: "Open on car", cluster: "On cluster", noRoute: "No route",
  aeb: "Emergency braking", autoHold: "Auto hold", lane: "Lane departure prevention", fcw: "Collision warning",
  on: "On", off: "Off", near: "Near", standard: "Standard", far: "Far", confirmOff: "Tap again to switch off",
  mediaVol: "Media volume", navVol: "Navigation voice", mute: "Mute",
  blocked: { IGNITION_OFF: "Turn the ignition on", TOO_FAST: "Only below 10 km/h", UNAVAILABLE: "Not available now", NOT_SPLIT: "Zones are synced",
    NOT_CONNECTED: "No car data", REMOTE_DISABLED: "Only on the car's Wi-Fi", RATE_LIMIT: "Slow down", BAD_ARGS: "Invalid value" },
};

const main = document.getElementById("main");
const statusBar = document.getElementById("status");
const statusText = document.getElementById("status-text");
// The companion app keeps the token itself (the page's address changes between Wi-Fi and the relay).
const store = window.QsApp
  ? { get: () => window.QsApp.getToken() || null, set: t => window.QsApp.setToken(t || "") }
  : { get: () => localStorage.getItem("qs.token"), set: t => t ? localStorage.setItem("qs.token", t) : localStorage.removeItem("qs.token") };
// Relative to the page: "/" on the car, "/c/<carId>/" through the relay.
const BASE = location.pathname.replace(/[^/]*$/, "");
const viaRelay = BASE.startsWith("/c/");
let token = store.get();
let state = null, lastOk = 0, timer = null, busyUntil = 0, armed = null, notice = {}, carOffline = null;
const local = {}; // targets of taps not yet reflected by the car: key -> {value, at}

const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const img = p => p ? `${esc(BASE + p.replace(/^\//, ""))}?t=${encodeURIComponent(token)}` : "";
const ago = ms => { const s = Math.round(ms / 1000); return s < 60 ? `${s} s` : s < 3600 ? `${Math.round(s / 60)} min` : `${Math.round(s / 3600)} h`; };

async function api(path, body) {
  const res = await fetch(BASE + path.replace(/^\//, ""), {
    method: body ? "POST" : "GET",
    headers: Object.assign({ "Content-Type": "application/json" }, token ? { Authorization: `Bearer ${token}` } : {}),
    body: body ? JSON.stringify(body) : undefined,
    cache: "no-store",
  });
  if (res.status === 401) { store.set(null); token = null; showUnpaired(true); throw new Error("unpaired"); }
  return { status: res.status, json: await res.json().catch(() => ({})) };
}

// --- pairing -------------------------------------------------------------------------

function showUnpaired(removed) {
  if (viaRelay) {
    stop();
    setStatus(null);
    main.className = "";
    main.innerHTML = `<section class="notice wide"><h1>${esc(removed ? T.removed : T.notPaired)}</h1><p class="soft">${esc(T.lanOnly)}</p></section>`;
    return;
  }
  showPairForm(null, removed ? T.removed : T.notPaired);
}

// [code] from the QR link; without one the phone types the six-digit PIN the car shows.
function showPairForm(code, heading) {
  stop();
  setStatus(null);
  const guess = window.QsApp && window.QsApp.deviceName ? window.QsApp.deviceName() || "Android" : /iphone|ipad/i.test(navigator.userAgent) ? "iPhone" : /android/i.test(navigator.userAgent) ? "Android" : "Phone";
  main.className = "";
  main.innerHTML = `<section class="notice wide"><h1>${esc(heading || T.nameTitle)}</h1>
    ${code ? "" : `<p class="soft">${esc(T.howPair)}</p>
    <input type="text" id="pin" inputmode="numeric" autocomplete="one-time-code" maxlength="7" placeholder="${esc(T.pinHint)}" aria-label="${esc(T.pinHint)}">`}
    <input type="text" id="name" maxlength="40" value="${esc(guess)}" aria-label="${esc(T.nameHint)}">
    <button id="pair" class="on">${esc(T.pair)}</button><p id="pair-msg" class="soft"></p></section>`;
  document.getElementById("pair").onclick = async () => {
    const msg = document.getElementById("pair-msg");
    document.getElementById("pair").disabled = true;
    msg.textContent = T.waiting;
    try {
      const typed = code || document.getElementById("pin").value.replace(/\D/g, "");
      const r = await api("/api/pair", { code: typed, name: document.getElementById("name").value });
      if (r.status === 200) {
        token = r.json.token;
        store.set(token);
        history.replaceState(null, "", location.pathname);
        start();
        return;
      }
      msg.textContent = { DENIED: T.denied, TIMEOUT: T.timeout }[r.json.error] || T.badCode;
    } catch (e) {
      msg.textContent = T.offline;
    }
    document.getElementById("pair").disabled = false;
  };
}

// --- polling -------------------------------------------------------------------------

function start() {
  stop();
  poll();
}
function stop() { clearTimeout(timer); timer = null; }

async function poll() {
  clearTimeout(timer);
  try {
    const r = await api("/api/state");
    if (r.status === 200) { state = r.json; lastOk = Date.now(); carOffline = null; }
    else if (r.json && r.json.blocked === "CAR_OFFLINE") carOffline = r.json.lastSeenMs || 0;
  } catch (e) {
    if (!token) return;
  }
  render();
  if (!document.hidden) timer = setTimeout(poll, 2000);
}

document.addEventListener("visibilitychange", () => { if (!document.hidden && token) start(); else stop(); });

function setStatus(ok) {
  statusBar.className = "status" + (ok === true ? " ok" : ok === false ? " bad" : "");
  const since = lastOk ? " · " + T.seen.replace("%s", ago(Date.now() - lastOk)) : "";
  statusText.textContent = ok === true ? (state && state.path === "relay" ? T.internet : T.wifi)
    : ok === false ? (carOffline != null ? `${T.carOffline}${carOffline ? " · " + T.lastSeen.replace("%s", ago(Date.now() - carOffline)) : ""}` : `${T.offline}${since}`)
    : "";
}

// --- rendering -----------------------------------------------------------------------

function render(force) {
  const online = Date.now() - lastOk < 5000;
  setStatus(online);
  if (!state) return;
  // Don't rebuild under a finger or a keyboard.
  const el = document.activeElement;
  if ((!force && Date.now() < busyUntil) || (el && el.tagName === "INPUT")) return;
  main.className = online && state.connected !== false ? "" : "offline";
  document.body.classList.toggle("ready", true);
  const cards = { MEDIA: media, CLIMATE: climate, CAR_MODE: carMode, AUTONOMY: autonomy, NAVIGATION: nav, ASSIST: assist, WEATHER: weather };
  const order = (state.layout && state.layout.open) || Object.keys(cards);
  main.innerHTML = order.filter(k => cards[k]).map(k => cards[k]()).join("");
}

// The drawer's icons: the same 24×24 paths and 2 px round stroke as on the car (ui/Theme.kt).
const ICONS = {
  Play: "M8 5v14l11-7z", Pause: "M7 5h4v14H7zM13 5h4v14h-4z",
  Prev: "M6 5h2v14H6zM19 5v14L9 12z", Next: "M16 5h2v14h-2zM5 5v14l10-7z",
  Volume: "M4 9h4l5-4v14l-5-4H4zM16 9a4 4 0 0 1 0 6M18.5 6.5a7.5 7.5 0 0 1 0 11",
  Mute: "M4 9h4l5-4v14l-5-4H4zM16 9l5 6M21 9l-5 6",
  Voice: "M4 21v-3.6A7 7 0 1 1 15 10l1.5 3h-2v2.5a2 2 0 0 1-2 2H10V21M18.5 7.5a4 4 0 0 1 0 5M21 5a7.5 7.5 0 0 1 0 10",
  Plus: "M12 5v14M5 12h14", Minus: "M5 12h14",
  OpenApp: "M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5",
  TurnRight: "M7 20v-6a4 4 0 0 1 4-4h7M14 6l4 4-4 4", NavArrow: "M12 2l7 19-7-4-7 4z",
  Pin: "M12 21s-7-6.5-7-11a7 7 0 0 1 14 0c0 4.5-7 11-7 11zM14.5 10a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0",
  Battery: "M4 7h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2zM21 10v4",
  BatteryBolt: "M4 7h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2zM21 10v4 M11 9l-2 3h3l-2 3",
  Fuel: "M4 21V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v16M3 21h12M14 9h2a2 2 0 0 1 2 2v5a1.5 1.5 0 0 0 3 0V8l-3-3M7 8h4",
  Wind: "M3 8h10a3 3 0 1 0-3-3M3 12h15a3 3 0 1 1-3 3M3 16h7",
  Thermometer: "M14 14.76V3.5a2 2 0 0 0-4 0v11.26a4 4 0 1 0 4 0zM12 10v5.5",
  AirConditioner: "M5 3h14a3 3 0 0 1 3 3v3a3 3 0 0 1-3 3H5a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3zM5.5 8.5h13M12 14v7M8 14c0 3-1.3 5.3-3.6 7M16 14c0 3 1.3 5.3 3.6 7",
  Bulb: "M9 18h6M10 21h4M12 3a6 6 0 0 0-3.6 10.8c.7.6 1.1 1.4 1.1 2.2h5c0-.8.4-1.6 1.1-2.2A6 6 0 0 0 12 3",
  MarkerBolt: "M12 21.5s-6.5-6-6.5-11.2a6.5 6.5 0 1 1 13 0c0 5.2-6.5 11.2-6.5 11.2M12.9 6.6l-2.4 3.6h3l-2.4 3.6",
  Cluster: "M3.5 17a8.5 8.5 0 0 1 17 0M12 17l3.6-5.6M2.5 20h19",
  History: "M3 12a9 9 0 1 0 2.64-6.36L3 8.3M3 3.5v4.8h4.8M12 7.5V12l3 2",
  Power: "M12 4v8M7.6 6.6a7 7 0 1 0 8.8 0", SyncZones: "M4 9h13l-3.5-3.5M20 15H7l3.5 3.5",
  Recirculate: "M18.5 9A7 7 0 1 0 19 13.5M19 4.5V9h-4.5", Collision: "M12 4L21 19H3ZM12 10v4M12 16.5h.01",
  Brake: "M17 12a5 5 0 1 1-10 0a5 5 0 1 1 10 0M5 7a8 8 0 0 0 0 10M19 7a8 8 0 0 1 0 10M12 10v2M12 14h.01",
  AutoHold: "M19 12a7 7 0 1 1-14 0a7 7 0 1 1 14 0M9.5 15l2.5-6 2.5 6M10.3 13h3.4", Lane: "M7 20L10 4M17 20L14 4M12 13v3",
  Search: "M16.5 10.5a6 6 0 1 1-12 0a6 6 0 1 1 12 0M15 15l5 5",
  Sun: "M16 12a4 4 0 1 1-8 0a4 4 0 1 1 8 0M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4",
};
const ic = (name, size = 22, filled = false, stroke = 2) => `<svg class="ic" viewBox="0 0 24 24" width="${size}" height="${size}" aria-hidden="true" ${filled
  ? 'fill="currentColor"' : `fill="none" stroke="currentColor" stroke-width="${stroke}" stroke-linecap="round" stroke-linejoin="round"`}><path d="${ICONS[name]}"/></svg>`;

// Night Glass colours (ui/Theme.kt).
const hex = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));
const mix = (a, b, t) => { const x = hex(a), y = hex(b); return `rgb(${x.map((v, i) => Math.round(v + (y[i] - v) * t)).join(",")})`; };
/** The 16..30 °C diverging scale: cool blue, mid violet, warm orange. */
const tempColor = c => { const t = Math.min(1, Math.max(0, ((c ?? 23) - 16) / 14)); return t < 0.5 ? mix("#2F6BFF", "#8A7BBF", t * 2) : mix("#8A7BBF", "#FF7A2E", (t - 0.5) * 2); };
const chargeColor = s => s == null ? "#9AA6B6" : s < 15 ? "#FF5A5F" : s < 30 ? "#FFB547" : "#3DDC97";
const fmtTime = ms => { const s = Math.max(0, Math.floor(ms / 1000)); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`; };
const nextOf = (all, cur) => all[(all.indexOf(cur) + 1) % all.length];

const btn = (inner, cmd, args, cls = "", label = "") =>
  `<button class="${cls}" data-cmd="${esc(cmd)}" data-args='${esc(JSON.stringify(args || {}))}'${label ? ` aria-label="${esc(label)}"` : ""}>${inner}</button>`;
const card = (title, body, cls = "", right = "") =>
  `<section class="card ${cls}">${title || right ? `<div class="head"><div class="label">${esc(title)}</div>${right}</div>` : ""}${body}</section>`;
const shown = (key, carValue, applying) => {
  const l = local[key];
  if (l && Date.now() - l.at < 3000) return l.value;
  delete local[key];
  return applying ?? carValue;
};
const noticeHtml = key => notice[key] && Date.now() - notice[key].at < 4000 ? `<div class="warn">${esc(notice[key].text)}</div>` : "";

/**
 * The car's horizontal slider: a pill whose fill never shrinks below a circle, the icon and value
 * on it, − and + beside it. Tap or drag the pill to set; the value goes to the car on release.
 */
function slider({ key, value, applying, min, max, step = 1, cmd, arg, extra = {}, fill, icon, unit = "" }) {
  const v = shown(key, value, applying);
  const f = v == null ? 0 : (v - min) / (max - min);
  const dec = v == null ? null : Math.max(min, v - step), inc = v == null ? null : Math.min(max, v + step);
  const data = `data-cmd="${esc(cmd)}" data-arg="${arg}" data-key="${key}" data-min="${min}" data-max="${max}" data-step="${step}" data-unit="${unit}" data-extra='${esc(JSON.stringify(extra))}'`;
  return `<div class="slider${applying != null ? " applying" : ""}">
    ${btn(ic("Minus", 20), cmd, Object.assign({ [arg]: dec, key }, extra), "sq", "−")}
    <div class="track" ${data} role="slider" aria-valuemin="${min}" aria-valuemax="${max}" aria-valuenow="${v ?? ""}">
      <div class="fill" style="width:calc(36px + (100% - 36px) * ${f.toFixed(3)});background:${fill}"></div>
      <div class="tval">${ic(icon, 18)}<b>${v == null ? "--" : v + unit}</b></div>
    </div>
    ${btn(ic("Plus", 20), cmd, Object.assign({ [arg]: inc, key }, extra), "sq", "+")}</div>`;
}

function media() {
  const m = state.media || {};
  let progress = "";
  if (m.durationMs > 0) {
    const pos = Math.min(m.durationMs, m.playing && m.positionAtMs ? m.positionMs + (Date.now() - m.positionAtMs) : m.positionMs);
    progress = `<div class="progress"><div style="width:${(pos / m.durationMs * 100).toFixed(1)}%"></div></div>
      <div class="times"><span>${fmtTime(pos)}</span><span>${fmtTime(m.durationMs)}</span></div>`;
  }
  const vol = (key, icon, channel) => {
    const v = m.volume && m.volume[key];
    return v ? slider({ key: "vol." + key, value: v.value, min: 0, max: v.max, cmd: "media.setVolume", arg: "value", extra: { channel },
      fill: "linear-gradient(90deg, rgba(238,242,247,.55), #EEF2F7)", icon }) : "";
  };
  const recent = (m.recent || []).map(r => btn(r.icon ? `<img src="${img(r.icon)}" alt="">` : `<b>${esc((r.label || "?").slice(0, 1))}</b>`,
    "media.playRecent", { packageName: r.packageName }, "app", r.label)).join("");
  const chip = m.appLabel ? `<span class="chip">${m.appIcon ? `<img src="${img(m.appIcon)}" alt="">` : ""}${esc(m.appLabel)}</span>` : "";
  return `<section class="card media wide">${m.art ? `<div class="backdrop" style="background-image:url('${img(m.art)}')"></div>` : ""}
    <div class="row top">${chip}<span class="grow"></span>${btn(ic("OpenApp", 20), "app.bringToFront", { which: "MEDIA" }, "sq", T.openCar)}</div>
    <div class="row gap">${m.art ? `<img class="art" src="${img(m.art)}" alt="">` : `<div class="art">${ic("Play", 34, true)}</div>`}
      <div class="grow"><div class="title ellipsis">${esc(m.title || T.nothing)}</div><div class="muted ellipsis">${esc(m.artist || "")}</div>${progress}</div></div>
    <div class="controls">
      ${btn(ic("Prev", 28, true), "media.previous", {}, "round", "⏮")}
      ${btn(ic(m.playing ? "Pause" : "Play", 34, true), "media.playPause", {}, "round play", m.playing ? "⏸" : "▶")}
      ${btn(ic("Next", 28, true), "media.next", {}, "round", "⏭")}
      <span class="grow"></span>
      ${btn(ic(m.muted ? "Mute" : "Volume", 24), "media.toggleMute", {}, "sq big" + (m.muted ? " lit" : ""), T.mute)}
    </div>
    ${vol("media", "Volume", "MEDIA")}${vol("navigation", "Voice", "NAVIGATION")}
    ${recent ? `<div class="recent"><span class="hist">${ic("History", 20)}</span>${recent}</div>` : ""}
    <div class="search">${ic("Search", 20)}<input type="text" id="q-media" placeholder="${esc(T.searchMedia)}" enterkeyhint="search">${btn(esc(T.go), "media.search", { from: "q-media" }, "go")}</div>
  </section>`;
}

function climate() {
  const c = (state.car || {}).climate || {}, a = c.applying || {};
  const sw = (name, on, icon, label) =>
    `<button class="cbtn${on ? " on" : ""}${icon ? "" : " text"}" data-cmd="climate.toggle" data-args='{"switch":"${name}"}' aria-pressed="${!!on}"><i class="dot"></i>${icon ? ic(icon, 22) : ""}<span>${esc(label)}</span></button>`;
  const temp = (key, label, value, applying, cmd) => {
    const v = shown(key, value, applying);
    const fill = `linear-gradient(90deg, ${tempColor(16)}, ${tempColor(((v ?? 23) + 16) / 2)}, ${tempColor(v)})`;
    return `${label ? `<div class="sublabel">${esc(label)}</div>` : ""}${slider({ key, value, applying, min: 16, max: 30, cmd, arg: "c", fill, icon: "Thermometer", unit: "°" })}`;
  };
  const split = c.sync === false;
  const ac = `<button class="pill${c.ac ? " on" : ""}" data-cmd="climate.toggle" data-args='{"switch":"AC"}' aria-pressed="${!!c.ac}">${ic("AirConditioner", 16)}A/C</button>`;
  const right = `<div class="row">${c.outsideTempC != null ? `<span class="muted">${esc(T.outside)} ${c.outsideTempC}°</span>` : ""}${ac}</div>`;
  return card(T.climate, `
    <div class="cgrid">${sw("POWER", c.on, "Power", T.power)}${sw("AUTO", c.auto, null, "AUTO")}${sw("SYNC", c.sync, "SyncZones", T.sync)}${sw("RECIRCULATION", c.recirculation, "Recirculate", T.recirc)}</div>
    ${temp("temp.driver", split ? T.driver : "", c.driverTempC, a.driverTempC, "climate.setDriverTemp")}
    ${split ? temp("temp.passenger", T.passenger, c.passengerTempC, a.passengerTempC, "climate.setPassengerTemp") : ""}
    ${slider({ key: "fan", value: c.fanLevel, applying: a.fanLevel, min: 1, max: 10, cmd: "climate.setFan", arg: "level",
      fill: "linear-gradient(90deg, rgba(77,163,255,.55), #4DA3FF)", icon: "Wind" })}
    ${noticeHtml("climate")}`, "", right);
}

/** The regeneration dial: three arcs of a rim round a disc, lit one per level, as on the car. */
function regenDial(level, size) {
  const lit = { LOW: 1, MEDIUM: 2, HIGH: 3 }[level] || 0;
  const r = 40, cx = 50, cy = 50, start = 135, sweep = 270, gap = 10, part = sweep / 3;
  const pt = deg => [cx + r * Math.cos(deg * Math.PI / 180), cy + r * Math.sin(deg * Math.PI / 180)];
  const arcs = [0, 1, 2].map(i => {
    const a0 = start + i * part + gap / 2, a1 = start + (i + 1) * part - gap / 2;
    const [x0, y0] = pt(a0), [x1, y1] = pt(a1);
    return `<path d="M${x0.toFixed(1)} ${y0.toFixed(1)}A${r} ${r} 0 0 1 ${x1.toFixed(1)} ${y1.toFixed(1)}" stroke="${i < lit ? "#3DDC97" : "rgba(255,255,255,.2)"}" stroke-width="9" stroke-linecap="round" fill="none"/>`;
  }).join("");
  return `<svg viewBox="0 0 100 100" width="${size}" height="${size}" aria-hidden="true">${arcs}<circle cx="50" cy="50" r="17" fill="none" stroke="${lit ? "#3DDC97" : "#6B7686"}" stroke-width="6"/><circle cx="50" cy="50" r="5" fill="${lit ? "#3DDC97" : "#6B7686"}"/></svg>`;
}

function carMode() {
  const e = (state.car || {}).energy || {};
  const modeLabel = { INITIAL: T.initial, SMART: T.smart, FORCED: T.forced }[e.batteryMode] || "--";
  const regenLabel = { LOW: T.low, MEDIUM: T.medium, HIGH: T.high }[e.regen] || "--";
  const modeIcon = { INITIAL: "MarkerBolt", SMART: "Bulb", FORCED: "BatteryBolt" }[e.batteryMode] || "Battery";
  const cycle = (title, value, symbol, cmd, args) =>
    btn(`<span class="label">${esc(title)}</span><span class="sym">${symbol}</span><span class="val">${esc(value)}</span>`, cmd, args, "cycle", `${title}: ${value}`);
  const forced = e.batteryMode === "FORCED";
  const hold = shown("hold", e.holdTarget);
  return card("", `
    <div class="cycles">
      ${cycle(T.battery, modeLabel, `<span class="mode">${ic(modeIcon, 64, false, 1.6)}</span>`, "energy.setBatteryMode", { mode: nextOf(["INITIAL", "SMART", "FORCED"], e.batteryMode) })}
      ${cycle(T.regen, regenLabel, regenDial(e.regen, 70), "energy.setRegen", { level: nextOf(["LOW", "MEDIUM", "HIGH"], e.regen) })}
    </div>
    <div class="hold${forced ? "" : " off"}"><div class="row spread"><span>${esc(T.hold)}</span><b>${hold ?? "--"}%</b></div>
      ${slider({ key: "hold", value: e.holdTarget, min: 30, max: 80, step: 5, cmd: "energy.setHoldTarget", arg: "percent",
        fill: "linear-gradient(90deg, rgba(61,220,151,.55), #3DDC97)", icon: "BatteryBolt", unit: "%" })}</div>
    ${noticeHtml("energy")}`);
}

function autonomy() {
  const e = (state.car || {}).energy || {};
  const total = (e.electricRangeKm ?? 0) + (e.fuelRangeKm ?? 0);
  const line = (icon, label, km, pct, color) => `<div class="gauge"><div class="row spread"><span class="row">${ic(icon, 18)}${esc(label)}</span><span><b>${km ?? "--"}</b> km · ${pct ?? "--"}%</span></div>
    <div class="bar"><div style="width:${Math.min(100, Math.max(0, pct ?? 0))}%;background:${color}"></div></div></div>`;
  return card(T.autonomy, `<div class="big">${total || "--"}<small> km</small></div>
    ${line("BatteryBolt", T.electric, e.electricRangeKm, e.batterySoc, chargeColor(e.batterySoc))}${line("Fuel", T.fuel, e.fuelRangeKm, e.fuelPercent, "#F2B84B")}`);
}

function nav() {
  const n = state.nav;
  const onCluster = state.cluster && state.cluster.navOnCluster;
  const right = `<div class="row">${btn(ic("Cluster", 20), "nav.toggleCluster", {}, "sq" + (onCluster ? " lit" : ""), T.cluster)}${btn(ic("OpenApp", 20), "app.bringToFront", { which: "NAV" }, "sq", T.openCar)}</div>`;
  const body = n ? `<div class="row gap"><div class="maneuver">${n.maneuver ? `<img src="${img(n.maneuver)}" alt="">` : ic("TurnRight", 40, false, 2.6)}</div>
      <div class="grow"><div class="title">${esc(n.title || "")}</div><div class="soft">${esc(n.text || "")}</div></div></div>
      ${n.subText ? `<div class="eta">${ic("Pin", 18)}<b>${esc(n.subText)}</b></div>` : ""}`
    : `<div class="where">${ic("NavArrow", 40)}<span>${esc(T.noRoute)}</span></div>`;
  return card(n && n.appLabel || T.nav, `${body}
    <div class="search">${ic("Search", 20)}<input type="text" id="q-nav" placeholder="${esc(T.searchNav)}" enterkeyhint="go">${btn(esc(T.go), "nav.search", { from: "q-nav" }, "go")}</div>`, "", right);
}

function assist() {
  const s = (state.car || {}).assist || {};
  const allowed = !state.permissions || state.permissions.assist !== false;
  const row = (key, icon, label, on, pill, cmd, args) => {
    const text = armed === key ? T.confirmOff : pill;
    return `<button class="arow${on ? " on" : ""}${armed === key ? " arm" : ""}" data-cmd="${cmd}" data-args='${esc(JSON.stringify(args))}' ${key ? `data-assist="${key}"` : ""} ${allowed ? "" : "disabled"}>
      <span class="ring">${ic(icon, 22)}${on ? "" : '<i class="slash"></i>'}</span><span class="name">${esc(label)}</span><span class="pill">${esc(text)}</span></button>`;
  };
  const onOff = v => v == null ? "--" : v ? T.on : T.off;
  const fcw = s.collisionWarning;
  return card(T.assist, `
    ${row("AEB", "Brake", T.aeb, s.aeb, onOff(s.aeb), "assist.toggle", { assist: "AEB" })}
    ${row("", "Collision", T.fcw, fcw && fcw !== "OFF", { OFF: T.off, NEAR: T.near, STANDARD: T.standard, FAR: T.far }[fcw] || "--", "assist.cycleCollisionWarning", {})}
    ${row("AUTO_HOLD", "AutoHold", T.autoHold, s.autoHold, onOff(s.autoHold), "assist.toggle", { assist: "AUTO_HOLD" })}
    ${row("LANE", "Lane", T.lane, s.laneKeep, onOff(s.laneKeep), "assist.toggle", { assist: "LANE" })}
    ${allowed ? "" : `<div class="warn">${esc(T.blocked.REMOTE_DISABLED)}</div>`}${noticeHtml("assist")}`);
}

function weather() {
  const w = state.weather;
  if (!w || w.tempC == null) return "";
  const days = (w.daily || []).slice(0, 5).map(d => `<div><span class="muted">${esc(d.date.slice(8))}/${esc(d.date.slice(5, 7))}</span><b>${d.maxC}°</b><span class="muted">${d.minC}°</span></div>`).join("");
  return card(w.place || T.weather, `<div class="row gap"><span class="sun">${ic("Sun", 44)}</span><div class="big">${w.tempC}°</div></div><div class="days">${days}</div>`);
}

// --- actions -------------------------------------------------------------------------

async function send(action, args, group) {
  try {
    const r = await api("/api/cmd", { action, args });
    if (r.json && r.json.ok === false && r.json.blocked !== "NEEDS_CONFIRM") {
      notice[group] = { text: T.blocked[r.json.blocked] || r.json.blocked, at: Date.now() };
    }
    return r.json || {};
  } catch (e) {
    return {};
  } finally {
    setTimeout(poll, 600);
  }
}

main.addEventListener("click", async ev => {
  const b = ev.target.closest("button[data-cmd]");
  if (!b) return;
  busyUntil = Date.now() + 400;
  const action = b.dataset.cmd;
  const args = JSON.parse(b.dataset.args || "{}");
  const group = action.split(".")[0];
  if (args.from) {
    const input = document.getElementById(args.from);
    const query = input.value.trim();
    if (!query) return;
    input.value = "";
    input.blur();
    return send(action, { query }, group);
  }
  if (args.key) {
    const key = args.key;
    delete args.key;
    const value = Object.values(args)[0];
    if (value == null) return;
    if (main.classList.contains("offline")) return;
    local[key] = { value, at: Date.now() };
    render(true); // the next tap must step from this value
    return send(action, args, group);
  }
  if (b.dataset.assist) {
    // The drawer's second tap: switching a safety function off is confirmed by tapping again.
    const key = b.dataset.assist;
    if (armed === key) { args.confirm = true; armed = null; }
    const r = await send(action, args, group);
    if (r.blocked === "NEEDS_CONFIRM") { armed = key; setTimeout(() => { if (armed === key) { armed = null; render(true); } }, 4000); }
    render(true);
    return;
  }
  send(action, args, group);
});

// Sliders: the value follows the finger on the pill and goes to the car once, on release.
let drag = null;
function dragTo(ev) {
  const t = drag.el, d = t.dataset, r = t.getBoundingClientRect();
  const min = +d.min, max = +d.max, step = +d.step;
  const f = Math.min(1, Math.max(0, (ev.clientX - r.left - 18) / Math.max(1, r.width - 36)));
  const v = Math.min(max, Math.max(min, min + Math.round(f * (max - min) / step) * step));
  drag.value = v;
  t.querySelector(".fill").style.width = `calc(36px + (100% - 36px) * ${((v - min) / (max - min)).toFixed(3)})`;
  t.querySelector("b").textContent = v + d.unit;
}
main.addEventListener("pointerdown", ev => {
  // A redraw between finger-down and click would swap the button out and lose the tap.
  busyUntil = Date.now() + 1000;
  const t = ev.target.closest(".track");
  if (!t || main.classList.contains("offline")) return;
  busyUntil = Date.now() + 60000;
  drag = { el: t, value: null };
  t.setPointerCapture(ev.pointerId);
  dragTo(ev);
});
main.addEventListener("pointermove", ev => { if (drag) dragTo(ev); });
function endDrag(ev, cancelled) {
  if (!drag) return;
  const d = drag.el.dataset, v = drag.value;
  drag = null;
  busyUntil = Date.now() + 400;
  if (cancelled || v == null) return render(true);
  local[d.key] = { value: v, at: Date.now() };
  render(true);
  send(d.cmd, Object.assign({ [d.arg]: v }, JSON.parse(d.extra || "{}")), d.cmd.split(".")[0]);
}
main.addEventListener("pointerup", ev => { if (drag) endDrag(ev, false); else busyUntil = Date.now() + 400; });
main.addEventListener("pointercancel", ev => endDrag(ev, true));
main.addEventListener("keydown", ev => {
  if (ev.key === "Enter" && ev.target.id && ev.target.id.startsWith("q-")) ev.target.nextElementSibling.click();
});

// --- start ---------------------------------------------------------------------------

const pairCode = new URLSearchParams(location.hash.slice(1)).get("pair");
if (pairCode) showPairForm(pairCode);
else if (token) start();
else showUnpaired(false);
