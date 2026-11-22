// QuickSettings phone page: the drawer, served by the car. Plain JS, no build step.
// API: docs/companion-api.md. The car decides everything; this page only shows and asks.
"use strict";

const pt = (navigator.language || "").toLowerCase().startsWith("pt");
const T = pt ? {
  wifi: "Conectado · Wi-Fi", offline: "Sem conexão com o carro", seen: "atualizado há %s",
  notPaired: "Este celular não está pareado", removed: "Este celular foi removido do carro",
  howPair: "No carro: QuickSettings → Configurações → Celulares → Adicionar celular, e leia o código com a câmera.",
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
  wifi: "Connected · Wi-Fi", offline: "No connection to the car", seen: "updated %s ago",
  notPaired: "This phone isn't paired", removed: "This phone was removed from the car",
  howPair: "On the car: QuickSettings → Settings → Phones → Add phone, then scan the code with the camera.",
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
let token = localStorage.getItem("qs.token");
let state = null, lastOk = 0, timer = null, busyUntil = 0, armed = null, notice = {};
const local = {}; // targets of taps not yet reflected by the car: key -> {value, at}

const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const img = p => p ? `${esc(p)}?t=${encodeURIComponent(token)}` : "";
const ago = ms => { const s = Math.round(ms / 1000); return s < 60 ? `${s} s` : s < 3600 ? `${Math.round(s / 60)} min` : `${Math.round(s / 3600)} h`; };

async function api(path, body) {
  const res = await fetch(path, {
    method: body ? "POST" : "GET",
    headers: Object.assign({ "Content-Type": "application/json" }, token ? { Authorization: `Bearer ${token}` } : {}),
    body: body ? JSON.stringify(body) : undefined,
    cache: "no-store",
  });
  if (res.status === 401) { localStorage.removeItem("qs.token"); token = null; showUnpaired(true); throw new Error("unpaired"); }
  return { status: res.status, json: await res.json().catch(() => ({})) };
}

// --- pairing -------------------------------------------------------------------------

function showUnpaired(removed) {
  stop();
  setStatus(null);
  main.className = "";
  main.innerHTML = `<section class="notice wide"><h1>${esc(removed ? T.removed : T.notPaired)}</h1><p class="soft">${esc(T.howPair)}</p></section>`;
}

function showPairForm(code) {
  stop();
  const guess = /iphone|ipad/i.test(navigator.userAgent) ? "iPhone" : /android/i.test(navigator.userAgent) ? "Android" : "Phone";
  main.className = "";
  main.innerHTML = `<section class="notice wide"><h1>${esc(T.nameTitle)}</h1>
    <input type="text" id="name" maxlength="40" value="${esc(guess)}" aria-label="${esc(T.nameHint)}">
    <button id="pair" class="on">${esc(T.pair)}</button><p id="pair-msg" class="soft"></p></section>`;
  document.getElementById("pair").onclick = async () => {
    const msg = document.getElementById("pair-msg");
    document.getElementById("pair").disabled = true;
    msg.textContent = T.waiting;
    try {
      const r = await api("/api/pair", { code, name: document.getElementById("name").value });
      if (r.status === 200) {
        token = r.json.token;
        localStorage.setItem("qs.token", token);
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
    if (r.status === 200) { state = r.json; lastOk = Date.now(); }
  } catch (e) {
    if (!token) return;
  }
  render();
  if (!document.hidden) timer = setTimeout(poll, 2000);
}

document.addEventListener("visibilitychange", () => { if (!document.hidden && token) start(); else stop(); });

function setStatus(ok) {
  statusBar.className = "status" + (ok === true ? " ok" : ok === false ? " bad" : "");
  statusText.textContent = ok === true ? T.wifi : ok === false ? `${T.offline}${lastOk ? " · " + T.seen.replace("%s", ago(Date.now() - lastOk)) : ""}` : "";
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
  const cards = { MEDIA: media, CLIMATE: climate, CAR_MODE: carMode, AUTONOMY: autonomy, NAVIGATION: nav, ASSIST: assist, WEATHER: weather };
  const order = (state.layout && state.layout.open) || Object.keys(cards);
  main.innerHTML = order.filter(k => cards[k]).map(k => cards[k]()).join("");
}

const btn = (label, cmd, args, cls = "") =>
  `<button class="${cls}" data-cmd="${esc(cmd)}" data-args='${esc(JSON.stringify(args || {}))}'>${label}</button>`;
const card = (title, body, wide) => `<section class="card${wide ? " wide" : ""}"><div class="label">${esc(title)}</div>${body}</section>`;
const shown = (key, carValue, applying) => {
  const l = local[key];
  if (l && Date.now() - l.at < 3000) return l.value;
  delete local[key];
  return applying ?? carValue;
};
const noticeHtml = key => notice[key] && Date.now() - notice[key].at < 4000 ? `<div class="warn">${esc(notice[key].text)}</div>` : "";

function media() {
  const m = state.media || {};
  let progress = "";
  if (m.durationMs > 0) {
    const pos = m.playing && m.positionAtMs ? m.positionMs + (Date.now() - m.positionAtMs) : m.positionMs;
    progress = `<div class="bar"><div style="width:${Math.min(100, Math.max(0, pos / m.durationMs * 100))}%"></div></div>`;
  }
  const vol = (key, label, channel) => {
    const v = m.volume && m.volume[key];
    return v ? `<label class="muted">${esc(label)}<input type="range" min="0" max="${v.max}" value="${shown("vol." + key, v.value)}" data-vol="${channel}" data-key="vol.${key}"></label>` : "";
  };
  const recent = (m.recent || []).map(r => btn(`${r.icon ? `<img class="icon" src="${img(r.icon)}" alt="">` : ""}${esc(r.label)}`, "media.playRecent", { packageName: r.packageName })).join("");
  return card(m.appLabel || T.media, `
    <div class="row">${m.art ? `<img class="art" src="${img(m.art)}" alt="">` : `<div class="art"></div>`}
      <div class="grow"><div class="ellipsis">${esc(m.title || T.nothing)}</div><div class="muted ellipsis">${esc(m.artist || "")}</div></div></div>
    ${progress}
    <div class="seg">${btn("⏮", "media.previous")}${btn(m.playing ? "⏸" : "▶", "media.playPause", {}, "on")}${btn("⏭", "media.next")}${btn(esc(T.mute), "media.toggleMute", {}, m.muted ? "on" : "")}</div>
    ${vol("media", T.mediaVol, "MEDIA")}${vol("navigation", T.navVol, "NAVIGATION")}
    ${recent ? `<div class="chips">${recent}</div>` : ""}
    <div class="row"><input type="text" id="q-media" placeholder="${esc(T.searchMedia)}">${btn(esc(T.go), "media.search", { from: "q-media" })}</div>
    ${btn(esc(T.openCar), "app.bringToFront", { which: "MEDIA" })}`, true);
}

function stepper(label, key, value, applying, cmd, argName, min, max, unit) {
  const v = shown(key, value, applying);
  const dec = v == null ? null : Math.max(min, v - 1), inc = v == null ? null : Math.min(max, v + 1);
  return `<div><div class="muted">${esc(label)}</div><div class="stepper">
    ${btn("−", cmd, { [argName]: dec, key })}<div class="value${applying != null ? " applying" : ""}">${v == null ? "--" : v + unit}</div>${btn("+", cmd, { [argName]: inc, key })}</div></div>`;
}

function climate() {
  const c = (state.car || {}).climate || {}, a = c.applying || {};
  const sw = (label, name, on) => btn(esc(label), "climate.toggle", { switch: name }, on ? "on" : "");
  return card(T.climate, `
    ${stepper(T.driver, "temp.driver", c.driverTempC, a.driverTempC, "climate.setDriverTemp", "c", 16, 30, "°")}
    ${c.sync === false ? stepper(T.passenger, "temp.passenger", c.passengerTempC, a.passengerTempC, "climate.setPassengerTemp", "c", 16, 30, "°") : ""}
    ${stepper(T.fan, "fan", c.fanLevel, a.fanLevel, "climate.setFan", "level", 1, 10, "")}
    <div class="seg">${sw(T.power, "POWER", c.on)}${sw(T.ac, "AC", c.ac)}${sw(T.auto, "AUTO", c.auto)}</div>
    <div class="seg">${sw(T.recirc, "RECIRCULATION", c.recirculation)}${sw(T.sync, "SYNC", c.sync)}</div>
    ${c.outsideTempC != null ? `<div class="muted">${esc(T.outside)} ${c.outsideTempC}°</div>` : ""}${noticeHtml("climate")}`);
}

function carMode() {
  const e = (state.car || {}).energy || {};
  const seg = (cmd, arg, current, items) => `<div class="seg">${items.map(([v, l]) => btn(esc(l), cmd, { [arg]: v }, current === v ? "on" : "")).join("")}</div>`;
  const hold = e.batteryMode === "FORCED" && e.holdTarget != null
    ? `<label class="muted">${esc(T.hold)} <b>${shown("hold", e.holdTarget)}%</b><input type="range" min="30" max="80" step="1" value="${shown("hold", e.holdTarget)}" data-hold="1" data-key="hold"></label>` : "";
  return card(T.carMode, `
    <div class="muted">${esc(T.regen)}</div>${seg("energy.setRegen", "level", e.regen, [["LOW", T.low], ["MEDIUM", T.medium], ["HIGH", T.high]])}
    <div class="muted">${esc(T.battery)}</div>${seg("energy.setBatteryMode", "mode", e.batteryMode, [["INITIAL", T.initial], ["SMART", T.smart], ["FORCED", T.forced]])}
    ${hold}${noticeHtml("energy")}`);
}

function autonomy() {
  const e = (state.car || {}).energy || {};
  const total = (e.electricRangeKm ?? 0) + (e.fuelRangeKm ?? 0);
  const line = (l, km, pct) => `<div class="row spread"><span class="soft">${esc(l)}</span><span>${km ?? "--"} km · ${pct ?? "--"}%</span></div>`;
  return card(T.autonomy, `<div class="big">${total || "--"} km</div>${line(T.electric, e.electricRangeKm, e.batterySoc)}${line(T.fuel, e.fuelRangeKm, e.fuelPercent)}`);
}

function nav() {
  const n = state.nav;
  const body = n ? `<div class="row">${n.maneuver ? `<img class="maneuver" src="${img(n.maneuver)}" alt="">` : ""}
      <div class="grow"><div class="big ellipsis">${esc(n.title || "")}</div><div class="soft">${esc(n.text || "")}</div><div class="muted">${esc(n.subText || "")}</div></div></div>`
    : `<div class="soft">${esc(T.noRoute)}</div>`;
  return card(n && n.appLabel || T.nav, `${body}
    <div class="row"><input type="text" id="q-nav" placeholder="${esc(T.searchNav)}">${btn(esc(T.go), "nav.search", { from: "q-nav" })}</div>
    <div class="seg">${btn(esc(T.openCar), "app.bringToFront", { which: "NAV" })}${btn(esc(T.cluster), "nav.toggleCluster", {}, state.cluster && state.cluster.navOnCluster ? "on" : "")}</div>`);
}

function assist() {
  const s = (state.car || {}).assist || {};
  const allowed = !state.permissions || state.permissions.assist !== false;
  const row = (key, label, on, args) => {
    const pill = armed === key ? T.confirmOff : on == null ? "--" : on ? T.on : T.off;
    return `<button class="assist${on ? " on" : ""}${armed === key ? " arm" : ""}" data-cmd="assist.toggle" data-args='${esc(JSON.stringify(args))}' data-assist="${key}" ${allowed ? "" : "disabled"}>
      <span>${esc(label)}</span><span class="pill">${esc(pill)}</span></button>`;
  };
  const fcwLabel = { OFF: T.off, NEAR: T.near, STANDARD: T.standard, FAR: T.far }[s.collisionWarning] || "--";
  return card(T.assist, `
    ${row("AEB", T.aeb, s.aeb, { assist: "AEB" })}${row("AUTO_HOLD", T.autoHold, s.autoHold, { assist: "AUTO_HOLD" })}${row("LANE", T.lane, s.laneKeep, { assist: "LANE" })}
    <button class="assist${s.collisionWarning && s.collisionWarning !== "OFF" ? " on" : ""}" data-cmd="assist.cycleCollisionWarning" data-args="{}" ${allowed ? "" : "disabled"}>
      <span>${esc(T.fcw)}</span><span class="pill">${esc(fcwLabel)}</span></button>
    ${allowed ? "" : `<div class="warn">${esc(T.blocked.REMOTE_DISABLED)}</div>`}${noticeHtml("assist")}`);
}

function weather() {
  const w = state.weather;
  if (!w) return "";
  const days = (w.daily || []).slice(0, 5).map(d => `<div>${esc(d.date.slice(5))}<br><b>${d.maxC}°</b> <span class="muted">${d.minC}°</span></div>`).join("");
  return card(w.place || T.weather, `<div class="big">${w.tempC}°</div><div class="days">${days}</div>`);
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

main.addEventListener("change", ev => {
  const t = ev.target;
  if (t.dataset.vol) {
    local[t.dataset.key] = { value: +t.value, at: Date.now() };
    send("media.setVolume", { channel: t.dataset.vol, value: +t.value }, "media");
  } else if (t.dataset.hold) {
    local.hold = { value: +t.value, at: Date.now() };
    send("energy.setHoldTarget", { percent: +t.value }, "energy");
  }
  t.blur();
});
// A redraw between finger-down and click would swap the button out and lose the tap.
main.addEventListener("pointerdown", ev => { busyUntil = Date.now() + (ev.target.type === "range" ? 60000 : 1000); });
main.addEventListener("pointerup", () => { busyUntil = Date.now() + 400; });
main.addEventListener("keydown", ev => {
  if (ev.key === "Enter" && ev.target.id && ev.target.id.startsWith("q-")) ev.target.nextElementSibling.click();
});

// --- start ---------------------------------------------------------------------------

const pairCode = new URLSearchParams(location.hash.slice(1)).get("pair");
if (pairCode) showPairForm(pairCode);
else if (token) start();
else showUnpaired(false);
