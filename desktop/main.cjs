const {
  app,
  BrowserWindow,
  ipcMain,
  dialog,
  safeStorage,
  session,
  clipboard,
  Menu,
} = require("electron");
const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");
const { pathToFileURL } = require("node:url");
const WebSocket = require("ws");
const {
  Store,
  text,
  requireId,
  requireFile,
  bytes,
  hash,
  trackNumber,
  annotationRects,
  wav,
} = require("./lib/core.cjs");
const { Providers, MODELS, TUTOR } = require("./lib/providers.cjs");
if (process.env.LANGUAGE_TALK_TEST_DIR)
  app.setPath("userData", process.env.LANGUAGE_TALK_TEST_DIR);
app.setName("Language Talk");
let win,
  store,
  settings = {},
  secrets = {},
  live = null;
const index = path.join(__dirname, "src", "index.html");
const appUrl = pathToFileURL(index).href;
const assets = () =>
  app.isPackaged
    ? process.resourcesPath
    : path.join(__dirname, "..", "app", "src", "main", "assets");
const preferencesFile = () =>
  path.join(app.getPath("userData"), "api-settings.json");
function persistSettings() {
  if (
    Object.values(secrets).some(Boolean) &&
    !safeStorage.isEncryptionAvailable()
  )
    throw Error(
      "Windows secure key storage မရရှိပါ။ API key ကို disk ထဲမသိမ်းနိုင်ပါ။",
    );
  const encrypted = {};
  for (const [k, v] of Object.entries(secrets))
    if (v) encrypted[k] = safeStorage.encryptString(v).toString("base64");
  const tmp = preferencesFile() + ".tmp";
  fs.writeFileSync(tmp, JSON.stringify({ settings, encrypted }), {
    mode: 0o600,
  });
  fs.renameSync(tmp, preferencesFile());
}
function publicSettings() {
  return {
    ...settings,
    geminiKeySaved: !!secrets.gemini,
    nvidiaKeySaved: !!secrets.nvidia,
  };
}
function catalog() {
  return ["ttmik-beginner", "ttmik-intermediate"].map((id) =>
    JSON.parse(
      fs.readFileSync(path.join(assets(), "books", id + ".json"), "utf8"),
    ),
  );
}
function readPdf(id) {
  requireId(id);
  if (["ttmik-beginner", "ttmik-intermediate"].includes(id))
    return fs.readFileSync(path.join(assets(), "books", id + ".pdf"));
  const b = store.state.books.find((b) => b.id === id);
  if (!b) throw Error("Book not found");
  return store.read(b.file);
}
function trusted(event) {
  if (event.sender !== win?.webContents || event.senderFrame?.url !== appUrl)
    throw Error("Untrusted request");
}
const handle = (name, fn) =>
  ipcMain.handle(name, async (event, ...args) => {
    trusted(event);
    try {
      return { ok: true, value: await fn(...args) };
    } catch (e) {
      return { ok: false, error: e.message || "Operation failed" };
    }
  });
