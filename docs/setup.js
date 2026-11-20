// WebADB setup panel: connects to the head unit over WebUSB and applies the adb-only grants
// that README.md lists. Every command here is the README's, so the two must change together.
import { Adb, AdbDaemonTransport } from "https://cdn.jsdelivr.net/npm/@yume-chan/adb@2.6.4/+esm";
import { AdbDaemonWebUsbDeviceManager } from "https://cdn.jsdelivr.net/npm/@yume-chan/adb-daemon-webusb@2.3.2/+esm";
import AdbWebCredentialStore from "https://cdn.jsdelivr.net/npm/@yume-chan/adb-credential-web@2.1.0/+esm";

const PKG = "com.carlosrpg.cherry.quicksettings";
const TAP_RELAY = `${PKG}/.overlay.TapRelayService`;

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
    title: "Draw over other apps",
    why: "The drawer itself floats over every app.",
    check: { cmd: `appops get ${PKG} SYSTEM_ALERT_WINDOW`, ok: (o) => /SYSTEM_ALERT_WINDOW: allow/.test(o) },
    manual: [`adb shell appops set ${PKG} SYSTEM_ALERT_WINDOW allow`],
  },
  {
    id: "listener", required: true,
    title: "Notification access",
    why: "Media sessions of apps on the head unit and turn-by-turn navigation.",
    check: {
      cmd: "settings --user current get secure enabled_notification_listeners",
      ok: (o) => o.includes(`${PKG}/`) && o.includes("QsNotificationListener"),
    },
    manual: [`adb shell cmd notification allow_listener ${PKG}/.nav.QsNotificationListener`],
  },
  {
    id: "usage", required: false,
    title: "Foreground app",
    why: "Lets the info bar step aside when a full-screen app such as Android Auto is up.",
    check: { cmd: `appops get ${PKG} GET_USAGE_STATS`, ok: (o) => /GET_USAGE_STATS: allow/.test(o) },
    manual: [`adb shell appops set ${PKG} GET_USAGE_STATS allow`],
  },
  {
    id: "taprelay", required: false,
    title: "Tap pass-through",
    why: "Swipe-only handle and the info bar's pull-down zone. The car's own accessibility services stay on.",
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
    title: "Wi-Fi and ADB automation",
    why: "Lets the app switch its own accessibility services back on after a force-stop, for the Wi-Fi and ADB buttons.",
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
}
console.assert(withService("null", TAP_RELAY) === TAP_RELAY, "empty list");
console.assert(withService("a/.B", TAP_RELAY) === `a/.B:${TAP_RELAY}`, "append keeps others");
console.assert(withService(`a/.B:${PKG}/${PKG}.overlay.TapRelayService`, TAP_RELAY) === null, "full class form counts");

const $ = (s) => document.querySelector(s);
const panel = $("#setup");
let adb = null;

const sh = async (cmd) => (await adb.subprocess.noneProtocol.spawnWaitText(cmd)).trim();
const grantCmd = (g) => g.manual[0].replace(/^adb shell /, "");

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

function renderList(states) {
  const ul = $("#grant-list");
  ul.replaceChildren(...GRANTS.map((g) => {
    const state = states?.[g.id];
    const li = document.createElement("li");
    li.className = "grant";
    li.dataset.state = state === undefined ? "unknown" : state ? "ok" : "missing";
    li.innerHTML = `
      <span class="grant-dot" aria-hidden="true"></span>
      <div class="grant-body">
        <div class="grant-title">${g.title} <span class="tag ${g.required ? "req" : ""}">${g.required ? "Required" : "Optional"}</span></div>
        <p>${g.why}</p>
        <details><summary>Manual command</summary>${g.manual.map((m) => `<pre><code>${m.replace(/&/g, "&amp;").replace(/</g, "&lt;")}</code></pre>`).join("")}</details>
      </div>`;
    const btn = document.createElement("button");
    btn.className = "btn small";
    btn.textContent = state ? "Granted" : "Grant";
    btn.disabled = !adb || state === true;
    btn.onclick = () => run([g]);
    li.append(btn);
    return li;
  }));
}

async function refresh() {
  const pkgs = await sh(`pm list packages ${PKG}`);
  if (!pkgs.split("\n").some((l) => l.trim() === `package:${PKG}`)) {
    setStatus("Connected, but QuickSettings isn't on the car yet. Install the APK first (step 2), then connect again.", "warn");
    renderList(null);
    $("#grant-all").disabled = true;
    return;
  }
  const states = {};
  for (const g of GRANTS) states[g.id] = g.check.ok(await sh(g.check.cmd));
  renderList(states);
  const missing = GRANTS.filter((g) => !states[g.id]);
  $("#grant-all").disabled = missing.length === 0;
  const reqMissing = missing.filter((g) => g.required).length;
  setStatus(
    missing.length === 0 ? "All set. Open QuickSettings on the car; its Status panel should show everything up."
      : reqMissing ? `${reqMissing} required permission${reqMissing > 1 ? "s" : ""} missing.`
      : "Required permissions granted. Optional ones are below.",
    reqMissing ? "warn" : "ok",
  );
}

async function run(grants) {
  try {
    for (const g of grants) {
      log(`$ ${g.grant ? g.title : grantCmd(g)}`);
      if (g.grant) await g.grant(sh);
      else { const out = await sh(grantCmd(g)); if (out) log(out); }
    }
    await refresh();
  } catch (e) {
    log(`! ${e.message ?? e}`);
    setStatus("A command failed. The log below has the details.", "error");
  }
}

async function connect() {
  const manager = AdbDaemonWebUsbDeviceManager.BROWSER;
  try {
    setStatus("Pick the head unit in the browser's USB dialog…");
    const device = await manager.requestDevice();
    if (!device) return setStatus("No device selected.");
    const connection = await device.connect();
    setStatus("Connected over USB. If the car asks to allow USB debugging, accept it.");
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
    const busy = /claim|busy|in use|access denied/i.test(String(e?.message ?? e));
    setStatus(busy
      ? "The USB port is held by another program. On a PC with the Android tools, run  adb kill-server  and try again."
      : `Couldn't connect: ${e?.message ?? e}`, "error");
  }
}

async function disconnect() {
  try { await adb?.close(); } catch { /* already gone */ }
  adb = null;
  $("#connect").hidden = false;
  $("#disconnect").hidden = true;
  $("#device").textContent = "";
  setStatus("Disconnected.");
  renderList(null);
  $("#grant-all").disabled = true;
}

if (!AdbDaemonWebUsbDeviceManager.BROWSER) {
  panel.classList.add("unsupported");
  setStatus("This browser has no WebUSB. Open this page in Chrome or Edge on a computer, or Chrome on an Android phone.", "error");
  $("#connect").disabled = true;
} else {
  setStatus("Not connected.");
}
renderList(null);
$("#connect").onclick = connect;
$("#disconnect").onclick = disconnect;
$("#grant-all").onclick = () => run(GRANTS.filter((g) => $(`#grant-list li:nth-child(${GRANTS.indexOf(g) + 1})`).dataset.state !== "ok"));
