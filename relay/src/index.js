// QuickSettings relay: lets a paired phone reach the car when they are not on the same Wi-Fi.
// The car keeps one outbound WebSocket to its Durable Object; a phone's https request to
// /c/<carId>/... is forwarded over it and the car answers it like a LAN request. The car checks
// the phone's token; the relay only forwards. Design: docs/companion-api.md.
import { DurableObject } from "cloudflare:workers";

const CAR_ID = /^[A-Za-z0-9_-]{8,64}$/;
const ANSWER_MS = 15_000;
// The car pings every 30 s; after this long without one it counts as offline.
const ONLINE_MS = 75_000;
const MAX_BODY = 64 * 1024;

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (url.pathname === "/car") {
      // The car's own connection: only an APK built with this relay's key may open one.
      const id = url.searchParams.get("id") || "";
      if (!CAR_ID.test(id) || !env.RELAY_KEY || !(await same(req.headers.get("x-relay-key") || "", env.RELAY_KEY))) {
        return new Response("forbidden", { status: 403 });
      }
      if (req.headers.get("upgrade") !== "websocket") return new Response("expected websocket", { status: 426 });
      return stub(env, id).fetch(req);
    }
    const m = url.pathname.match(/^\/c\/([^/]+)(\/.*)?$/);
    if (!m || !CAR_ID.test(m[1])) return new Response("not found", { status: 404 });
    if (!m[2]) return Response.redirect(`${url.origin}/c/${m[1]}/`, 301); // the page uses relative paths
    return stub(env, m[1]).fetch(req);
  },
};

function stub(env, id) {
  return env.CARS.get(env.CARS.idFromName(id));
}

/** Constant-time comparison of two strings, through their SHA-256. */
async function same(a, b) {
  const enc = new TextEncoder();
  const [x, y] = await Promise.all([a, b].map(s => crypto.subtle.digest("SHA-256", enc.encode(s))));
  return crypto.subtle.timingSafeEqual(x, y);
}

const json = (status, body) => new Response(JSON.stringify(body), {
  status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
});

export class Car extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.pending = new Map();
    this.next = 1;
    // Answered without waking the object, so an idle car costs nothing.
    ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
  }

  async fetch(req) {
    const url = new URL(req.url);
    if (url.pathname === "/car") {
      for (const old of this.ctx.getWebSockets()) old.close(1012, "replaced");
      const [client, server] = Object.values(new WebSocketPair());
      this.ctx.acceptWebSocket(server);
      server.serializeAttachment({ connectedAt: Date.now(), carId: url.searchParams.get("id") });
      return new Response(null, { status: 101, webSocket: client });
    }

    const carId = url.pathname.split("/")[2];
    const path = url.pathname.slice(`/c/${carId}`.length);
    const ws = this.car();
    const lastSeenMs = ws ? this.seen(ws) : (await this.ctx.storage.get("lastSeen")) || 0;
    if (path === "/api/ping") return json(200, { carId, online: !!ws, lastSeenMs, path: "relay" });
    if (!ws) return json(503, { ok: false, blocked: "CAR_OFFLINE", lastSeenMs });

    const body = req.method === "POST" ? await req.text() : "";
    if (body.length > MAX_BODY) return json(413, { error: "TOO_LARGE" });
    const id = this.next++;
    const answer = new Promise(resolve => {
      const timer = setTimeout(() => { this.pending.delete(id); resolve(null); }, ANSWER_MS);
      this.pending.set(id, r => { clearTimeout(timer); resolve(r); });
    });
    try {
      ws.send(JSON.stringify({
        id, method: req.method, path, query: url.search.slice(1),
        authorization: req.headers.get("authorization") || "", body,
      }));
    } catch (e) {
      this.pending.delete(id);
      return json(503, { ok: false, blocked: "CAR_OFFLINE", lastSeenMs });
    }
    const r = await answer;
    if (!r) return json(504, { ok: false, blocked: "CAR_OFFLINE", lastSeenMs });
    const bytes = Uint8Array.from(atob(r.body || ""), c => c.charCodeAt(0));
    const headers = { "content-type": r.type || "application/octet-stream", "cache-control": r.cache || "no-store" };
    return new Response(r.status === 204 ? null : bytes, { status: r.status || 502, headers });
  }

  /** The car's socket, if it is connected and pinged recently. */
  car() {
    const ws = this.ctx.getWebSockets()[0];
    if (!ws) return null;
    if (Date.now() - this.seen(ws) > ONLINE_MS) {
      ws.close(1001, "silent");
      return null;
    }
    return ws;
  }

  seen(ws) {
    const ping = this.ctx.getWebSocketAutoResponseTimestamp(ws);
    return Math.max(ping ? ping.getTime() : 0, (ws.deserializeAttachment() || {}).connectedAt || 0);
  }

  async webSocketMessage(ws, message) {
    let r;
    try { r = JSON.parse(message); } catch (e) { return; }
    const resolve = this.pending.get(r.id);
    if (resolve) { this.pending.delete(r.id); resolve(r); }
  }

  async webSocketClose(ws, code) {
    await this.ctx.storage.put("lastSeen", this.seen(ws));
    try { ws.close(code === 1005 ? 1000 : code, "bye"); } catch (e) { /* already closed */ }
  }

  async webSocketError(ws) {
    await this.ctx.storage.put("lastSeen", this.seen(ws));
  }
}
