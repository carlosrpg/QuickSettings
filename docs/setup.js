// WebADB setup panel: connects to the head unit over WebUSB and applies the adb-only grants
// that README.md lists. Every command here is the README's, so the two must change together.
import { Adb, AdbDaemonTransport } from "https://cdn.jsdelivr.net/npm/@yume-chan/adb@2.6.4/+esm";
import { AdbDaemonWebUsbDeviceManager } from "https://cdn.jsdelivr.net/npm/@yume-chan/adb-daemon-webusb@2.3.2/+esm";
import AdbWebCredentialStore from "https://cdn.jsdelivr.net/npm/@yume-chan/adb-credential-web@2.1.0/+esm";

const PKG = "com.carlosrpg.cherry.quicksettings";
const TAP_RELAY = `${PKG}/.overlay.TapRelayService`;

const STRINGS = {
  en: {
    overlay: ["Draw over other apps", "The drawer itself floats over every app."],
    listener: ["Notification access", "Media sessions of apps on the head unit and turn-by-turn navigation."],
    usage: ["Foreground app", "Lets the info bar step aside when a full-screen app such as Android Auto is up."],
    taprelay: ["Tap pass-through", "Swipe-only handle and the info bar's pull-down zone. The car's own accessibility services stay on."],
    securesettings: ["Wi-Fi and ADB automation", "Lets the app switch its own accessibility services back on after a force-stop, for the Wi-Fi and ADB buttons."],
    required: "Required", optional: "Optional", manual: "Manual command", grant: "Grant", granted: "Granted",
    notConnected: "Not connected.", pick: "Pick the head unit in the browser's USB dialog…", noDevice: "No device selected.",
    connected: "Connected over USB. If the car asks to allow USB debugging, accept it.",
    notInstalled: "Connected, but QuickSettings isn't on the car yet. Install the APK first (step 2), then connect again.",
    allSet: "All set. Open QuickSettings on the car; its Status panel should show everything up.",
    missing: (n) => `${n} required permission${n > 1 ? "s" : ""} missing.`,
    reqDone: "Required permissions granted. Optional ones are below.",
    failed: "A command failed. The log below has the details.",
    busy: "The USB port is held by another program. On a PC with the Android tools, run  adb kill-server  and try again.",
    cant: (m) => `Couldn't connect: ${m}`, disconnected: "Disconnected.",
    noUsb: "This browser has no WebUSB. Open this page in Chrome or Edge on a computer, or Chrome on an Android phone.",
  },
  pt: {
    overlay: ["Sobrepor a outros apps", "A própria gaveta, que flutua sobre todos os apps."],
    listener: ["Acesso às notificações", "Mídia dos apps instalados na central e navegação curva a curva."],
    usage: ["App em primeiro plano", "Deixa a barra de informações sair da frente quando um app em tela cheia, como o Android Auto, está aberto."],
    taprelay: ["Repasse de toques", "Alça só por deslize e a zona de puxar da barra de informações. Os serviços de acessibilidade do próprio carro continuam ligados."],
    securesettings: ["Automação de Wi-Fi e ADB", "Permite ao app religar os próprios serviços de acessibilidade depois de uma parada forçada, para os botões de Wi-Fi e ADB."],
    required: "Obrigatória", optional: "Opcional", manual: "Comando manual", grant: "Conceder", granted: "Concedida",
    notConnected: "Não conectado.", pick: "Escolha a central na janela USB do navegador…", noDevice: "Nenhum aparelho escolhido.",
    connected: "Conectado por USB. Se o carro pedir para permitir a depuração USB, aceite.",
    notInstalled: "Conectado, mas o QuickSettings ainda não está no carro. Instale o APK primeiro (passo 2) e conecte de novo.",
    allSet: "Tudo pronto. Abra o QuickSettings no carro; o painel Status deve mostrar tudo ativo.",
    missing: (n) => `${n} permiss${n > 1 ? "ões obrigatórias faltando" : "ão obrigatória faltando"}.`,
    reqDone: "Permissões obrigatórias concedidas. As opcionais estão abaixo.",
    failed: "Um comando falhou. O registro abaixo tem os detalhes.",
    busy: "A porta USB está presa por outro programa. Num PC com as ferramentas do Android, rode  adb kill-server  e tente de novo.",
    cant: (m) => `Não foi possível conectar: ${m}`, disconnected: "Desconectado.",
    noUsb: "Este navegador não tem WebUSB. Abra esta página no Chrome ou Edge num computador, ou no Chrome de um celular Android.",
  },
};
const T = STRINGS[document.documentElement.lang.startsWith("pt") ? "pt" : "en"];

