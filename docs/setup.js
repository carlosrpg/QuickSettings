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
    notInstalled: "Connected, but QuickSettings isn't on the car yet. Install it above; the permissions appear here after.",
    onCar: (v) => `On the car: ${v}`, notOnCar: "Not on the car yet", latest: (v) => `Latest: ${v}`, noLatest: "Latest: not published on this site",
    downloading: "Downloading the latest APK…", sending: "Sending the APK to the car…", installing: "Installing on the car…",
    installed: "Installed, and QuickSettings is opening on the car.",
    copied: "The car refused the install over ADB. The APK is now in the car's Download folder: open it there with a file manager.",
    installFailed: "The install failed. The log below has the details.",
    noDownload: "This site has no APK to download yet. Get it from the releases page, then use Choose APK file.",
    allSet: "All set. Open QuickSettings on the car; its Status panel should show everything up.",
    missing: (n) => `${n} required permission${n > 1 ? "s" : ""} missing.`,
    reqDone: "The required permission is granted. Optional ones are below.",
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
    notInstalled: "Conectado, mas o QuickSettings ainda não está no carro. Instale-o acima; as permissões aparecem aqui depois.",
    onCar: (v) => `No carro: ${v}`, notOnCar: "Ainda não está no carro", latest: (v) => `Mais recente: ${v}`, noLatest: "Mais recente: não publicada neste site",
    downloading: "Baixando o APK mais recente…", sending: "Enviando o APK para o carro…", installing: "Instalando no carro…",
    installed: "Instalado, e o QuickSettings está abrindo no carro.",
    copied: "O carro recusou a instalação por ADB. O APK está agora na pasta Download do carro: abra-o lá com um gerenciador de arquivos.",
    installFailed: "A instalação falhou. O registro abaixo tem os detalhes.",
    noDownload: "Este site ainda não tem o APK para baixar. Baixe-o na página de versões e use Escolher arquivo APK.",
    allSet: "Tudo pronto. Abra o QuickSettings no carro; o painel Status deve mostrar tudo ativo.",
    missing: (n) => `${n} permiss${n > 1 ? "ões obrigatórias faltando" : "ão obrigatória faltando"}.`,
    reqDone: "A permissão obrigatória foi concedida. As opcionais estão abaixo.",
    failed: "Um comando falhou. O registro abaixo tem os detalhes.",
    busy: "A porta USB está presa por outro programa. Num PC com as ferramentas do Android, rode  adb kill-server  e tente de novo.",
    cant: (m) => `Não foi possível conectar: ${m}`, disconnected: "Desconectado.",
    noUsb: "Este navegador não tem WebUSB. Abra esta página no Chrome ou Edge num computador, ou no Chrome de um celular Android.",
  },
  es: {
    overlay: ["Mostrar sobre otras apps", "El propio cajón, que flota sobre todas las apps."],
    listener: ["Acceso a las notificaciones", "Medios de las apps instaladas en la unidad principal y navegación paso a paso."],
    usage: ["App en primer plano", "Permite que la barra de información se quite del camino cuando hay una app a pantalla completa abierta, como Android Auto."],
    taprelay: ["Paso de toques", "Manija solo por deslizamiento y la zona para deslizar hacia abajo de la barra de información. Los servicios de accesibilidad del propio auto siguen activos."],
    securesettings: ["Automatización de Wi-Fi y ADB", "Permite que la app vuelva a activar sus propios servicios de accesibilidad después de una detención forzada, para los botones de Wi-Fi y ADB."],
    required: "Obligatorio", optional: "Opcional", manual: "Comando manual", grant: "Conceder", granted: "Concedido",
    notConnected: "Sin conexión.", pick: "Elige la unidad principal en la ventana USB del navegador…", noDevice: "No se eligió ningún dispositivo.",
    connected: "Conectado por USB. Si el auto pide permitir la depuración USB, acéptala.",
    notInstalled: "Conectado, pero QuickSettings todavía no está en el auto. Instálalo arriba; los permisos aparecen aquí después.",
    onCar: (v) => `En el auto: ${v}`, notOnCar: "Todavía no está en el auto", latest: (v) => `Más reciente: ${v}`, noLatest: "Más reciente: no publicada en este sitio",
    downloading: "Descargando el APK más reciente…", sending: "Enviando el APK al auto…", installing: "Instalando en el auto…",
    installed: "Instalado, y QuickSettings se está abriendo en el auto.",
    copied: "El auto rechazó la instalación por ADB. El APK ahora está en la carpeta Download del auto: ábrelo ahí con un administrador de archivos.",
    installFailed: "La instalación falló. El registro de abajo tiene los detalles.",
    noDownload: "Este sitio todavía no tiene el APK para descargar. Descárgalo de la página de versiones y usa Elegir archivo APK.",
    allSet: "Todo listo. Abre QuickSettings en el auto; su panel Estado debería mostrar todo activo.",
    missing: (n) => `Falta${n > 1 ? "n" : ""} ${n} permiso${n > 1 ? "s obligatorios" : " obligatorio"}.`,
    reqDone: "El permiso obligatorio está concedido. Los opcionales están abajo.",
    failed: "Un comando falló. El registro de abajo tiene los detalles.",
    busy: "Otro programa está ocupando el puerto USB. En una PC con las herramientas de Android, ejecuta  adb kill-server  e intenta de nuevo.",
    cant: (m) => `No se pudo conectar: ${m}`, disconnected: "Desconectado.",
    noUsb: "Este navegador no tiene WebUSB. Abre esta página en Chrome o Edge en una computadora, o en Chrome en un celular Android.",
  },
};
const LANG = document.documentElement.lang;
const T = STRINGS[LANG.startsWith("pt") ? "pt" : LANG.startsWith("es") ? "es" : "en"];

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
    id: "listener", required: false,
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

