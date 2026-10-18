const { test } = require("node:test");
const assert = require("node:assert/strict");
const { Providers } = require("../lib/providers.cjs");
function mock(result, settings = {}) {
  let request;
  const p = new Providers(
    () => settings,
    () => ({ gemini: "private-example", nvidia: "private-example" }),
    async (url, opts) => {
      request = {
        url,
        ...opts,
        body: opts.body ? JSON.parse(opts.body) : null,
      };
      return { ok: true, json: async () => result };
    },
  );
  return { p, request: () => request };
}
const root = (text) => ({
  status: "completed",
  steps: [
    {
      type: "model_output",
      content: [{ type: "text", text: JSON.stringify(text) }],
    },
  ],
});
test("Gemini matches Android non-stored structured Interactions contract", async () => {
  const { p, request } = mock(
    root({ reply: "안녕하세요", meaning: "မင်္ဂလာပါ" }),
  );
  await p.tutor({ prompt: "Hello" });
  const r = request();
  assert.match(r.url, /interactions$/);
  assert.equal(r.body.store, false);
  assert.equal(r.body.response_format.mime_type, "application/json");
  assert.equal(r.headers["x-goog-api-key"], "private-example");
  assert.ok(!JSON.stringify(r.body).includes("private-example"));
});
test("NVIDIA gets extracted text JSON without image or audio", async () => {
  const { p, request } = mock(
    {
      choices: [
        { message: { content: JSON.stringify({ reply: "안녕하세요" }) } },
      ],
    },
    { provider: "nvidia" },
  );
  await p.tutor({ prompt: "hello" });
  const r = request();
  assert.match(r.url, /chat\/completions$/);
  assert.equal(r.body.response_format.type, "json_object");
  assert.equal(r.body.chat_template_kwargs.enable_thinking, false);
  assert.equal(typeof r.body.messages[1].content, "string");
  await assert.rejects(
    p.generate({
      provider: "nvidia",
      instruction: "x",
      input: "x",
      schema: {},
      audio: { data: "AA==" },
    }),
    /NVIDIA/,
  );
});
test("PDF schema preserves source quotes and rejects English-only meaning", async () => {
  const valid = {
    summary: "မင်္ဂလာပါ",
    sentences: [{ quote: "안녕하세요.", meaning: "မင်္ဂလာပါ။" }],
  };
  const { p } = mock(root(valid));
  assert.equal(
    (await p.section({ section: "meaning", source: "안녕하세요." })).sentences
      .length,
    1,
  );
  const english = mock(
    root({
      summary: "Hello",
      sentences: [{ quote: "안녕하세요.", meaning: "Hello" }],
    }),
  );
  await assert.rejects(
    english.p.section({ section: "meaning", source: "안녕하세요." }),
    /Gemini/,
  );
  const invented = mock(
    root({
      summary: "မြန်မာ",
      sentences: [{ quote: "다른 문장", meaning: "မြန်မာ" }],
    }),
  );
  await assert.rejects(
    invented.p.section({ section: "meaning", source: "안녕하세요." }),
    /မူရင်း/,
  );
});
test("TTS requires actual audio; no delivery mode sent", async () => {
  const { p, request } = mock({
    status: "completed",
    steps: [
      {
        type: "model_output",
        content: [{ type: "audio", mime_type: "audio/wav", data: "UklGRg==" }],
      },
    ],
  });
  const a = await p.speech("안녕하세요");
  assert.equal(a.mime, "audio/wav");
  assert.equal(request().body.response_format.delivery, undefined);
  const empty = mock(root({ reply: "no audio" }));
  await assert.rejects(empty.p.speech("안녕"), /အသံ/);
});
test("quota, offline, missing key and timeouts never report success or expose credentials", async () => {
  for (const status of [401, 403, 404, 429, 503]) {
    const p = new Providers(
      () => ({}),
      () => ({ gemini: "secret" }),
      async () => ({ ok: false, status }),
    );
    await assert.rejects(
      p.catalog("gemini"),
      (e) => !e.message.includes("secret") && !!e.message,
    );
  }
  const noKey = new Providers(
    () => ({}),
    () => ({}),
  );
  await assert.rejects(noKey.catalog("gemini"), /API key/);
  const offline = new Providers(
    () => ({}),
    () => ({ gemini: "secret" }),
    async () => {
      throw new Error("secret in URL");
    },
  );
  await assert.rejects(
    offline.catalog("gemini"),
    (e) => !e.message.includes("secret"),
  );
});