// Appends our service to the colon-separated list, keeping the car's own services.
export function withService(current, component) {
  const list = (current ?? "").trim();
  const items = list === "" || list === "null" ? [] : list.split(":");
  const cls = component.replace("/.", `/${PKG}.`);
  if (items.includes(component) || items.includes(cls)) return null; // already there
  return [...items, component].join(":");
}

const GRANTS = [
  {
    id: "overlay", required: true,
    check: { cmd: `appops get ${PKG} SYSTEM_ALERT_WINDOW`, ok: (o) => /SYSTEM_ALERT_WINDOW: allow/.test(o) },
    manual: [`adb shell appops set ${PKG} SYSTEM_ALERT_WINDOW allow`],
  },
  {
    id: "listener", required: true,
    check: {
      cmd: "settings --user current get secure enabled_notification_listeners",
      ok: (o) => o.includes(`${PKG}/`) && o.includes("QsNotificationListener"),
    },
    manual: [`adb shell cmd notification allow_listener ${PKG}/.nav.QsNotificationListener`],
  },
  {
    id: "usage", required: false,
    check: { cmd: `appops get ${PKG} GET_USAGE_STATS`, ok: (o) => /GET_USAGE_STATS: allow/.test(o) },
    manual: [`adb shell appops set ${PKG} GET_USAGE_STATS allow`],
  },
  {
    id: "taprelay", required: false,
    check: {
      cmd: "settings --user current get secure enabled_accessibility_services; echo '|'; settings --user current get secure accessibility_enabled",
      ok: (o) => {
        const [list, enabled] = o.split("|");
        return withService(list, TAP_RELAY) === null && enabled.trim() === "1";
      },
    },
    grant: async (sh) => {
      const current = await sh("settings --user current get secure enabled_accessibility_services");
      const next = withService(current, TAP_RELAY);
      if (next !== null) {
        // The value comes from the device and goes back into a shell line: allow only component characters.
        if (!/^[A-Za-z0-9_.\/$:]+$/.test(next)) throw new Error(`Unexpected accessibility list: ${current}`);
        await sh(`settings --user current put secure enabled_accessibility_services '${next}'`);
      }
      await sh("settings --user current put secure accessibility_enabled 1");
    },
    manual: [
      `adb shell 'settings --user current put secure enabled_accessibility_services "$(settings --user current get secure enabled_accessibility_services):${TAP_RELAY}"'`,
      "adb shell settings --user current put secure accessibility_enabled 1",
    ],
  },
  {
    id: "securesettings", required: false,
    check: {
      cmd: `dumpsys package ${PKG} | grep -c 'WRITE_SECURE_SETTINGS: granted=true'`,
      ok: (o) => parseInt(o, 10) > 0,
    },
    manual: [`adb shell pm grant ${PKG} android.permission.WRITE_SECURE_SETTINGS`],
  },
];

// The head unit's adbd drops any command line containing "install"; keep every line clear of it.
for (const g of GRANTS) {
  for (const c of [g.check.cmd, ...g.manual]) console.assert(!/install/i.test(c), `adbd would drop: ${c}`);
  for (const t of Object.values(STRINGS)) console.assert(t[g.id], `missing text for ${g.id}`);
}
console.assert(withService("null", TAP_RELAY) === TAP_RELAY, "empty list");
console.assert(withService("a/.B", TAP_RELAY) === `a/.B:${TAP_RELAY}`, "append keeps others");
console.assert(withService(`a/.B:${PKG}/${PKG}.overlay.TapRelayService`, TAP_RELAY) === null, "full class form counts");

const $ = (s) => document.querySelector(s);
let adb = null;
let states = null;