// The latest release, published next to the site by CI (GitHub's own download links send no CORS
// headers, so a page can't fetch them). Missing when the site is served without CI, e.g. locally.
const LATEST = new URL("latest/", import.meta.url);
let latest = null;
fetch(new URL("version.json", LATEST)).then((r) => (r.ok ? r.json() : null)).catch(() => null).then((j) => {
  latest = j?.version ?? null;
  showApp(undefined);
});

function showApp(onCar) {
  const row = $("#app-row");
  row.dataset.state = onCar === undefined ? "unknown" : onCar ? "ok" : "missing";
  $("#app-version").textContent = [
    onCar === undefined ? null : onCar ? T.onCar(onCar) : T.notOnCar,
    latest ? T.latest(latest) : T.noLatest,
  ].filter(Boolean).join(" · ");
  $("#install-latest").disabled = !adb || !latest;
  $("#choose-apk").disabled = !adb;
}

async function pushFile(path, blob, mode) {
  const sync = await adb.sync();
  try { await sync.write({ filename: path, file: blob.stream(), permission: mode }); } finally { await sync.dispose(); }
}

// Push the APK and car-setup.sh, run the script (the only line adbd sees is "sh <script>").
async function installApk(apk) {
  const busy = [$("#install-latest"), $("#choose-apk")];
  busy.forEach((b) => (b.disabled = true));
  let result = "failed";
  try {
    const script = await fetch(new URL("car-setup.sh", import.meta.url)).then((r) => r.blob());
    setStatus(T.sending);
    await pushFile("/data/local/tmp/qs.apk", apk, 0o644);
    await pushFile("/data/local/tmp/qs_r.sh", script, 0o755);
    setStatus(T.installing);
    log("$ sh /data/local/tmp/qs_r.sh");
    const out = await sh("sh /data/local/tmp/qs_r.sh");
    log(out);
    result = /QS_RESULT:(\w+)/.exec(out)?.[1] ?? "failed";
  } catch (e) {
    log(`! ${e.message ?? e}`);
  } finally {
    // The script cleans up after itself; this covers a push that failed before it ran.
    await sh("rm -f /data/local/tmp/qs.apk /data/local/tmp/qs_r.sh").catch(() => {});
  }
  await refresh();
  if (result === "ok") log(T.installed);
  else setStatus(result === "copied" ? T.copied : T.installFailed, result === "copied" ? "warn" : "error");
}

async function installLatest() {
  setStatus(T.downloading);
  const r = await fetch(new URL("QuickSettings.apk", LATEST)).catch(() => null);
  if (!r?.ok) return setStatus(T.noDownload, "error");
  await installApk(await r.blob());
}

async function refresh() {
  const pkgs = await sh(`pm list packages ${PKG}`);
  if (!pkgs.split("\n").some((l) => l.trim() === `package:${PKG}`)) {
    states = null;
    showApp(false);
    renderList();
    return setStatus(T.notInstalled, "warn");
  }
  showApp(/versionName=(\S+)/.exec(await sh(`dumpsys package ${PKG} | grep versionName`))?.[1] ?? "?");
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
  showApp(undefined);
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
$("#install-latest").onclick = installLatest;
$("#choose-apk").onclick = () => $("#apk-file").click();
$("#apk-file").onchange = (e) => {
  const file = e.target.files[0];
  e.target.value = "";
  if (file) installApk(file);
};