const sendLive = (data) => {
  if (win && !win.isDestroyed()) win.webContents.send("live-event", data);
};
function stopLive() {
  if (!live) return;
  clearTimeout(live.timer);
  live.ws.close();
  live = null;
  sendLive({ type: "closed" });
}
app
  .whenReady()
  .then(() => {
    store = new Store(app.getPath("userData"));
    if (fs.existsSync(preferencesFile())) {
      const saved = JSON.parse(fs.readFileSync(preferencesFile(), "utf8"));
      settings = saved.settings || {};
      for (const [p, c] of Object.entries(saved.encrypted || {})) {
        try {
          secrets[p] = safeStorage.decryptString(Buffer.from(c, "base64"));
        } catch {
          /* Key must be re-entered on a different Windows account. */
        }
      }
    }
    const providers = new Providers(
      () => ({ ...settings, voice: store.state.preferences.voice }),
      () => secrets,
    );
    handle("boot", () => ({
      version: app.getVersion(),
      state: store.state,
      courses: catalog(),
      curriculum: JSON.parse(
        fs.readFileSync(
          path.join(assets(), "learning", "curriculum.json"),
          "utf8",
        ),
      ),
      settings: publicSettings(),
    }));
    handle("paste-key", async () => {
      const value = (await clipboard.readText()).trim();
      if (!value || value.length > 600 || /[\r\n\s]/.test(value))
        throw Error("API key တစ်ခုတည်းကို Copy လုပ်ပြီး Paste နှိပ်ပါ။");
      return value;
    });
    handle("settings", (input) => {
      for (const field of [
        "provider",
        "geminiModel",
        "nvidiaModel",
        "speechModel",
        "liveModel",
      ])
        if (input[field] !== undefined)
          settings[field] = text(input[field], 200);
      for (const p of ["gemini", "nvidia"]) {
        const key = input[p + "Key"];
        if (key !== undefined && key !== "") secrets[p] = text(key.trim(), 600);
        if (input["clear" + p]) delete secrets[p];
      }
      persistSettings();
      return publicSettings();
    });
    handle("save", (action, d) => {
      switch (action) {
        case "preferences":
          for (const k of [
            "theme",
            "readerTheme",
            "record",
            "displayName",
            "voice",
            "autoSpeak",
            "readerFit",
          ])
            if (d[k] !== undefined) store.state.preferences[k] = d[k];
          break;
        case "page":
          requireId(d.bookId);
          if (!Number.isInteger(d.page) || d.page < 1 || d.page > 10000)
            throw Error("Invalid page");
          store.state.pages[d.bookId] = d.page;
          break;
        case "annotation": {
          requireId(d.bookId);
          const item = {
            id: crypto.randomUUID(),
            bookId: d.bookId,
            page: d.page,
            quote: text(d.quote, 4000),
            note: text(d.note || "", 6000),
            kind: d.kind,
            rects: annotationRects(d.rects),
            createdAt: Date.now(),
          };
          store.state.annotations.push(item);
          store.save();
          return item;
        }
        case "delete-annotation":
          store.state.annotations = store.state.annotations.filter(
            (x) => x.id !== d,
          );
          break;
        case "notebook": {
          const item = {
            ...d,
            id: crypto.randomUUID(),
            quote: text(d.quote, 4000),
            note: text(d.note || "", 6000),
            createdAt: Date.now(),
          };
          store.state.notebook.push(item);
          store.save();
          return item;
        }
        case "delete-note":
          store.state.notebook = store.state.notebook.filter((x) => x.id !== d);
          break;
        case "explanation":
          text(d.key, 160);
          store.state.explanations[d.key] = d.value;
          break;
        case "practice":
          requireId(d.id);
          store.state.practice[d.id] = d.value;
          break;
        case "clip-text": {
          const session = store.state.sessions.find(
            (s) => s.id === requireId(d.sessionId),
          );
          const clip = session?.clips.find((c) => c.id === requireId(d.clipId));
          if (!clip) throw Error("Recording not found");
          clip.text = text(d.text);
          break;
        }
        case "delete-session":
          store.state.sessions = store.state.sessions.filter((x) => x.id !== d);
          break;
        default:
          throw Error("Unknown action");
      }
      store.save();
      if (action === "delete-session") store.prune();
      return store.state;
    });
    handle("import-pdf", async () => {
      const r = await dialog.showOpenDialog(win, {
        title: "PDF စာအုပ်ရွေးပါ",
        filters: [{ name: "PDF books", extensions: ["pdf"] }],
        properties: ["openFile"],
      });
      if (r.canceled) return null;
      const file = r.filePaths[0];
      if (fs.statSync(file).size > 80 * 1024 * 1024)
        throw Error("PDF must be 80 MB or smaller");
      const b = bytes(fs.readFileSync(file));
      if (!b.subarray(0, 1024).includes(Buffer.from("%PDF-")))
        throw Error("Not a PDF file");
      const id = "book-" + hash(b).slice(0, 24);
      if (store.state.books.some((x) => x.id === id))
        return store.state.books.find((x) => x.id === id);
      const book = {
        id,
        title: path.basename(file, ".pdf"),
        file: store.put(b, "pdf"),
        createdAt: Date.now(),
      };
      store.state.books.push(book);
      store.save();
      return book;
    });
    handle("pdf", (id) => readPdf(id));
    handle("tracks", async (bookId, page, number) => {
      requireId(bookId);
      if (!Number.isInteger(page) || page < 1) throw Error("Invalid page");
      if (
        number !== undefined &&
        (!Number.isInteger(number) || number < 1 || number > 9999)
      )
        throw Error("Track must be 1–9999");
      const r = await dialog.showOpenDialog(win, {
        title: "မူရင်း Track အသံဖိုင်ရွေးပါ",
        filters: [
          {
            name: "Audio",
            extensions: ["mp3", "wav", "m4a", "ogg", "webm", "flac"],
          },
        ],
        properties: ["openFile", ...(number ? [] : ["multiSelections"])],
      });
      if (r.canceled) return { tracks: store.state.tracks, skipped: 0 };
      let skipped = 0;
      for (const p of r.filePaths) {
        const n = number || trackNumber(path.basename(p));
        const ext = path.extname(p).slice(1).toLowerCase();
        if (
          !n ||
          fs.statSync(p).size > 48 * 1024 * 1024 ||
          !["mp3", "wav", "m4a", "ogg", "webm", "flac"].includes(ext)
        ) {
          skipped++;
          continue;
        }
        const file = store.put(fs.readFileSync(p), ext);
        const old = store.state.tracks.find(
          (x) => x.bookId === bookId && x.number === n,
        );
        const pages = [
          ...new Set([...(old?.pages || []), ...(number ? [page] : [])]),
        ];
        store.state.tracks = store.state.tracks.filter(
          (x) => !(x.bookId === bookId && x.number === n),
        );
        store.state.tracks.push({
          bookId,
          number: n,
          file,
          title: path.basename(p),
          pages,
        });
      }
      store.save();
      return { tracks: store.state.tracks, skipped };
    });
    handle("media", (name) => {
      requireFile(name);
      if (
        ![
          ...store.state.tracks,
          ...store.state.sessions.flatMap((x) => x.clips),
        ].some((x) => x.file === name)
      )
        throw Error("Media not found");
      return store.read(name);
    });
    handle("session", (title, source) => store.session(title, source));
    handle("clip", (id, d) =>
      store.clip(id, { ...d, audio: d.audio ? Buffer.from(d.audio) : null }),
    );
    handle("api", async (action, d = {}) => {
      switch (action) {
        case "catalog":
          return providers.catalog(d.provider);
        case "tutor":
          return providers.tutor(d);
        case "roleplay":
          return providers.roleplay(d);
        case "section":
          return providers.section(d);
        case "ask":
          return providers.tutor({
            ...d,
            context: "Explain this source in Myanmar: " + text(d.source, 16000),
          });
        case "speech":
          return providers.speech(d.text);
        case "transcribe":
          return providers.transcribe(d.audio);
        case "probe": {
          if (d.feature === "text")
            return providers.tutor({
              provider: d.provider,
              prompt: "Say 안녕하세요 with its Myanmar meaning.",
            });
          if (d.feature === "pdf")
            return providers.section({
              provider: d.provider,
              section: "meaning",
              source: "안녕하세요. 저는 학생이에요.",
            });
          if (d.provider !== "gemini")
            throw Error(
              "ဒီ feature က Gemini audio key လိုအပ်ပါသည်။ NVIDIA text endpoint မှာ မပါပါ။",
            );
          if (d.feature === "speech")
            return providers.speech("안녕하세요. 만나서 반갑습니다.");
          if (d.feature === "transcribe") {
            const speech = await providers.speech("안녕하세요.");
            return {
              heardText: await providers.transcribe({
                data: speech.data,
                mime: speech.mime,
              }),
            };
          }
          throw Error("Live ကို Practice → Live မှာ Mic နဲ့တိုက်ရိုက်စမ်းပါ။");
        }
        default:
          throw Error("Unknown API operation");
      }
    });
    handle("backup", async () => {
      const r = await dialog.showSaveDialog(win, {
        defaultPath: "Language-Talk-Windows-backup.json",
        filters: [{ name: "Backup", extensions: ["json"] }],
      });
      if (r.canceled) return false;
      const b = JSON.stringify(store.backup());
      fs.writeFileSync(r.filePath + ".tmp", b);
      fs.renameSync(r.filePath + ".tmp", r.filePath);
      return true;
    });
    handle("restore", async () => {
      const r = await dialog.showOpenDialog(win, {
        filters: [
          { name: "Language Talk Windows backup", extensions: ["json"] },
        ],
        properties: ["openFile"],
      });
      if (r.canceled) return null;
      const p = r.filePaths[0];
      if (fs.statSync(p).size > 256 * 1024 * 1024)
        throw Error("Backup is larger than 256 MB");
      const check = await dialog.showMessageBox(win, {
        type: "question",
        buttons: ["Cancel", "Restore"],
        defaultId: 0,
        cancelId: 0,
        message: "ဒီ Windows app ဒေတာကို backup နဲ့အစားထိုးမည်။",
        detail:
          "လက်ရှိ state.json ကို before-restore အဖြစ်လည်း သိမ်းပေးမည်။ API key မပြောင်းပါ။",
      });
      if (check.response !== 1) return null;
      return store.restore(fs.readFileSync(p, "utf8"));
    });
    handle("export-session", async (id) => {
      const s = store.state.sessions.find((x) => x.id === requireId(id));
      if (!s) throw Error("Session not found");
      const r = await dialog.showSaveDialog(win, {
        defaultPath: "Language-Talk-session.json",
        filters: [{ name: "Replay with audio", extensions: ["json"] }],
      });
      if (r.canceled) return false;
      const files = {};
      for (const c of s.clips)
        if (c.file) files[c.file] = store.read(c.file).toString("base64");
      fs.writeFileSync(
        r.filePath,
        JSON.stringify({
          format: "language-talk-replay",
          version: 1,
          session: s,
          files,
        }),
      );
      return true;
    });
    handle("live-start", async (d) => {
      if (live) throw Error("Live session တစ်ခု ဖွင့်ထားပြီးပါပြီ။");
      if (!secrets.gemini) throw Error("Gemini API key ထည့်ပါ။");
      const s = store.session(d.title || "Live conversation", null);
      const ws = new WebSocket(
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent",
        {
          headers: { "x-goog-api-key": secrets.gemini },
          maxPayload: 8 * 1024 * 1024,
          handshakeTimeout: 20000,
        },
      );
      const state = {
        ws,
        session: s.id,
        pcm: [],
        pcmBytes: 0,
        output: "",
        input: "",
        ready: false,
        timer: setTimeout(() => stopLive(), 20 * 60 * 1000),
      };
      live = state;
      ws.on("open", () => {
        if (live !== state) return;
        ws.send(
          JSON.stringify({
            setup: {
              model: "models/" + (settings.liveModel || MODELS.live),
              generationConfig: {
                responseModalities: ["AUDIO"],
                speechConfig: {
                  voiceConfig: {
                    prebuiltVoiceConfig: {
                      voiceName: store.state.preferences.voice || "Kore",
                    },
                  },
                },
              },
              systemInstruction: {
                parts: [
                  {
                    text:
                      "You are a friendly Korean speaking partner for a Myanmar learner. Speak Korean only, slowly and clearly. Ask one question and wait. Offer a short answer frame if the learner needs help. Never simulate the learner. Topic: " +
                      text(d.context || "Everyday workplace greetings", 3000),
                  },
                ],
              },
              inputAudioTranscription: {},
              outputAudioTranscription: {},
              realtimeInputConfig: {
                automaticActivityDetection: {
                  silenceDurationMs: 1500,
                  prefixPaddingMs: 200,
                },
              },
            },
          }),
        );
      });
      ws.on("message", (raw) => {
        if (live !== state) return;
        try {
          const m = JSON.parse(raw.toString());
          if (m.setupComplete) {
            state.ready = true;
            sendLive({ type: "ready", session: s });
            ws.send(
              JSON.stringify({
                clientContent: {
                  turns: [
                    {
                      role: "user",
                      parts: [
                        {
                          text: "Please greet me and ask the first short question.",
                        },
                      ],
                    },
                  ],
                  turnComplete: true,
                },
              }),
            );
          }
          if (m.error)
            throw Error("Live provider error. Check model, access and quota.");
          const sc = m.serverContent;
          if (sc) {
            if (sc.interrupted) {
              state.pcm = [];
              state.pcmBytes = 0;
              state.output = "";
              sendLive({ type: "interrupted" });
            }
            if (sc.inputTranscription?.text) {
              const t = sc.inputTranscription.text;
              state.input = t.startsWith(state.input) ? t : state.input + t;
              sendLive({ type: "input", text: state.input });
            }
            if (sc.outputTranscription?.text) {
              const t = sc.outputTranscription.text;
              state.output = t.startsWith(state.output) ? t : state.output + t;
              sendLive({ type: "output", text: state.output });
            }
            for (const p of sc.modelTurn?.parts || []) {
              const a = p.inlineData;
              if (a?.data) {
                const b = Buffer.from(a.data, "base64");
                state.pcmBytes += b.length;
                if (state.pcmBytes > 20 * 1024 * 1024)
                  throw Error("Live audio turn is too large");
                if (!state.aiStarted) state.aiStarted = Date.now();
                state.pcm.push(b);
                sendLive({ type: "audio", data: a.data });
              }
            }
            if (sc.turnComplete) {
              if (store.state.preferences.record) {
                if (state.pcm.length)
                  store.clip(s.id, {
                    speaker: "ai",
                    text: state.output,
                    createdAt: state.aiStarted,
                    audio: wav(Buffer.concat(state.pcm)),
                    ext: "wav",
                  });
              }
              sendLive({
                type: "turn",
                input: state.input,
                output: state.output,
              });
              state.pcm = [];
              state.pcmBytes = 0;
              state.input = "";
              state.output = "";
              state.aiStarted = null;
            }
          }
        } catch (e) {
          sendLive({ type: "error", error: e.message });
          stopLive();
        }
      });
      ws.on("error", () => {
        sendLive({
          type: "error",
          error:
            "Live connection မရပါ။ Internet, model နဲ့ key access ကိုစစ်ပါ။",
        });
        stopLive();
      });
      ws.on("close", () => {
        if (live === state) {
          clearTimeout(state.timer);
          live = null;
          sendLive({ type: "closed" });
        }
      });
      return { session: s };
    });
    handle("live-stop", () => {
      stopLive();
      return true;
    });
    ipcMain.on("live-audio", (event, data) => {
      trusted(event);
      if (
        live?.ready &&
        live.ws.readyState === WebSocket.OPEN &&
        typeof data === "string" &&
        data.length < 64000
      )
        live.ws.send(
          JSON.stringify({
            realtimeInput: {
              audio: { mimeType: "audio/pcm;rate=16000", data },
            },
          }),
        );
    });
    win = new BrowserWindow({
      width: 1380,
      height: 900,
      minWidth: 960,
      minHeight: 700,
      title: "Language Talk",
      backgroundColor: "#f5f6fa",
      icon: path.join(__dirname, "assets", "icon.png"),
      webPreferences: {
        preload: path.join(__dirname, "preload.cjs"),
        contextIsolation: true,
        nodeIntegration: false,
        sandbox: true,
        webSecurity: true,
      },
    });
    const trustedMedia = (contents, permission, details) =>
      contents === win.webContents &&
      permission === "media" &&
      (!details?.mediaTypes || details.mediaTypes.every((t) => t === "audio"));
    session.defaultSession.setPermissionRequestHandler(
      (contents, permission, callback, details) =>
        callback(
          trustedMedia(contents, permission, details) &&
            details.requestingUrl === appUrl,
        ),
    );
    session.defaultSession.setPermissionCheckHandler(
      (contents, permission, _origin, details) =>
        trustedMedia(contents, permission, details),
    );
    Menu.setApplicationMenu(
      Menu.buildFromTemplate([
        { label: "Language Talk", submenu: [{ role: "quit" }] },
        {
          label: "Edit",
          submenu: [
            { role: "undo" },
            { role: "redo" },
            { type: "separator" },
            { role: "cut" },
            { role: "copy" },
            { role: "paste" },
            { role: "selectAll" },
          ],
        },
        { label: "View", submenu: [{ role: "togglefullscreen" }] },
      ]),
    );
    win.setMenuBarVisibility(false);
    win.webContents.on("context-menu", (_event, params) => {
      if (params.isEditable)
        Menu.buildFromTemplate([
          { role: "undo", enabled: params.editFlags.canUndo },
          { role: "redo", enabled: params.editFlags.canRedo },
          { type: "separator" },
          { role: "cut", enabled: params.editFlags.canCut },
          { role: "copy", enabled: params.editFlags.canCopy },
          { role: "paste", enabled: params.editFlags.canPaste },
          { role: "selectAll" },
        ]).popup({ window: win });
      else if (params.selectionText)
        Menu.buildFromTemplate([{ role: "copy" }]).popup({ window: win });
    });
    win.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
    win.webContents.on("will-navigate", (e) => e.preventDefault());
    win.webContents.on("will-attach-webview", (e) => e.preventDefault());
    win.on("closed", () => {
      stopLive();
      win = null;
    });
    win.loadFile(index);
  })
  .catch((e) => {
    dialog.showErrorBox("Language Talk", e.message);
    app.quit();
  });
app.on("window-all-closed", () => app.quit());
