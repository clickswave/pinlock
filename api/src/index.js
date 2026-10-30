// The API behind the Pinlock demo app. Every account, order and card number
// here is made up. It exists so a pinned Android app has something real to
// talk to, on a host we own, that anyone can point a proxy at.
//
// GET /v1/orders/:id does not check who owns the order. That is deliberate:
// it is the bug the IDOR walkthrough finds. Nothing else here is meant to be
// vulnerable.

const USERS = [
  {
    id: "u_1042",
    name: "Sam Carter",
    email: "demo@example.com",
    password: "correct-horse",
    plan: "Plus",
    joined: "2026-03-14",
  },
  {
    id: "u_1043",
    name: "Jordan Lee",
    email: "jordan@example.com",
    password: "battery-staple",
    plan: "Free",
    joined: "2026-05-02",
  },
];

const ORDERS = [
  {
    id: "o_5001",
    owner: "u_1042",
    item: "Noise-cancelling headphones",
    total: 249.0,
    currency: "USD",
    status: "delivered",
    placed: "2026-09-21",
    card: "visa •••• 4242",
    ship_to: "Sam Carter, 18 Harbour St, Bristol BS1 4QA, UK",
  },
  {
    id: "o_5002",
    owner: "u_1042",
    item: "USB-C charger, 65 W",
    total: 39.0,
    currency: "USD",
    status: "shipped",
    placed: "2026-10-18",
    card: "visa •••• 4242",
    ship_to: "Sam Carter, 18 Harbour St, Bristol BS1 4QA, UK",
  },
  {
    id: "o_5003",
    owner: "u_1043",
    item: "Mechanical keyboard",
    total: 129.0,
    currency: "USD",
    status: "processing",
    placed: "2026-10-27",
    card: "mastercard •••• 5100",
    ship_to: "Jordan Lee, 410 Pine Ave, Portland OR 97204, US",
  },
];

const CONFIG = {
  min_version: 1,
  features: { orders: true, support_chat: false, admin_export: false },
};

const DEFAULT_KEY = "pinlock-demo-signing-key";
const TOKEN_TTL = 24 * 60 * 60;

const enc = new TextEncoder();

function b64url(bytes) {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function b64urlJson(value) {
  return b64url(enc.encode(JSON.stringify(value)));
}

function fromB64url(s) {
  const bin = atob(s.replace(/-/g, "+").replace(/_/g, "/"));
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}

async function hmacKey(secret) {
  return crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, [
    "sign",
    "verify",
  ]);
}

async function signToken(user, secret, now) {
  const head = b64urlJson({ alg: "HS256", typ: "JWT" });
  const body = b64urlJson({ sub: user.id, email: user.email, iat: now, exp: now + TOKEN_TTL });
  const sig = await crypto.subtle.sign("HMAC", await hmacKey(secret), enc.encode(`${head}.${body}`));
  return `${head}.${body}.${b64url(new Uint8Array(sig))}`;
}

async function verifyToken(token, secret, now) {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const [head, body, sig] = parts;
  let ok = false;
  try {
    ok = await crypto.subtle.verify("HMAC", await hmacKey(secret), fromB64url(sig), enc.encode(`${head}.${body}`));
  } catch {
    return null;
  }
  if (!ok) return null;
  const claims = JSON.parse(new TextDecoder().decode(fromB64url(body)));
  if (typeof claims.exp !== "number" || claims.exp < now) return null;
  return USERS.find((u) => u.id === claims.sub) ?? null;
}

function json(status, value) {
  return new Response(JSON.stringify(value, null, 2), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

function publicUser(u) {
  const { password, ...rest } = u;
  return rest;
}

function publicOrder(o) {
  const { owner, ...rest } = o;
  return rest;
}

async function caller(request, secret, now) {
  const auth = request.headers.get("authorization") ?? "";
  const m = auth.match(/^Bearer\s+(.+)$/i);
  return m ? verifyToken(m[1].trim(), secret, now) : null;
}

export default {
  async fetch(request, env = {}) {
    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "") || "/";
    const secret = env.TOKEN_KEY || DEFAULT_KEY;
    const now = Math.floor(Date.now() / 1000);

    if (path === "/" && request.method === "GET") {
      return json(200, {
        service: "pinlock demo api",
        about: "Fake data for a certificate pinning demo. Nothing here is real.",
        source: "https://github.com/clickswave/pinlock",
      });
    }

    if (path === "/v1/config" && request.method === "GET") {
      return json(200, CONFIG);
    }

    if (path === "/v1/login" && request.method === "POST") {
      let body;
      try {
        body = await request.json();
      } catch {
        return json(400, { error: "bad_request", message: "expected a JSON body" });
      }
      const user = USERS.find((u) => u.email === String(body?.email ?? "").trim().toLowerCase());
      if (!user || user.password !== body?.password) {
        return json(401, { error: "invalid_credentials" });
      }
      return json(200, { token: await signToken(user, secret, now), user: publicUser(user) });
    }

    if (path === "/v1/me" || path.startsWith("/v1/orders")) {
      const user = await caller(request, secret, now);
      if (!user) return json(401, { error: "unauthorized" });
      if (request.method !== "GET") return json(405, { error: "method_not_allowed" });

      if (path === "/v1/me") return json(200, publicUser(user));

      if (path === "/v1/orders") {
        return json(200, { orders: ORDERS.filter((o) => o.owner === user.id).map(publicOrder) });
      }

      // The deliberate IDOR: any signed-in caller can read any order by id.
      const order = ORDERS.find((o) => o.id === path.slice("/v1/orders/".length));
      return order ? json(200, publicOrder(order)) : json(404, { error: "not_found" });
    }

    return json(404, { error: "not_found" });
  },
};