const sh = async (cmd) => (await adb.subprocess.noneProtocol.spawnWaitText(cmd)).trim();
const grantCmd = (g) => g.manual[0].replace(/^adb shell /, "");
const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;");

function setStatus(text, tone = "") {
  const el = $("#setup-status");
  el.textContent = text;
  el.dataset.tone = tone;
}

function log(line) {
  const el = $("#setup-log");
  el.hidden = false;
  el.textContent += line + "\n";
  el.scrollTop = el.scrollHeight;
}

function renderList() {
  $("#grant-list").replaceChildren(...GRANTS.map((g) => {
    const state = states?.[g.id];
    const [title, why] = T[g.id];
    const li = document.createElement("li");
    li.className = "grant";
    li.dataset.state = state === undefined ? "unknown" : state ? "ok" : "missing";
    li.innerHTML = `
      <span class="grant-dot" aria-hidden="true"></span>
      <div class="grant-body">
        <div class="grant-title">${title} <span class="tag ${g.required ? "req" : ""}">${g.required ? T.required : T.optional}</span></div>
        <p>${why}</p>
        <details><summary>${T.manual}</summary>${g.manual.map((m) => `<pre><code>${esc(m)}</code></pre>`).join("")}</details>
      </div>`;
    const btn = document.createElement("button");
    btn.className = "btn small";
    btn.textContent = state ? T.granted : T.grant;
    btn.disabled = !adb || state === true;
    btn.onclick = () => run([g]);
    li.append(btn);
    return li;
  }));
  $("#grant-all").disabled = !states || GRANTS.every((g) => states[g.id]);
}

async function refresh() {
  const pkgs = await sh(`pm list packages ${PKG}`);
  if (!pkgs.split("\n").some((l) => l.trim() === `package:${PKG}`)) {
    states = null;
    renderList();
    return setStatus(T.notInstalled, "warn");
  }
  states = {};
  for (const g of GRANTS) states[g.id] = g.check.ok(await sh(g.check.cmd));
  renderList();
  const missing = GRANTS.filter((g) => !states[g.id]);
  const reqMissing = missing.filter((g) => g.required).length;
  setStatus(missing.length === 0 ? T.allSet : reqMissing ? T.missing(reqMissing) : T.reqDone, reqMissing ? "warn" : "ok");
}

async function run(grants) {
  try {
    for (const g of grants) {
      log(`$ ${g.grant ? T[g.id][0] : grantCmd(g)}`);
      if (g.grant) await g.grant(sh);
      else { const out = await sh(grantCmd(g)); if (out) log(out); }
    }
    await refresh();
  } catch (e) {
    log(`! ${e.message ?? e}`);
    setStatus(T.failed, "error");
  }
}

async function connect() {
  try {
    setStatus(T.pick);
    const device = await AdbDaemonWebUsbDeviceManager.BROWSER.requestDevice();
    if (!device) return setStatus(T.noDevice);
    const connection = await device.connect();
    setStatus(T.connected);
    const transport = await AdbDaemonTransport.authenticate({
      serial: device.serial, connection, credentialStore: new AdbWebCredentialStore("QuickSettings"),
    });
    adb = new Adb(transport);
    $("#connect").hidden = true;
    $("#disconnect").hidden = false;
    $("#device").textContent = `${await sh("getprop ro.product.model")} · Android ${await sh("getprop ro.build.version.release")}`;
    await refresh();
  } catch (e) {
    adb = null;
    const msg = String(e?.message ?? e);
    setStatus(/claim|busy|in use|access denied/i.test(msg) ? T.busy : T.cant(msg), "error");
  }
}

async function disconnect() {
  try { await adb?.close(); } catch { /* already gone */ }
  adb = null;
  states = null;
  $("#connect").hidden = false;
  $("#disconnect").hidden = true;
  $("#device").textContent = "";
  setStatus(T.disconnected);
  renderList();
}

if (!AdbDaemonWebUsbDeviceManager.BROWSER) {
  setStatus(T.noUsb, "error");
  $("#connect").disabled = true;
} else {
  setStatus(T.notConnected);
}
renderList();
$("#connect").onclick = connect;
$("#disconnect").onclick = disconnect;
$("#grant-all").onclick = () => run(GRANTS.filter((g) => !states?.[g.id]));
