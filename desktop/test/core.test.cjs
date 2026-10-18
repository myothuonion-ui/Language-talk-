const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const {
  Store,
  defaults,
  hash,
  trackNumber,
  parseJson,
  wav,
  requireFile,
  safeState,
} = require("../lib/core.cjs");
const temp = () => fs.mkdtempSync(path.join(os.tmpdir(), "language-talk-"));
test("Track numbers accept explicit names and reject ambiguous names", () => {
  assert.equal(trackNumber("Track 002.mp3"), 2);
  assert.equal(trackNumber("001.mp3"), 1);
  assert.equal(trackNumber("chapter 2 audio 4.mp3"), null);
  assert.equal(trackNumber("Track 04 - lesson 2.mp3"), 4);
  assert.equal(trackNumber("sound.mp3"), null);
});
test("state, annotations, imported books and original audio survive restart", () => {
  const dir = temp(),
    s = new Store(dir);
  const file = s.put(Buffer.from("%PDF-demo"), "pdf");
  s.state.books.push({ id: "book-1", title: "Study", file });
  s.state.annotations.push({
    id: "note-1",
    bookId: "book-1",
    page: 4,
    quote: "안녕하세요",
    note: "မင်္ဂလာပါ",
    kind: "highlight",
  });
  s.state.pages["book-1"] = 4;
  s.state.explanations["book-1:4:meaning"] = { summary: "မြန်မာ" };
  s.save();
  const session = s.session("Practice");
  const clip = s.clip(session.id, {
    speaker: "user",
    text: "안녕",
    audio: wav(Buffer.alloc(3200), 16000),
    ext: "wav",
  });
  const reopened = new Store(dir);
  assert.equal(reopened.state.annotations[0].note, "မင်္ဂလာပါ");
  assert.equal(reopened.state.pages["book-1"], 4);
  assert.equal(
    reopened.state.explanations["book-1:4:meaning"].summary,
    "မြန်မာ",
  );
  assert.equal(reopened.read(clip.file).subarray(0, 4).toString(), "RIFF");
  fs.rmSync(dir, { recursive: true });
});
test("portable backup restores documents, tracks and paired replays without secrets", () => {
  const dir = temp(),
    s = new Store(dir);
  const file = s.put(Buffer.from("%PDF-1"), "pdf"),
    audio = s.put(Buffer.from("original mp3"), "mp3");
  s.state.books.push({ id: "book-1", title: "Book", file });
  s.state.tracks.push({ bookId: "book-1", number: 3, pages: [5], file: audio });
  const session = s.session("Speaking");
  s.clip(session.id, {
    speaker: "user",
    text: "제 이름은",
    audio: wav(Buffer.alloc(3200)),
    ext: "wav",
  });
  s.clip(session.id, {
    speaker: "ai",
    text: "반가워요",
    audio: wav(Buffer.alloc(4800)),
    ext: "wav",
  });
  const backup = s.backup();
  assert.ok(!JSON.stringify(backup).includes("apiKey"));
  const target = temp(),
    restored = new Store(target);
  restored.restore(JSON.stringify(backup));
  assert.equal(restored.state.sessions[0].clips.length, 2);
  assert.equal(restored.read(audio).toString(), "original mp3");
  assert.equal(restored.state.tracks[0].pages[0], 5);
  fs.rmSync(dir, { recursive: true });
  fs.rmSync(target, { recursive: true });
});
test("invalid restore leaves existing state intact and rejects traversal and missing media", () => {
  const dir = temp(),
    s = new Store(dir);
  const before = JSON.stringify(s.state);
  for (const name of ["../../key", "/etc/passwd", "../data.wav"])
    assert.throws(() => requireFile(name));
  const bad = { ...defaults(), files: { "../secret": "abc" } };
  assert.throws(() => s.restore(JSON.stringify(bad)));
  assert.equal(JSON.stringify(s.state), before);
  const missing = {
    ...defaults(),
    books: [{ id: "book-1", title: "Book", file: "a".repeat(64) + ".pdf" }],
    files: {},
  };
  assert.throws(() => s.restore(JSON.stringify(missing)), /missing/);
  assert.equal(JSON.stringify(s.state), before);
  fs.rmSync(dir, { recursive: true });
});
test("malformed backup, duplicates and invalid recording speakers are rejected", () => {
  assert.throws(() => safeState({ format: "language-talk-backup" }));
  const dir = temp(),
    s = new Store(dir);
  const session = s.session("x");
  assert.throws(() => s.clip(session.id, { speaker: "system", text: "x" }));
  assert.throws(() => s.put(Buffer.alloc(0), "wav"));
  assert.throws(
    () =>
      s.restore(
        JSON.stringify({
          ...defaults(),
          files: {
            ["a".repeat(64) + ".wav"]:
              Buffer.from("wrong digest").toString("base64"),
          },
        }),
      ),
    /invalid/,
  );
  fs.rmSync(dir, { recursive: true });
});
test("WAV header has correct size and sample rate", () => {
  const b = wav(Buffer.alloc(32000), 16000);
  assert.equal(b.length, 32044);
  assert.equal(b.readUInt32LE(24), 16000);
  assert.equal(b.readUInt32LE(40), 32000);
});
test("JSON fences tolerated; truncated or plain prose fails explicitly", () => {
  assert.deepEqual(parseJson('```json\n{"reply":"안녕하세요"}\n```'), {
    reply: "안녕하세요",
  });
  assert.throws(() => parseJson("a Myanmar prose response"));
  assert.throws(() => parseJson('{"reply":'));
});
test("annotation coordinates survive backup and reject invalid values", () => {
  const { annotationRects, safeState, defaults } = require("../lib/core.cjs");
  assert.deepEqual(annotationRects([[0.1, 0.2, 0.3, 0.04]]), [
    [0.1, 0.2, 0.3, 0.04],
  ]);
  for (const bad of [[[NaN, 0, 0.2, 0.1]], [[1.5, 0, 0.1, 0.1]], [[0, 0, 1]]])
    assert.throws(() => annotationRects(bad));
  const s = defaults();
  s.annotations.push({
    id: "note",
    bookId: "book",
    page: 15,
    kind: "highlight",
    quote: "안녕",
    note: "",
    rects: [[0.1, 0.2, 0.3, 0.04]],
  });
  assert.deepEqual(
    safeState(JSON.parse(JSON.stringify(s))).annotations[0].rects,
    [[0.1, 0.2, 0.3, 0.04]],
  );
});
