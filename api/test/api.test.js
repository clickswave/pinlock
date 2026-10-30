import { test } from "node:test";
import assert from "node:assert/strict";
import api from "../src/index.js";

const base = "https://pinlock.clickswave.org";

async function call(path, init = {}) {
  const res = await api.fetch(new Request(base + path, init), {});
  return { status: res.status, body: await res.json() };
}

async function login(email, password) {
  return call("/v1/login", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
}

function bearer(token) {
  return { headers: { authorization: `Bearer ${token}` } };
}

test("login returns a token and never the password", async () => {
  const { status, body } = await login("demo@example.com", "correct-horse");
  assert.equal(status, 200);
  assert.match(body.token, /^eyJ[\w-]+\.[\w-]+\.[\w-]+$/);
  assert.equal(body.user.name, "Sam Carter");
  assert.equal(body.user.password, undefined);
});

test("wrong password is refused", async () => {
  assert.equal((await login("demo@example.com", "nope")).status, 401);
  assert.equal((await login("nobody@example.com", "correct-horse")).status, 401);
});

test("me and orders need a valid token", async () => {
  assert.equal((await call("/v1/me")).status, 401);
  assert.equal((await call("/v1/me", bearer("eyJhbGciOiJIUzI1NiJ9.e30.AAAA"))).status, 401);

  const { body } = await login("demo@example.com", "correct-horse");
  const tampered = body.token.slice(0, -2) + (body.token.endsWith("A") ? "BB" : "AA");
  assert.equal((await call("/v1/me", bearer(tampered))).status, 401);

  const me = await call("/v1/me", bearer(body.token));
  assert.equal(me.status, 200);
  assert.equal(me.body.email, "demo@example.com");
});

test("the order list holds only the caller's orders", async () => {
  const { body } = await login("demo@example.com", "correct-horse");
  const { status, body: list } = await call("/v1/orders", bearer(body.token));
  assert.equal(status, 200);
  assert.deepEqual(list.orders.map((o) => o.id), ["o_5001", "o_5002"]);
  assert.equal(list.orders[0].owner, undefined);
});

test("a single order is readable by any signed-in user (the planted IDOR)", async () => {
  const { body } = await login("demo@example.com", "correct-horse");
  const other = await call("/v1/orders/o_5003", bearer(body.token));
  assert.equal(other.status, 200);
  assert.equal(other.body.ship_to.startsWith("Jordan Lee"), true);
  assert.equal((await call("/v1/orders/o_9999", bearer(body.token))).status, 404);
});

test("config and the root answer without a token", async () => {
  assert.equal((await call("/v1/config")).body.features.orders, true);
  assert.equal((await call("/")).status, 200);
  assert.equal((await call("/nope")).status, 404);
});
