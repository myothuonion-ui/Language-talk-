const { contextBridge, ipcRenderer } = require("electron");
const call = (channel, ...args) => ipcRenderer.invoke(channel, ...args);
contextBridge.exposeInMainWorld("languageTalk", {
  boot: () => call("boot"),
  pasteKey: () => call("paste-key"),
  save: (action, data) => call("save", action, data),
  importPdf: () => call("import-pdf"),
  pdf: (id) => call("pdf", id),
  tracks: (book, page, number) => call("tracks", book, page, number),
  media: (file) => call("media", file),
  settings: (settings) => call("settings", settings),
  api: (action, data) => call("api", action, data),
  session: (title, source) => call("session", title, source),
  clip: (id, data) => call("clip", id, data),
  backup: () => call("backup"),
  restore: () => call("restore"),
  exportSession: (id) => call("export-session", id),
  liveStart: (data) => call("live-start", data),
  liveStop: () => call("live-stop"),
  liveAudio: (data) => ipcRenderer.send("live-audio", data),
  onLive: (callback) => {
    const handler = (_event, data) => callback(data);
    ipcRenderer.on("live-event", handler);
    return () => ipcRenderer.removeListener("live-event", handler);
  },
});
