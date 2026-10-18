const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const ID = /^[a-zA-Z0-9_-]{1,100}$/;
const FILE = /^[a-f0-9]{64}\.(pdf|mp3|wav|m4a|ogg|webm|flac)$/;
const MAX_FILE = 80 * 1024 * 1024;
const defaults = () => ({
  format: "language-talk-windows",
  version: 1,
  books: [],
  pages: {},
  annotations: [],
  explanations: {},
  tracks: [],
  sessions: [],
  notebook: [],
  practice: {},
  preferences: {
    theme: "system",
    readerTheme: "paper",
    record: true,
    displayName: "",
    voice: "Kore",
    autoSpeak: true,
  },
});
function requireId(id) {
  if (typeof id !== "string" || !ID.test(id)) throw Error("Invalid item ID");
  return id;
}
function requireFile(name) {
  if (typeof name !== "string" || !FILE.test(name))
    throw Error("Invalid media reference");
  return name;
}
function text(value, max = 60000) {
  if (typeof value !== "string" || value.length > max)
    throw Error("Text is too large or invalid");
  return value;
}
function bytes(value, max = MAX_FILE) {
  const b = Buffer.from(value);
  if (!b.length || b.length > max) throw Error("File is empty or too large");
  return b;
}
function hash(buffer) {
  return crypto.createHash("sha256").update(buffer).digest("hex");
}
function trackNumber(name) {
  const stem = path.basename(name, path.extname(name));
  const explicit = stem.match(/(?:track|트랙|트렉)\s*[-_ ]*0*(\d{1,4})(?!\d)/i);
  if (explicit) return Number(explicit[1]) || null;
  const ns = stem.match(/\d+/g);
  return ns?.length === 1 && Number(ns[0]) > 0 && Number(ns[0]) <= 9999
    ? Number(ns[0])
    : null;
}
function parseJson(raw) {
  let clean = text(raw)
    .trim()
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/\s*```$/, "");
  try {
    const v = JSON.parse(clean);
    if (!v || typeof v !== "object" || Array.isArray(v)) throw Error();
    return v;
  } catch {
    throw Error(
      "AI ရလဒ်ပုံစံ မပြည့်စုံပါ။ ရထားပြီးသားအပိုင်းတွေ သိမ်းထားပြီး ကျန်အပိုင်းကို ပြန်စမ်းပါ။",
    );
  }
}
function wav(pcm, rate = 24000) {
  const data = Buffer.from(pcm);
  const header = Buffer.alloc(44);
  header.write("RIFF");
  header.writeUInt32LE(data.length + 36, 4);
  header.write("WAVEfmt ", 8);
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20);
  header.writeUInt16LE(1, 22);
  header.writeUInt32LE(rate, 24);
  header.writeUInt32LE(rate * 2, 28);
  header.writeUInt16LE(2, 32);
  header.writeUInt16LE(16, 34);
  header.write("data", 36);
  header.writeUInt32LE(data.length, 40);
  return Buffer.concat([header, data]);
}
function annotationRects(value) {
  if (value === undefined) return [];
  if (!Array.isArray(value) || value.length > 100)
    throw Error("Invalid annotation coordinates");
  if (
    value.some(
      (r) =>
        !Array.isArray(r) ||
        r.length !== 4 ||
        r.some((n) => !Number.isFinite(n) || n < 0 || n > 1),
    )
  )
    throw Error("Invalid annotation coordinates");
  return value;
}
function safeState(s) {
  if (!s || s.format !== "language-talk-windows" || s.version !== 1)
    throw Error(
      "This is not a supported Windows backup. Use a Windows backup JSON.",
    );
  for (const [k, limit] of Object.entries({
    books: 300,
    annotations: 10000,
    tracks: 10000,
    sessions: 3000,
    notebook: 10000,
  }))
    if (!Array.isArray(s[k]) || s[k].length > limit)
      throw Error("Invalid backup entries");
  for (const b of s.books) {
    requireId(b.id);
    requireFile(b.file);
    text(b.title, 240);
  }
  for (const tr of s.tracks) {
    requireId(tr.bookId);
    requireFile(tr.file);
    if (!Number.isInteger(tr.number) || tr.number < 1 || tr.number > 9999)
      throw Error("Invalid track");
  }
  for (const session of s.sessions) {
    requireId(session.id);
    if (!Array.isArray(session.clips) || session.clips.length > 3000)
      throw Error("Invalid replay");
    for (const c of session.clips) {
      requireId(c.id);
      if (c.file) requireFile(c.file);
      if (!["user", "ai"].includes(c.speaker)) throw Error("Invalid speaker");
      text(c.text, 60000);
    }
  }
  for (const a of s.annotations) {
    requireId(a.id);
    requireId(a.bookId);
    text(a.quote, 4000);
    text(a.note || "", 6000);
    annotationRects(a.rects);
    if (
      !Number.isInteger(a.page) ||
      a.page < 1 ||
      a.page > 10000 ||
      !["highlight", "underline", "bold", "note", "save"].includes(a.kind)
    )
      throw Error("Invalid annotation");
  }
  for (const item of s.notebook) {
    requireId(item.id);
    text(item.quote, 4000);
    text(item.note || "", 6000);
  }
  for (const k of ["pages", "explanations", "practice", "preferences"])
    if (!s[k] || typeof s[k] !== "object" || Array.isArray(s[k]))
      throw Error("Invalid backup state");
  if (JSON.stringify(s).length > 24 * 1024 * 1024)
    throw Error("Saved state is too large");
  return s;
}
class Store {
  constructor(dir) {
    this.dir = dir;
    this.media = path.join(dir, "media");
    fs.mkdirSync(this.media, { recursive: true });
    this.file = path.join(dir, "state.json");
    if (fs.existsSync(this.file)) {
      try {
        this.state = safeState(JSON.parse(fs.readFileSync(this.file, "utf8")));
      } catch {
        throw Error(
          "Saved data could not be read. Your files are preserved; restore state.json from a backup.",
        );
      }
    } else this.state = defaults();
  }
  save() {
    safeState(this.state);
    const tmp = this.file + ".tmp";
    fs.writeFileSync(tmp, JSON.stringify(this.state), { mode: 0o600 });
    fs.renameSync(tmp, this.file);
  }
  put(buffer, ext) {
    const b = bytes(buffer);
    const name = hash(b) + "." + ext;
    requireFile(name);
    const target = path.join(this.media, name);
    if (!fs.existsSync(target)) fs.writeFileSync(target, b);
    return name;
  }
  read(name) {
    requireFile(name);
    return fs.readFileSync(path.join(this.media, name));
  }
  session(title, source = null) {
    const s = {
      id: crypto.randomUUID(),
      title: text(title, 240),
      source,
      createdAt: Date.now(),
      clips: [],
    };
    this.state.sessions.unshift(s);
    this.save();
    return s;
  }
  clip(sessionId, data) {
    const s = this.state.sessions.find((x) => x.id === requireId(sessionId));
    if (!s) throw Error("Session not found");
    if (!["user", "ai"].includes(data.speaker)) throw Error("Invalid speaker");
    const c = {
      id: crypto.randomUUID(),
      speaker: data.speaker,
      text: text(data.text || ""),
      createdAt:
        Number.isFinite(data.createdAt) &&
        data.createdAt <= Date.now() &&
        data.createdAt > Date.now() - 86400000
          ? data.createdAt
          : Date.now(),
    };
    if (data.audio) {
      if (!["wav", "webm", "mp3", "m4a", "ogg"].includes(data.ext))
        throw Error("Unsupported recording");
      c.file = this.put(bytes(data.audio, 20 * 1024 * 1024), data.ext);
    }
    s.clips.push(c);
    this.save();
    return c;
  }
  prune() {
    const used = new Set([
      ...this.state.books.map((x) => x.file),
      ...this.state.tracks.map((x) => x.file),
      ...this.state.sessions.flatMap((x) =>
        x.clips.map((c) => c.file).filter(Boolean),
      ),
    ]);
    for (const file of fs.readdirSync(this.media))
      if (FILE.test(file) && !used.has(file))
        fs.unlinkSync(path.join(this.media, file));
  }
  backup() {
    const names = new Set([
      ...this.state.books.map((x) => x.file),
      ...this.state.tracks.map((x) => x.file),
      ...this.state.sessions.flatMap((x) =>
        x.clips.map((c) => c.file).filter(Boolean),
      ),
    ]);
    let total = 0;
    const files = {};
    for (const name of names) {
      const b = this.read(name);
      total += b.length;
      if (total > 180 * 1024 * 1024)
        throw Error(
          "Backup exceeds 180 MB. Export sessions separately or remove older recordings.",
        );
      files[name] = b.toString("base64");
    }
    return { ...this.state, files };
  }
  restore(raw) {
    const payload = JSON.parse(text(raw, 256 * 1024 * 1024));
    const files = payload.files || {};
    delete payload.files;
    const s = safeState(payload);
    const decoded = [];
    let size = 0;
    for (const [name, value] of Object.entries(files)) {
      requireFile(name);
      const b = bytes(Buffer.from(text(value, 112 * 1024 * 1024), "base64"));
      size += b.length;
      if (size > 180 * 1024 * 1024 || !name.startsWith(hash(b) + "."))
        throw Error("Backup media is invalid");
      decoded.push([name, b]);
    }
    const names = [
      ...s.books.map((x) => x.file),
      ...s.tracks.map((x) => x.file),
      ...s.sessions.flatMap((x) => x.clips.map((c) => c.file).filter(Boolean)),
    ];
    for (const name of names)
      if (!files[name] && !fs.existsSync(path.join(this.media, name)))
        throw Error("Backup is missing an original document or recording");
    for (const [name, b] of decoded)
      fs.writeFileSync(path.join(this.media, name), b);
    if (fs.existsSync(this.file))
      fs.copyFileSync(this.file, this.file + ".before-restore");
    delete s.files;
    this.state = s;
    this.save();
    return s;
  }
}
module.exports = {
  ID,
  MAX_FILE,
  Store,
  defaults,
  requireId,
  requireFile,
  text,
  bytes,
  hash,
  trackNumber,
  parseJson,
  wav,
  safeState,
  annotationRects,
};
