import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import ts from "typescript";

const source = await readFile(
  new URL("../src/features/cart/cartApi.ts", import.meta.url),
  "utf8",
);
const compiled = ts.transpileModule(source, {
  compilerOptions: {
    target: ts.ScriptTarget.ES2022,
    module: ts.ModuleKind.ES2022,
  },
}).outputText;
const { SessionCartClient } = await import(
  "data:text/javascript;base64," + Buffer.from(compiled).toString("base64")
);
const csrf = { token: "test-only-token", headerName: "X-CSRF-TOKEN" };
const cart = (revision) => ({
  cartToken: "test-cart",
  revision,
  items: [],
  subtotal: "0",
});

function mock(steps) {
  const calls = [];
  const previous = globalThis.fetch;
  globalThis.fetch = async (url, init) => {
    calls.push({ url, ...init });
    const step = steps.shift();
    assert.ok(step, "Unexpected extra request: " + url);
    if (step instanceof Error) throw step;
    const { status = 200, body } = step;
    return new Response(status === 204 ? null : JSON.stringify(body), {
      status,
    });
  };
  return {
    calls,
    restore() {
      globalThis.fetch = previous;
      assert.equal(steps.length, 0);
    },
  };
}

test("init is deduplicated and sequential; POST/PATCH use server revision; DELETE reads back", async () => {
  const m = mock([
    { body: cart(0) },
    { body: csrf },
    { body: cart(1) },
    { body: cart(2) },
    { status: 204 },
    { body: cart(3) },
  ]);
  try {
    const client = new SessionCartClient();
    await Promise.all([client.refresh(), client.refresh()]);
    await client.mutate("POST", "1", 1);
    await client.mutate("PATCH", "1", 3);
    await client.mutate("DELETE", "1");
    assert.deepEqual(
      m.calls.map((c) => c.url),
      [
        "/api/v1/cart",
        "/api/v1/auth/csrf",
        "/api/v1/cart/items",
        "/api/v1/cart/items/1",
        "/api/v1/cart/items/1?expectedRevision=2",
        "/api/v1/cart",
      ],
    );
    assert.equal(m.calls[2].headers["X-CSRF-TOKEN"], csrf.token);
    assert.equal(m.calls[2].credentials, "same-origin");
    assert.deepEqual(JSON.parse(m.calls[2].body), {
      productId: "1",
      quantity: 1,
      expectedRevision: 0,
    });
    assert.equal(JSON.parse(m.calls[3].body).expectedRevision, 1);
    assert.equal(client.cart.revision, 3);
  } finally {
    m.restore();
  }
});

for (const status of [403, 409, 503]) {
  test(`${status}: refresh session state but never replay mutation`, async () => {
    const m = mock([
      { body: cart(0) },
      { body: csrf },
      { status, body: { code: status === 409 ? "CART_CHANGED" : "API_ERROR" } },
      { body: cart(7) },
      { body: csrf },
    ]);
    try {
      const client = new SessionCartClient();
      await client.refresh();
      await assert.rejects(client.mutate("POST", "1", 1), /Đã tải lại giỏ/);
      assert.equal(client.cart.revision, 7);
      assert.equal(m.calls.filter((c) => c.method === "POST").length, 1);
      assert.equal(client.ready, true);
    } finally {
      m.restore();
    }
  });
}

test("422 preserves last confirmed cart and exposes business error", async () => {
  const m = mock([
    { body: cart(4) },
    { body: csrf },
    {
      status: 422,
      body: { code: "INSUFFICIENT_STOCK", message: "Số lượng vượt tồn kho." },
    },
  ]);
  try {
    const client = new SessionCartClient();
    await client.refresh();
    await assert.rejects(client.mutate("PATCH", "1", 99), /vượt tồn kho/);
    assert.equal(client.cart.revision, 4);
    assert.equal(client.ready, true);
  } finally {
    m.restore();
  }
});

test("lost response reads cart back and does not add twice", async () => {
  const m = mock([
    { body: cart(0) },
    { body: csrf },
    new TypeError("network"),
    { body: cart(1) },
    { body: csrf },
  ]);
  try {
    const client = new SessionCartClient();
    await client.refresh();
    await assert.rejects(client.mutate("POST", "1", 1), /chủ động/);
    assert.equal(client.cart.revision, 1);
    assert.equal(m.calls.filter((c) => c.method === "POST").length, 1);
  } finally {
    m.restore();
  }
});

test("failed reconciliation blocks further writes until successful reload", async () => {
  const m = mock([
    { body: cart(0) },
    { body: csrf },
    new TypeError("network"),
    new TypeError("network"),
  ]);
  try {
    const client = new SessionCartClient();
    await client.refresh();
    await assert.rejects(client.mutate("POST", "1", 1), /Không tải lại/);
    assert.equal(client.ready, false);
    await assert.rejects(client.mutate("POST", "1", 1), /tải lại giỏ hàng/);
    assert.equal(m.calls.length, 4);
  } finally {
    m.restore();
  }
});

test("only one pending write is allowed", async () => {
  const m = mock([{ body: cart(0) }, { body: csrf }, { body: cart(1) }]);
  try {
    const client = new SessionCartClient();
    await client.refresh();
    const first = client.mutate("POST", "1", 1);
    await assert.rejects(client.mutate("POST", "1", 1), /đang được lưu/);
    await first;
    assert.equal(m.calls.filter((c) => c.method === "POST").length, 1);
  } finally {
    m.restore();
  }
});
