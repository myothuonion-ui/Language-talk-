import * as pdfjs from "../node_modules/pdfjs-dist/build/pdf.mjs";
pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  "../node_modules/pdfjs-dist/build/pdf.worker.mjs",
  import.meta.url,
).href;
const $ = (s, r = document) => r.querySelector(s),
  $$ = (s, r = document) => [...r.querySelectorAll(s)],
  el = $("#content"),
  bridge = window.languageTalk;
let data,
  state,
  courses,
  curriculum,
  settings,
  view = "books",
  book = null,
  pdf = null,
  pageNo = 1,
  pageText = "",
  pageImage = "",
  readerTab = "meaning",
  selection = "",
  renderGeneration = 0,
  pdfRenderTask = null,
  zoom = 1,
  practiceMode = "daily",
  unitIndex = 0,
  turnIndex = 0,
  lessonTurns = [],
  lessonSource = null,
  history = [],
  sessionId = null,
  recorder = null,
  micStream = null,
  liveActive = false,
  liveReady = false,
  liveAudioCtx = null,
  liveNode = null,
  liveSource = null,
  liveUnsubscribe = null,
  userPcm = [],
  liveNextTime = 0,
  liveSources = [],
  replayId = null,
  replayIndex = 0,
  replayFilter = "all",
  replayFiles = [],
  replayLoop = null,
  replayA = null,
  replayB = null,
  audioUrls = [],
  blobUrls = [];
const icons = {
  books:
    '<path d="M3 4q5-2 9 1 4-3 9-1v15q-5-2-9 1-4-3-9-1z"/><path d="M12 5v15M6 8h3m-3 4h3m6-4h3m-3 4h3"/>',
  mic: '<rect x="8" y="2" width="8" height="13" rx="4"/><path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3m-4 0h8"/>',
  replay: '<path d="M3 9a9 9 0 1 1 0 6M3 3v6h6"/><path d="m10 8 6 4-6 4z"/>',
  user: '<circle cx="12" cy="7" r="4"/><path d="M4 22v-3a8 8 0 0 1 16 0v3"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 1v2m0 18v2M1 12h2m18 0h2M4 4l2 2m12 12 2 2M4 20l2-2M18 6l2-2"/>',
  play: '<path d="m7 3 14 9-14 9z"/>',
  stop: '<rect x="5" y="5" width="14" height="14" rx="2"/>',
  chevron: '<path d="m9 5 7 7-7 7"/>',
  plus: '<path d="M12 4v16M4 12h16"/>',
  sound:
    '<path d="m4 9 5-4v14l-5-4H1V9zM13 8a6 6 0 0 1 0 8m4-12a11 11 0 0 1 0 16"/>',
};
const icon = (n) =>
  `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icons[n] || icons.books}</svg>`;
const esc = (v) =>
  String(v ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const fmt = (t) =>
  new Date(t).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
function toast(message) {
  const t = $("#toast");
  t.textContent = message;
  t.classList.add("show");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => t.classList.remove("show"), 5500);
}
async function call(p) {
  const r = await p;
  if (!r.ok) throw Error(r.error);
  return r.value;
}
async function action(button, fn) {
  if (button) button.disabled = true;
  try {
    return await fn();
  } catch (e) {
    toast(e.message);
    const output = $("#operation-error");
    if (output) {
      output.textContent = e.message;
      output.hidden = false;
    }
  } finally {
    if (button?.isConnected) button.disabled = false;
  }
}
function applyTheme() {
  const chosen = state.preferences.theme || "system";
  document.body.dataset.theme =
    chosen === "system"
      ? matchMedia("(prefers-color-scheme: dark)").matches
        ? "dark"
        : "light"
      : chosen;
}
async function save(actionName, value) {
  const result = await call(bridge.save(actionName, value));
  if (
    [
      "preferences",
      "page",
      "practice",
      "delete-annotation",
      "delete-note",
      "delete-session",
      "explanation",
    ].includes(actionName)
  )
    state = result;
  return result;
}
function modal(html) {
  const d = $("#modal");
  d.innerHTML = `<button class="close ghost" aria-label="Close dialog">✕</button>${html}`;
  $(".close", d).onclick = () => d.close();
  d.showModal();
  return d;
}
function title(k, title, sub, actions = "") {
  return `<div class="page-title"><div><div class="eyebrow">${k}</div><h1>${title}</h1><p class="muted">${sub}</p></div><div class="row">${actions}</div></div>`;
}
async function refresh() {
  data = await call(bridge.boot());
  ({ state, courses, curriculum, settings } = data);
  applyTheme();
  $("#version").textContent = data.version;
  $("#api-badge").textContent =
    settings.geminiKeySaved || settings.nvidiaKeySaved
      ? "ကိုယ့် API နဲ့ လေ့လာပါ"
      : "Offline books · AI key ထည့်နိုင်သည်";
}
async function navigate(next) {
  if (recorder || liveActive) {
    toast("အသံဖမ်းခြင်း / Live ကို အရင်ရပ်ပါ။");
    return;
  }
  if (view === "replays") cleanupReplay();
  view = next;
  book = null;
  renderGeneration++;
  $$("[data-nav]").forEach((b) =>
    b.classList.toggle("active", b.dataset.nav === next),
  );
  $("#breadcrumb").textContent = {
    books: "Your library / စာအုပ်စင်",
    practice: "Daily practice / စကားပြောလေ့ကျင့်",
    replays: "Your voice, your progress / အသံမှတ်တမ်း",
    me: "Your personal space / ကိုယ်ပိုင်နေရာ",
  }[next];
  await refresh();
  ({
    books: showBooks,
    practice: showPractice,
    replays: showReplays,
    me: showMe,
  })[next]();
}
function showBooks() {
  const all = [...courses, ...state.books];
  el.innerHTML =
    title(
      "READ · UNDERSTAND · SPEAK",
      "စာအုပ်တစ်မျက်နှာကနေ စလိုက်ပါ",
      "စာဖတ်၊ နားထောင်ပြီး လက်တွေ့စကားပြောအထိ ဆက်လေ့ကျင့်ပါ။",
      `<button id="import-pdf" class="primary">${icon("plus")} PDF ထည့်မယ်</button>`,
    ) +
    `<section class="hero"><div class="copy"><div class="eyebrow">A LITTLE KOREAN, EVERY DAY</div><h1>ဖတ်ရင်း နားလည်၊<br>ပြောရင်း တိုးတက်။</h1><p>ကိုရီးယားစာကို မြန်မာလို နားလည်အောင်ဖတ်ပြီး<br>ကိုယ့်အသံနဲ့ ပြန်လေ့ကျင့်ဖို့ သီးသန့်နေရာ။</p><button id="continue" class="primary">ဆက်ဖတ်မယ် ${icon("chevron")}</button></div><img src="logo.svg" alt="Book and voice"></section><div class="section-heading"><h2>Your bookshelf <span class="muted small">· ${all.length} books</span></h2><span class="muted small">PDF နဲ့ သိမ်းထားတဲ့မှတ်စုတွေ offline ဖတ်နိုင်သည်</span></div><div class="grid">${all
      .map((b, i) => {
        const p = state.pages[b.id] || 1;
        return `<article class="card book-card"><div class="cover ${i % 2 ? "coral" : ""}"><span>LANGUAGE TALK<br>KOREAN COLLECTION</span><strong>${b.level === "Intermediate" ? "Korean<br>Talk" : b.level ? "Everyday<br>Korean" : "My<br>Library"}</strong><span>${b.level || "PERSONAL PDF"}</span></div><div class="details"><span class="tag">${esc(b.level || "Imported PDF")}</span><h3>${esc(b.title)}</h3><p>${b.chapters ? `${b.chapters.length} chapters · ${b.pageCount} pages` : "ကိုယ်တိုင်ထည့်ထားသော စာအုပ်"}</p><div class="progress"><span style="width:${Math.min(100, (p / (b.pageCount || p + 100)) * 100)}%"></span></div><p>နောက်ဆုံးဖတ်ထားတာ · Page ${p}</p><button class="primary small" data-book="${b.id}">ဖွင့်မယ် ${icon("chevron")}</button> ${b.chapters ? `<button class="small" data-chapters="${b.id}">အခန်းများ</button>` : ""}</div></article>`;
      })
      .join("")}</div>`;
  $("#import-pdf").onclick = (e) =>
    action(e.currentTarget, async () => {
      const b = await call(bridge.importPdf());
      if (b) {
        await refresh();
        await openBook(b.id);
      }
    });
  $("#continue").onclick = () =>
    openBook(all.find((x) => state.pages[x.id])?.id || all[0].id);
  $$("[data-book]").forEach(
    (b) => (b.onclick = () => openBook(b.dataset.book)),
  );
  $$("[data-chapters]").forEach(
    (b) => (b.onclick = () => chooseChapter(b.dataset.chapters)),
  );
}
function chooseChapter(id, practice = false) {
  const b = courses.find((x) => x.id === id);
  const d = modal(
    `<div class="eyebrow">CHOOSE YOUR LESSON</div><h2>${esc(b.title)}</h2><p class="muted small">အခန်းအားလုံးကို တိုက်ရိုက်ရွေးနိုင်သည်။ ကျော်သွားတာကို ပြီးပြီလို့ မသတ်မှတ်ပါ။</p><div class="chapter-list">${b.chapters.map((c) => `<button data-ch="${c.number}"><strong class="chapter-num">${String(c.number).padStart(2, "0")}</strong><div>${esc(c.title)}<span> · Page ${c.startPage}</span></div></button>`).join("")}</div>`,
  );
  $$("[data-ch]", d).forEach(
    (btn) =>
      (btn.onclick = () => {
        d.close();
        const c = b.chapters.find((x) => x.number === Number(btn.dataset.ch));
        if (practice) startBookPractice(b, c);
        else openBook(id, c.startPage);
      }),
  );
}
async function openBook(id, page) {
  sessionId = null;
  book = [...courses, ...state.books].find((b) => b.id === id);
  if (!book) return;
  pageNo = page || state.pages[id] || 1;
  readerTab = "meaning";
  selection = "";
  el.innerHTML = '<div class="loading">PDF စာအုပ်ဖွင့်နေပါသည်…</div>';
  try {
    const buffer = await call(bridge.pdf(id));
    const loading = pdfjs.getDocument({
      data: new Uint8Array(buffer),
      isEvalSupported: false,
      cMapUrl: new URL("../node_modules/pdfjs-dist/cmaps/", import.meta.url)
        .href,
      cMapPacked: true,
      standardFontDataUrl: new URL(
        "../node_modules/pdfjs-dist/standard_fonts/",
        import.meta.url,
      ).href,
      wasmUrl: new URL("../node_modules/pdfjs-dist/wasm/", import.meta.url)
        .href,
    });
    pdf = await loading.promise;
    pageNo = Math.min(Math.max(pageNo, 1), pdf.numPages);
    showReader();
    await renderPdf();
  } catch (e) {
    el.innerHTML = `<div class="error">${esc(e.message)}</div><button id="back-books">စာအုပ်စင်သို့</button>`;
    $("#back-books").onclick = () => navigate("books");
  }
}
function showReader() {
  el.innerHTML = `<div class="toolbar"><button id="reader-back" class="small">← စာအုပ်စင်</button><strong style="flex:1">${esc(book.title)}</strong>${book.chapters ? '<button id="reader-chapters" class="small">အခန်းများ</button>' : ""}</div><div class="toolbar"><button id="prev-page" class="small" aria-label="Previous page">←</button><label class="row small">Page <input id="page-input" type="number" min="1" max="${pdf.numPages}" value="${pageNo}"> / ${pdf.numPages}</label><button id="next-page" class="small" aria-label="Next page">→</button><button id="zoom-out" class="small" aria-label="Zoom out">−</button><span id="zoom-label" class="small">${Math.round(zoom * 100)}%</span><button id="zoom-in" class="small" aria-label="Zoom in">＋</button><select id="reader-theme" aria-label="Reader theme">${["paper", "sepia", "night"].map((t) => `<option value="${t}" ${state.preferences.readerTheme === t ? "selected" : ""}>${t === "paper" ? "Original" : t === "sepia" ? "Sepia" : "Night"}</option>`).join("")}</select><div style="flex:1"></div><button id="track-import" class="small">Track ဖိုင်များထည့်</button><button id="track-link" class="small">Track ချိတ်မယ်</button></div><div class="reader-layout"><div id="pdf-area" class="pdf-area ${state.preferences.readerTheme}"><div id="pdf-frame" class="pdf-frame"><canvas id="pdf-canvas"></canvas><div id="text-layer" class="textLayer"></div></div></div><div class="reader-side"><div class="card"><div class="row between"><div><div class="eyebrow no-margin">PAGE COMPANION</div><h3 class="no-margin">စာဖတ်ဖော်</h3></div><button id="reader-notes" class="small">မှတ်စုများ</button></div><div id="track-controls"></div><div class="notice">PDF သို့မဟုတ် Text ထဲက စာသားကို mouse နဲ့ ရွေးပါ။</div><div id="selection-tools"></div><div class="tabs">${[
    ["meaning", "အဓိပ္ပာယ်"],
    ["vocabulary", "ဝေါဟာရ"],
    ["grammar", "Grammar"],
    ["exercises", "လေ့ကျင့်ခန်း"],
    ["text", "Text"],
  ]
    .map(
      ([k, v]) =>
        `<button data-tab="${k}" class="${readerTab === k ? "active" : ""}">${v}</button>`,
    )
    .join("")}</div><div id="reader-panel"></div></div></div></div>`;
  $("#reader-back").onclick = () => navigate("books");
  if ($("#reader-chapters"))
    $("#reader-chapters").onclick = () => chooseChapter(book.id);
  $("#prev-page").onclick = () => changePage(pageNo - 1);
  $("#next-page").onclick = () => changePage(pageNo + 1);
  $("#page-input").onchange = (e) => changePage(Number(e.target.value));
  $("#zoom-in").onclick = () => changeZoom(0.2);
  $("#zoom-out").onclick = () => changeZoom(-0.2);
  $("#reader-theme").onchange = async (e) => {
    await save("preferences", { readerTheme: e.target.value });
    $("#pdf-area").className = "pdf-area " + e.target.value;
  };
  $("#reader-notes").onclick = () => showNotebook(book.id);
  $("#track-import").onclick = (e) =>
    action(e.currentTarget, () => importTracks());
  $("#track-link").onclick = () => linkTrack();
  $$("[data-tab]").forEach(
    (b) =>
      (b.onclick = () => {
        readerTab = b.dataset.tab;
        $$("[data-tab]").forEach((t) => t.classList.toggle("active", t === b));
        renderReaderPanel();
      }),
  );
  $("#pdf-area").onmouseup = captureSelection;
  $("#reader-panel").onmouseup = captureSelection;
  let startX;
  $("#pdf-area").addEventListener(
    "touchstart",
    (e) => {
      if (e.touches.length === 1) startX = e.touches[0].clientX;
    },
    { passive: true },
  );
  $("#pdf-area").addEventListener(
    "touchend",
    (e) => {
      if (startX != null && Math.abs(e.changedTouches[0].clientX - startX) > 90)
        changePage(pageNo + (e.changedTouches[0].clientX < startX ? 1 : -1));
      startX = null;
    },
    { passive: true },
  );
}
async function changeZoom(delta) {
  zoom = Math.max(0.7, Math.min(2.4, zoom + delta));
  $("#zoom-label").textContent = Math.round(zoom * 100) + "%";
  await renderPdf(false);
}
async function changePage(n) {
  if (!Number.isInteger(n) || n < 1 || n > pdf.numPages) return;
  pageNo = n;
  selection = "";
  $("#page-input").value = pageNo;
  await renderPdf();
}
async function renderPdf(persist = true) {
  const generation = ++renderGeneration;
  pdfRenderTask?.cancel();
  const p = await pdf.getPage(pageNo);
  if (generation !== renderGeneration) return;
  const area = $("#pdf-area");
  if (!area) return;
  const scale =
    ((area.clientWidth - 38) / p.getViewport({ scale: 1 }).width) * zoom;
  const viewport = p.getViewport({ scale });
  const canvas = $("#pdf-canvas"),
    frame = $("#pdf-frame"),
    layer = $("#text-layer");
  frame.style.width = viewport.width + "px";
  frame.style.height = viewport.height + "px";
  frame.style.setProperty("--total-scale-factor", scale);
  canvas.width = Math.ceil(viewport.width * devicePixelRatio);
  canvas.height = Math.ceil(viewport.height * devicePixelRatio);
  canvas.style.width = viewport.width + "px";
  canvas.style.height = viewport.height + "px";
  pdfRenderTask = p.render({
    canvasContext: canvas.getContext("2d"),
    viewport,
    transform:
      devicePixelRatio === 1
        ? null
        : [devicePixelRatio, 0, 0, devicePixelRatio, 0, 0],
  });
  try {
    await pdfRenderTask.promise;
  } catch (error) {
    if (error.name === "RenderingCancelledException") return;
    throw error;
  }
  if (generation !== renderGeneration) return;
  const tc = await p.getTextContent();
  pageText = tc.items
    .map((i) => i.str + (i.hasEOL ? "\n" : " "))
    .join("")
    .trim();
  pageImage = canvas.toDataURL("image/png").split(",")[1];
  layer.replaceChildren();
  const tl = new pdfjs.TextLayer({
    textContentSource: tc,
    container: layer,
    viewport,
  });
  await tl.render();
  if (generation !== renderGeneration) return;
  markPage();
  if (persist) await save("page", { bookId: book.id, page: pageNo });
  renderTrackControls();
  renderSelection();
  renderReaderPanel();
  $("#prev-page").disabled = pageNo === 1;
  $("#next-page").disabled = pageNo === pdf.numPages;
}
function markPage(quote) {
  const notes = state.annotations.filter(
    (a) => a.bookId === book.id && a.page === pageNo,
  );
  $$("#text-layer span").forEach((s) => {
    s.classList.toggle(
      "marked",
      !!s.textContent.trim() &&
        (quote
          ? quote.includes(s.textContent.trim())
          : notes.some((n) => n.quote.includes(s.textContent.trim()))),
    );
  });
}
function captureSelection() {
  const sel = getSelection();
  if (!sel || sel.isCollapsed) return;
  const q = sel.toString().trim();
  if (
    q &&
    q.length <= 4000 &&
    ($("#pdf-area")?.contains(sel.anchorNode) ||
      $("#reader-panel")?.contains(sel.anchorNode))
  ) {
    selection = q;
    renderSelection();
  }
}
function renderSelection() {
  const target = $("#selection-tools");
  if (!target) return;
  target.innerHTML = selection
    ? `<div class="selected-quote">${esc(selection)}</div><div class="row">${[
        ["highlight", "မျဉ်းသား"],
        ["underline", "Underline"],
        ["bold", "Bold"],
        ["note", "မှတ်စု"],
        ["save", "သိမ်း"],
      ]
        .map(
          ([k, v]) =>
            `<button class="small" data-annotate="${k}">${v}</button>`,
        )
        .join(
          "",
        )}<button class="small" id="ask-selection">ဒီစာကို မေးမယ်</button><button class="small" id="shadow-selection">${icon("mic")} လိုက်ပြောမယ်</button><button class="small" id="speak-selection">${icon("sound")} နားထောင်</button></div>`
    : "";
  $$("[data-annotate]").forEach(
    (b) => (b.onclick = () => annotate(b.dataset.annotate)),
  );
  if ($("#ask-selection")) $("#ask-selection").onclick = askSelection;
  if ($("#shadow-selection"))
    $("#shadow-selection").onclick = () => shadow(selection);
  if ($("#speak-selection"))
    $("#speak-selection").onclick = (e) =>
      action(e.currentTarget, () => speak(selection));
}
async function annotate(kind) {
  let note = "";
  if (kind === "note") {
    const d = modal(
      `<h2>ကိုယ်ပိုင်မှတ်စု</h2><div class="selected-quote">${esc(selection)}</div><textarea id="annotation-note" rows="4" placeholder="နားလည်ထားတာ / မှတ်သားစရာ"></textarea><div class="spacer"></div><button class="primary" id="save-annotation">သိမ်းမယ်</button>`,
    );
    $("#save-annotation", d).onclick = (e) =>
      action(e.currentTarget, async () => {
        await addAnnotation(kind, $("#annotation-note", d).value);
        d.close();
      });
  } else await addAnnotation(kind, note);
}
async function addAnnotation(kind, note) {
  const item = await save("annotation", {
    bookId: book.id,
    page: pageNo,
    quote: selection,
    note,
    kind,
  });
  state.annotations.push(item);
  markPage();
  if (readerTab === "text") renderReaderPanel();
  toast("မှတ်သားထားပြီးပြီ။");
}
const explanationKey = () => `${book.id}:${pageNo}:${readerTab}`;
function renderReaderPanel() {
  const panel = $("#reader-panel");
  if (!panel) return;
  if (readerTab === "text") {
    let html = esc(pageText);
    const ann = state.annotations.filter(
      (x) => x.bookId === book.id && x.page === pageNo,
    );
    for (const a of ann) {
      const q = esc(a.quote);
      html = html.replace(
        q,
        a.kind === "bold"
          ? `<strong>${q}</strong>`
          : a.kind === "underline"
            ? `<u>${q}</u>`
            : `<mark>${q}</mark>`,
      );
    }
    panel.innerHTML = `${pageText ? "" : '<div class="notice">ဒီစာမျက်နှာက စကင်ပုံဖြစ်ပြီး စာသားမထုတ်နိုင်ပါ။ PDF ပုံကို Gemini နဲ့ ရှင်းပြနိုင်သည်။</div>'}<div class="reader-text">${html}</div>`;
    return;
  }
  const value = state.explanations[explanationKey()];
  panel.innerHTML = `<div class="row between"><span class="muted small">${value ? "သိမ်းထားတဲ့ ရှင်းပြချက်" : "Gemini / NVIDIA · ကိုယ့် key သုံးမည်"}</span><button id="explain-page" class="primary small">${value ? "ပြန်ရှင်းပြမယ်" : "ဒီအပိုင်းကို ရှင်းပြပါ"}</button></div><div id="operation-error" class="error" hidden></div><div id="explanation-output">${value ? explanationHtml(value, readerTab) : `<div class="empty">${readerTab === "exercises" ? "ဒီစာမျက်နှာအပေါ် အခြေခံတဲ့ လေ့ကျင့်ခန်းအသစ်များ" : "မူရင်းစာကို မြန်မာလို နားလည်အောင် ရှင်းပြမယ်။"}<br><small>အပိုင်းတစ်ခုချင်းစီ သိမ်းထားပေးသည်။</small></div>`}</div>`;
  $("#explain-page").onclick = (e) =>
    action(e.currentTarget, async () => {
      const btn = e.currentTarget;
      btn.textContent = "ရှင်းပြနေပါသည်…";
      const key = explanationKey(),
        source = pageText,
        tab = readerTab,
        id = book.id;
      try {
        const value = await call(
          bridge.api("section", {
            section: tab,
            source:
              source ||
              "This is a scanned page. Describe only clearly legible Korean text in the attached image. Quotes must be left empty if source text is not supplied.",
            image: settings.provider === "nvidia" ? undefined : pageImage,
          }),
        );
        await save("explanation", { key, value });
        if (book?.id === id && explanationKey() === key) {
          renderReaderPanel();
        }
      } finally {
        if (btn.isConnected) btn.textContent = "ဒီအပိုင်းကို ရှင်းပြပါ";
      }
    });
  bindExplanation(panel);
}
function explanationHtml(v, tab) {
  return (
    (v.summary ? `<p style="margin-top:18px">${esc(v.summary)}</p>` : "") +
    (v.sentences || v.items || [])
      .map(
        (i, index) =>
          `<div class="explain-item">${i.quote ? `<button class="source-quote" data-source="${esc(i.quote)}">${esc(i.quote)}</button>` : ""}${i.pattern ? `<p class="korean">${esc(i.pattern)}</p>` : ""}<p>${esc(i.meaning || i.explanation || i.question || "")}</p>${i.example ? `<p class="korean">${esc(i.example)}</p>` : ""}${i.question ? `<strong class="korean">${esc(i.question)}</strong><details><summary>အဖြေကြည့်မယ်</summary><p>${esc(i.answer)}</p><p>${esc(i.explanation)}</p></details>` : ""}${i.quote ? `<button class="small ghost" data-save-item="${index}">＋ ကိုယ်ပိုင်မှတ်စုထဲသိမ်း</button>` : ""}</div>`,
      )
      .join("") +
    (tab === "exercises"
      ? '<p class="muted small">AI က မူရင်းစာအပေါ်အခြေခံပြီး ထပ်ထည့်ထားသော လေ့ကျင့်ခန်းများဖြစ်သည်။</p>'
      : "")
  );
}
function bindExplanation(panel) {
  $$("[data-source]", panel).forEach(
    (b) =>
      (b.onclick = () => {
        selection = b.dataset.source;
        markPage(selection);
        renderSelection();
      }),
  );
  $$("[data-save-item]", panel).forEach(
    (b) =>
      (b.onclick = (e) =>
        action(e.currentTarget, async () => {
          const v = state.explanations[explanationKey()];
          const i = (v.sentences || v.items)[Number(b.dataset.saveItem)];
          const n = await save("notebook", {
            bookId: book.id,
            page: pageNo,
            quote: i.quote,
            note: i.meaning || i.explanation || "",
            kind: readerTab,
          });
          state.notebook.push(n);
          toast("ကိုယ်ပိုင်မှတ်စုထဲ သိမ်းပြီးပြီ။");
        })),
  );
}
function askSelection() {
  const d = modal(
    `<div class="eyebrow">ASK ABOUT THIS PASSAGE</div><h2>ဒီစာကြောင်းကို မေးမယ်</h2><div class="selected-quote">${esc(selection)}</div><form id="ask-form"><label>မေးခွန်း<textarea id="ask-text" rows="3" placeholder="ဒီ Grammar ကို နမူနာနဲ့ရှင်းပြပါ" required></textarea></label><button class="primary">မေးမယ်</button></form><div id="ask-result"></div>`,
  );
  const source = selection;
  $("#ask-form", d).onsubmit = (e) => {
    e.preventDefault();
    action($("button.primary", d), async () => {
      const r = await call(
        bridge.api("ask", { source, prompt: $("#ask-text", d).value }),
      );
      $("#ask-result", d).innerHTML =
        `<div class="spacer"></div><div class="korean">${esc(r.reply)}</div><p>${esc(r.meaning)}</p><p>${esc(r.explanation)}</p>`;
    });
  };
}
async function importTracks(number) {
  const result = await call(bridge.tracks(book.id, pageNo, number));
  state.tracks = result.tracks;
  renderTrackControls();
  toast(
    result.skipped
      ? `${result.skipped} ဖိုင်က နံပါတ်မရှင်းလို့ ကျော်ထားသည်။ တစ်ဖိုင်ချင်း ချိတ်နိုင်ပါသည်။`
      : "Track ဖိုင်များချိတ်ပြီးပြီ။",
  );
}
function linkTrack() {
  const d = modal(
    '<h2>စာမျက်နှာနဲ့ Track ချိတ်မယ်</h2><form id="track-form"><label>မူရင်းစာအုပ်ပေါ်က Track နံပါတ်<input id="track-number" type="number" min="1" max="9999" value="1" required></label><button class="primary">အသံဖိုင်ရွေးပြီး ချိတ်မယ်</button></form>',
  );
  $("#track-form", d).onsubmit = (e) => {
    e.preventDefault();
    action($("button.primary", d), async () => {
      await importTracks(Number($("#track-number", d).value));
      d.close();
    });
  };
}
function renderTrackControls() {
  const el = $("#track-controls");
  if (!el) return;
  const found = [
    ...pageText.matchAll(/(?:track|트랙|트렉)\s*0*(\d{1,4})/gi),
  ].map((m) => Number(m[1]));
  const numbers = [
    ...new Set([
      ...found,
      ...state.tracks
        .filter((x) => x.bookId === book.id && x.pages?.includes(pageNo))
        .map((x) => x.number),
    ]),
  ];
  el.innerHTML = numbers.length
    ? `<div class="row" style="margin-top:15px">${numbers.map((n) => `<button data-track="${n}" class="small">${icon("play")} Track ${String(n).padStart(2, "0")}</button>`).join("")}</div><div id="track-player"></div>`
    : "";
  $$("[data-track]").forEach(
    (btn) =>
      (btn.onclick = (e) =>
        action(e.currentTarget, async () => {
          const number = Number(btn.dataset.track);
          let track = state.tracks.find(
            (t) => t.bookId === book.id && t.number === number,
          );
          if (!track) {
            await importTracks(number);
            track = state.tracks.find(
              (t) => t.bookId === book.id && t.number === number,
            );
          }
          if (track) {
            const b = await call(bridge.media(track.file));
            const url = URL.createObjectURL(new Blob([b]));
            blobUrls.push(url);
            $("#track-player").innerHTML =
              `<p class="muted small">မူရင်း Track ${number} · ${esc(track.title)}</p><audio controls autoplay id="track-audio"></audio>`;
            $("#track-audio").src = url;
          }
        })),
  );
}
function showNotebook(filter) {
  const entries = [...state.annotations, ...state.notebook]
    .filter((x) => !filter || x.bookId === filter)
    .sort((a, b) => b.createdAt - a.createdAt);
  const d = modal(
    `<div class="eyebrow">YOUR SOURCE-LINKED NOTEBOOK</div><h2>ကိုယ်ပိုင်မှတ်စုစာအုပ်</h2><input id="note-search" class="w-full" placeholder="စကားလုံး / မှတ်စုရှာမယ်"><div class="notebook-list" id="note-items"></div>`,
  );
  const render = () => {
    $("#note-items", d).innerHTML =
      entries
        .filter((x) =>
          (x.quote + " " + x.note)
            .toLowerCase()
            .includes($("#note-search", d).value.toLowerCase()),
        )
        .map(
          (n) =>
            `<div class="note"><strong>${esc(n.quote)}</strong><p>${esc(n.note)}</p><div class="row between"><span class="source-location">${esc(n.kind)} · Page ${n.page || "—"}</span><button class="small" data-note-source="${n.id}">မူရင်းစာ</button><button class="small ghost danger" data-delete-note="${n.id}">ဖျက်မယ်</button></div></div>`,
        )
        .join("") || '<div class="empty">သိမ်းထားတာ မရှိသေးပါ။</div>';
    $$("[data-note-source]", d).forEach(
      (b) =>
        (b.onclick = () => {
          const n = entries.find((x) => x.id === b.dataset.noteSource);
          d.close();
          openBook(n.bookId, n.page);
        }),
    );
    $$("[data-delete-note]", d).forEach(
      (b) =>
        (b.onclick = (e) =>
          action(e.currentTarget, async () => {
            const id = b.dataset.deleteNote;
            await save(
              state.annotations.some((x) => x.id === id)
                ? "delete-annotation"
                : "delete-note",
              id,
            );
            d.close();
            showNotebook(filter);
          })),
    );
  };
  $("#note-search", d).oninput = render;
  render();
}
function showPractice() {
  el.innerHTML =
    title(
      "LISTEN · REPEAT · CONNECT",
      "စကားပြောဖို့ နည်းနည်းစီလေ့ကျင့်ပါ",
      "စာသားနဲ့ အရင်လေ့ကျင့်နိုင်ပြီး၊ Gemini key ထည့်ရင် အသံနဲ့ ပြောနိုင်ပါသည်။",
    ) +
    `<div class="tabs">${[
      ["daily", "ဒီနေ့ နည်းနည်းစီ"],
      ["book", "စာအုပ်သင်ခန်းစာ"],
      ["chat", "စိတ်ကြိုက်စကားပြော"],
      ["live", "Live စကားပြော"],
      ["review", "ပြန်လေ့ကျင့်"],
    ]
      .map(
        ([k, v]) =>
          `<button data-practice="${k}" class="${practiceMode === k ? "active" : ""}">${v}</button>`,
      )
      .join("")}</div><div id="practice-content"></div>`;
  $$("[data-practice]").forEach(
    (b) =>
      (b.onclick = () => {
        if (recorder || liveActive)
          return toast("အသံဖမ်းခြင်း / Live ကိုအရင်ရပ်ပါ။");
        practiceMode = b.dataset.practice;
        history = [];
        sessionId = null;
        showPractice();
      }),
  );
  if (practiceMode === "book") bookPracticePicker();
  else if (practiceMode === "live") liveScreen();
  else if (practiceMode === "review") reviewPractice();
  else if (practiceMode === "chat") chatPractice();
  else dailyPractice();
}
function dailyPractice() {
  const u = curriculum[unitIndex];
  const saved = state.practice[u.id] || {};
  $("#practice-content").innerHTML =
    `<div class="practice-grid"><section class="card practice-stage"><div class="row between"><span class="tag">DAILY PRACTICE · ${unitIndex + 1} / ${curriculum.length}</span><select id="unit-select" aria-label="Choose daily lesson">${curriculum.map((c, i) => `<option value="${i}" ${i === unitIndex ? "selected" : ""}>${i + 1}. ${esc(c.title)}</option>`).join("")}</select></div><div class="eyebrow" style="margin-top:25px">${esc(u.topic)} · ${saved.completed ? "ပြီးခဲ့သော သင်ခန်းစာ" : "၅–၁၀ မိနစ်"}</div><h2>${esc(u.title)}</h2><div class="korean">${esc(u.question)}</div><p class="muted">${esc(u.goal)}</p><div class="answer-frame"><small>အဖြေပုံစံ</small><div style="font-size:21px">${esc(u.answerPattern || u.pattern)}</div><details><summary class="small">အဖြေနမူနာကြည့်မယ်</summary><div>${esc(u.example)}</div><div class="small">${esc(u.meaning)}</div></details></div><div id="practice-interaction"></div><div class="row between"><button id="previous-unit" class="small" ${unitIndex === 0 ? "disabled" : ""}>← အရင်သင်ခန်းစာ</button><button id="next-unit" class="primary">Next · ဆက်မယ် →</button></div></section><aside><section class="card"><div class="eyebrow">UNDERSTAND THE PATTERN</div><h3>${esc(u.pattern)}</h3><p>${esc(u.explanation)}</p>${u.words.map((w) => `<div class="explain-item"><strong class="korean">${esc(w.korean)}</strong><div class="small muted">${esc(w.myanmar)}</div></div>`).join("")}</section><div class="notice">အဖြေစစ်ပြီးလည်း ဒီမှာပဲ ဆက်နေမယ်။ ကိုယ်တိုင် Next နှိပ်မှ ဆက်မည်။</div><div id="operation-error" class="error" hidden></div></aside></div>`;
  $("#unit-select").onchange = (e) => {
    if (recorder) return toast("Mic ကိုအရင်ရပ်ပါ။");
    unitIndex = Number(e.target.value);
    history = [];
    sessionId = null;
    dailyPractice();
  };
  $("#previous-unit").onclick = () => {
    if (recorder) return;
    unitIndex--;
    history = [];
    sessionId = null;
    dailyPractice();
  };
  $("#next-unit").onclick = (e) =>
    action(e.currentTarget, async () => {
      if (recorder) return toast("Mic ကိုအရင်ရပ်ပါ။");
      await save("practice", {
        id: u.id,
        value: {
          ...state.practice[u.id],
          completed: true,
          completedAt: Date.now(),
        },
      });
      if (unitIndex < curriculum.length - 1) {
        unitIndex++;
        history = [];
        sessionId = null;
        dailyPractice();
      } else
        toast("သင်ခန်းစာအားလုံး ဖတ်လေ့ကျင့်ပြီးပြီ။ ပြန်လေ့ကျင့်နိုင်ပါသည်။");
    });
  interaction(
    u.title,
    `${u.goal}\nQuestion: ${u.question}\nPattern: ${u.answerPattern}\nAssessment criterion: ${u.criterion}`,
  );
}
function bookPracticePicker() {
  $("#practice-content").innerHTML =
    `<div class="grid">${courses.map((b, i) => `<section class="card book-card"><div class="cover ${i ? "coral" : ""}"><span>LANGUAGE TALK</span><strong>Real-Life<br>Korean</strong><span>${b.level}</span></div><div class="details"><span class="tag">${b.chapters.length} CHAPTERS</span><h3>${esc(b.title)}</h3><p class="muted">မူရင်းဝါကျနဲ့ နားထောင်၊ လိုက်ပြော၊ ပြန်ယှဉ်နားထောင်ပါ။</p><button class="primary" data-book-practice="${b.id}">အခန်းရွေးမယ်</button></div></section>`).join("")}</div><div id="book-lesson"></div>`;
  $$("[data-book-practice]").forEach(
    (b) => (b.onclick = () => chooseChapter(b.dataset.bookPractice, true)),
  );
}
function startBookPractice(b, c) {
  lessonTurns = c.sections.flatMap((s) => s.turns || []);
  if (!lessonTurns.length)
    return toast("ဒီအခန်းမှာ စကားပြောဝါကျမရှိပါ။ စာအုပ်ကနေ ဖတ်နိုင်ပါသည်။");
  lessonSource = {
    bookId: b.id,
    page: c.startPage,
    title: c.title,
    chapter: c.number,
  };
  turnIndex = Math.min(
    state.practice[b.id + "-" + c.number]?.turn || 0,
    lessonTurns.length - 1,
  );
  sessionId = null;
  history = [];
  bookPracticeLesson();
}
function bookPracticeLesson() {
  const t = lessonTurns[turnIndex];
  $("#practice-content").innerHTML =
    `<div class="practice-grid"><section class="card practice-stage"><div class="row between"><button id="book-lessons-back" class="small">← အခန်းရွေးမယ်</button><span class="tag">${turnIndex + 1} / ${lessonTurns.length} TURNS</span></div><div class="eyebrow" style="margin-top:24px">${esc(lessonSource.title)} · ${esc(t.speaker)}</div><div class="korean">${esc(t.text)}</div><p class="muted">${esc(t.english)}</p><div class="row" style="justify-content:center"><button id="reference-voice">${icon("sound")} မူရင်းဝါကျ နားထောင်</button><button id="open-book-source" class="small">Page ${t.page}</button></div><div id="practice-interaction"></div><div class="row between"><button id="previous-turn" ${turnIndex === 0 ? "disabled" : ""}>← အရင်ဝါကျ</button><button id="next-turn" class="primary">Next →</button></div></section><aside class="card"><div class="eyebrow">LISTEN · REPEAT · COMPARE</div><h2>ဝါကျတစ်ကြောင်းချင်း လေ့ကျင့်ပါ</h2><p>အသံနမူနာကို နားထောင်ပြီး ကိုယ့်အသံနဲ့ ပြန်ပြောပါ။ Replays မှာ နှစ်ခုယှဉ်ပြီး နားထောင်နိုင်သည်။</p><div class="notice">AI အသံဖြစ်သည်။ စာအုပ်ရဲ့ မူရင်း Track အသံက Books မှာ သီးခြားချိတ်နိုင်သည်။</div><div id="operation-error" class="error" hidden></div></aside></div>`;
  $("#book-lessons-back").onclick = () => {
    if (!recorder) bookPracticePicker();
  };
  $("#reference-voice").onclick = (e) =>
    action(e.currentTarget, () => speak(t.text, lessonSource.title));
  $("#open-book-source").onclick = () => {
    if (!recorder) {
      view = "books";
      openBook(lessonSource.bookId, t.page);
    }
  };
  $("#previous-turn").onclick = () => {
    if (!recorder) {
      turnIndex--;
      bookPracticeLesson();
    }
  };
  $("#next-turn").onclick = (e) =>
    action(e.currentTarget, async () => {
      if (recorder) return;
      const id = lessonSource.bookId + "-" + lessonSource.chapter;
      if (turnIndex < lessonTurns.length - 1) {
        turnIndex++;
        await save("practice", {
          id,
          value: { turn: turnIndex, completed: false },
        });
        history = [];
        bookPracticeLesson();
      } else {
        await save("practice", {
          id,
          value: { turn: turnIndex, completed: true },
        });
        toast("ဒီအခန်း လေ့ကျင့်ပြီးပြီ။");
        bookPracticePicker();
      }
    });
  interaction(
    lessonSource.title,
    `Repeat this exact Korean sentence: ${t.text}. Explain its grammar in Myanmar. Assess the learner response but never move to next sentence automatically.`,
    lessonSource,
  );
}
function chatPractice() {
  $("#practice-content").innerHTML =
    `<div class="practice-grid"><section class="card"><div class="eyebrow">OPEN CONVERSATION</div><h2>ကိုယ်ပြောချင်တဲ့အကြောင်း ပြောကြည့်ပါ</h2><label>စကားပြောအကြောင်းအရာ<select id="chat-topic">${["Workplace greetings · စက်ရုံမှာ နှုတ်ဆက်ခြင်း", "Asking for help · အကူအညီတောင်းခြင်း", "Ordering food · အစားအသောက်မှာခြင်း", "Shopping · ဈေးဝယ်ခြင်း", "Free conversation · စိတ်ကြိုက်စကားပြော"].map((t) => `<option>${t}</option>`).join("")}</select></label><div id="practice-interaction"></div></section><aside class="card"><div class="eyebrow">ONE QUESTION AT A TIME</div><h3>ကိုရီးယားလို ဖြည်းဖြည်းပြောပါ</h3><p>မသိတဲ့နေရာမှာ မြန်မာလို မေးနိုင်သည်။ AI ရဲ့အသံက ကိုရီးယားလိုသာဖြစ်ပြီး ရှင်းပြချက်က မြန်မာလိုဖြစ်သည်။</p><div class="notice">Mic နှိပ်ပြီး ပြော၊ ထပ်နှိပ်ရင် ရပ်ပြီး အဖြေစစ်မည်။ ကိုယ့်အသံကို API မအောင်မြင်လည်း သိမ်းထားပေးသည်။</div><div id="operation-error" class="error" hidden></div></aside></div>`;
  interaction("Open conversation", () => $("#chat-topic").value);
}
async function ensureSession(title = "Practice", source = null) {
  if (!sessionId) {
    const s = await call(bridge.session(title, source));
    sessionId = s.id;
  }
  return sessionId;
}
function interaction(title, context, source = null) {
  const container = $("#practice-interaction");
  container.innerHTML = `<div id="chat-thread" class="chat-thread">${history.map(bubbleHtml).join("")}</div><form id="practice-form"><div class="row" style="align-items:stretch"><input id="learner-input" style="flex:1" placeholder="ကိုရီးယား / မြန်မာလို ရေးနိုင်သည်" required maxlength="10000"><button class="primary" id="send-practice">ပို့မယ်</button></div></form><button class="mic-button" id="record-mic" aria-label="Record answer">${icon("mic")}</button><div class="mic-label" id="record-label">Mic နှိပ်ပြီး ပြောပါ · တစ်ကြိမ် ၉၀ စက္ကန့်</div><p class="muted small">${state.preferences.record ? "အသံမှတ်တမ်း ဖွင့်ထားသည် · Replays မှာ ပြန်နားထောင်နိုင်သည်" : "အသံမှတ်တမ်း ပိတ်ထားသည် · Me မှာ ဖွင့်နိုင်သည်"}</p>`;
  $("#practice-form").onsubmit = (e) => {
    e.preventDefault();
    const prompt = $("#learner-input").value;
    $("#learner-input").value = "";
    action($("#send-practice"), () => sendPractice(prompt, null));
  };
  $("#record-mic").onclick = (e) => {
    if (recorder) {
      recorder.stop();
      return;
    }
    action(e.currentTarget, async () => {
      if (!settings.geminiKeySaved)
        throw Error("အသံနားလည်ဖို့ Gemini API key လိုပါသည်။ Me မှာထည့်ပါ။");
      await startRecording(async (blob) => {
        await action($("#record-mic"), async () => {
          const raw = new Uint8Array(await blob.arrayBuffer());
          let sid, savedClip;
          if (state.preferences.record) {
            sid = await ensureSession(title, source);
            savedClip = await call(
              bridge.clip(sid, {
                speaker: "user",
                text: "အသံဖမ်းထားသည်",
                audio: raw,
                ext: "webm",
              }),
            );
          }
          const audio = {
            data: toBase64(raw),
            mime: blob.type.split(";")[0] || "audio/webm",
          };
          const heard = await call(bridge.api("transcribe", { audio }));
          if (savedClip)
            await call(
              bridge.save("clip-text", {
                sessionId: sid,
                clipId: savedClip.id,
                text: heard,
              }),
            );
          await sendPractice(heard, null, true);
        });
      });
    });
  };
  async function sendPractice(prompt, audio, alreadySaved = false) {
    const ctx = typeof context === "function" ? context() : context;
    history.push({ role: "user", text: prompt });
    renderThread();
    if (state.preferences.record && !alreadySaved)
      await call(
        bridge.clip(await ensureSession(title, source), {
          speaker: "user",
          text: prompt,
        }),
      );
    const result = await call(
      bridge.api("tutor", {
        prompt,
        context: ctx,
        history: history
          .slice(-12, -1)
          .map((h) => `${h.role}: ${h.text}`)
          .join("\n"),
        audio,
      }),
    );
    history.push({
      role: "ai",
      text: result.reply,
      meaning: result.meaning,
      explanation: result.explanation,
      correction: result.correction,
    });
    renderThread();
    if (
      state.preferences.record &&
      !(state.preferences.autoSpeak && settings.geminiKeySaved)
    )
      await call(
        bridge.clip(await ensureSession(title, source), {
          speaker: "ai",
          text: result.reply,
        }),
      );
    if (state.preferences.autoSpeak && settings.geminiKeySaved)
      await speak(result.reply, title, source);
  }
}
function bubbleHtml(h) {
  return `<div class="bubble ${h.role === "user" ? "user" : ""}"><div class="korean">${esc(h.text)}</div>${h.meaning ? `<div class="meaning">${esc(h.meaning)}</div>` : ""}${h.explanation ? `<details class="small"><summary>ရှင်းပြချက်</summary>${esc(h.explanation)}${h.correction ? `<p>${esc(h.correction)}</p>` : ""}</details>` : ""}</div>`;
}
function renderThread() {
  const t = $("#chat-thread");
  if (t) {
    t.innerHTML = history.map(bubbleHtml).join("");
    t.scrollTop = t.scrollHeight;
  }
}
function toBase64(u8) {
  let binary = "";
  for (let i = 0; i < u8.length; i += 16384)
    binary += String.fromCharCode(...u8.subarray(i, i + 16384));
  return btoa(binary);
}
function fromBase64(s) {
  return Uint8Array.from(atob(s), (c) => c.charCodeAt(0));
}
async function startRecording(onComplete) {
  if (recorder) return;
  micStream = await navigator.mediaDevices.getUserMedia({
    audio: { echoCancellation: true, noiseSuppression: true },
    video: false,
  });
  const chunks = [];
  const mime = MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
    ? "audio/webm;codecs=opus"
    : "audio/webm";
  const r = new MediaRecorder(micStream, { mimeType: mime });
  recorder = r;
  $("#record-mic")?.classList.add("recording");
  if ($("#record-label"))
    $("#record-label").textContent = "အသံဖမ်းနေသည်… ထပ်နှိပ်ပြီး ရပ်ပါ။";
  r.ondataavailable = (e) => {
    if (e.data.size) chunks.push(e.data);
  };
  r.onstop = async () => {
    clearTimeout(r.limit);
    micStream.getTracks().forEach((t) => t.stop());
    micStream = null;
    recorder = null;
    $("#record-mic")?.classList.remove("recording");
    if ($("#record-label"))
      $("#record-label").textContent =
        "အသံဖမ်းပြီးပြီ · ပြန်နှိပ်ပြီး ထပ်ပြောနိုင်သည်";
    await onComplete(new Blob(chunks, { type: mime }));
  };
  r.start(1000);
  r.limit = setTimeout(() => {
    if (r.state === "recording") r.stop();
  }, 90000);
}
async function speak(text, title = "Reading practice", source = null) {
  const audio = await call(bridge.api("speech", { text }));
  const raw = fromBase64(audio.data);
  if (state.preferences.record)
    await call(
      bridge.clip(await ensureSession(title, source), {
        speaker: "ai",
        text,
        audio: raw,
        ext: "wav",
      }),
    );
  const url = URL.createObjectURL(new Blob([raw], { type: audio.mime }));
  blobUrls.push(url);
  const a = new Audio(url);
  await a.play();
  a.onended = () => {
    URL.revokeObjectURL(url);
  };
  return a;
}
function shadow(quote) {
  const d = modal(
    `<div class="eyebrow">LISTEN · REPEAT · COMPARE</div><h2>နားထောင်ပြီး လိုက်ပြောမယ်</h2><div class="selected-quote">${esc(quote)}</div><div class="row"><button id="shadow-reference" class="primary">${icon("sound")} AI နမူနာ နားထောင်</button><button id="shadow-record">${icon("mic")} ကိုယ့်အသံ ဖမ်းမယ်</button></div><p class="muted small" id="shadow-status">နမူနာက ကိုရီးယား AI အသံဖြစ်သည်။</p><div id="shadow-playback"></div><div class="notice">Replays မှာ နှစ်ခုယှဉ်နားထောင်နိုင်သည်။</div>`,
  );
  const source = { bookId: book.id, page: pageNo };
  let sid;
  const getSid = async () =>
    sid ||
    (sid = (
      await call(bridge.session("Shadowing · " + quote.slice(0, 50), source))
    ).id);
  $("#shadow-reference", d).onclick = (e) =>
    action(e.currentTarget, async () => {
      const audio = await call(bridge.api("speech", { text: quote }));
      const raw = fromBase64(audio.data);
      if (state.preferences.record)
        await call(
          bridge.clip(await getSid(), {
            speaker: "ai",
            text: quote,
            audio: raw,
            ext: "wav",
          }),
        );
      const url = URL.createObjectURL(new Blob([raw], { type: audio.mime }));
      blobUrls.push(url);
      const a = new Audio(url);
      await a.play();
    });
  let r, stream;
  const close = () => {
    if (r?.state === "recording") r.stop();
    stream?.getTracks().forEach((t) => t.stop());
  };
  d.addEventListener("close", close, { once: true });
  $("#shadow-record", d).onclick = (e) =>
    action(e.currentTarget, async () => {
      if (r?.state === "recording") {
        r.stop();
        return;
      }
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const chunks = [];
      r = new MediaRecorder(stream, { mimeType: "audio/webm" });
      r.ondataavailable = (e) => {
        if (e.data.size) chunks.push(e.data);
      };
      r.onstop = () =>
        action(null, async () => {
          clearTimeout(r.limit);
          stream.getTracks().forEach((t) => t.stop());
          const blob = new Blob(chunks, { type: "audio/webm" });
          const raw = new Uint8Array(await blob.arrayBuffer());
          if (state.preferences.record)
            await call(
              bridge.clip(await getSid(), {
                speaker: "user",
                text: quote,
                audio: raw,
                ext: "webm",
              }),
            );
          const url = URL.createObjectURL(blob);
          blobUrls.push(url);
          $("#shadow-playback", d).innerHTML =
            '<div class="spacer"></div><audio controls></audio>';
          $("audio", d).src = url;
          $("#shadow-status", d).textContent =
            "ကိုယ့်အသံ သိမ်းပြီးပြီ။ နားထောင်ပြီး ယှဉ်ကြည့်ပါ။";
          $("#shadow-record", d).textContent = "ကိုယ့်အသံ ပြန်ဖမ်းမယ်";
        });
      r.start();
      r.limit = setTimeout(() => {
        if (r.state === "recording") r.stop();
      }, 90000);
      $("#shadow-record", d).textContent = "■ ရပ်မယ်";
      $("#shadow-status", d).textContent = "အသံဖမ်းနေပါသည်…";
    });
}
function reviewPractice() {
  const notes = [
    ...state.notebook,
    ...state.annotations.filter((x) => x.kind === "save"),
  ];
  const n = notes[unitIndex % Math.max(1, notes.length)];
  $("#practice-content").innerHTML = n
    ? `<section class="card practice-stage"><div class="eyebrow">YOUR SAVED WORDS · ${(unitIndex % notes.length) + 1} / ${notes.length}</div><div class="korean">${esc(n.quote)}</div><details><summary>မှတ်စု ပြန်ကြည့်မယ်</summary><p>${esc(n.note)}</p></details><div class="row" style="justify-content:center;margin-top:25px"><button id="review-listen">${icon("sound")} နားထောင်</button><button id="review-source">မူရင်းစာ</button><button id="review-next" class="primary">Next →</button></div></section>`
    : '<div class="empty">စာအုပ်ထဲက ဝေါဟာရ၊ Grammar တွေကို အရင်သိမ်းပါ။ ဒီနေရာမှာ ပြန်လေ့ကျင့်နိုင်မည်။</div>';
  if (n) {
    $("#review-next").onclick = () => {
      unitIndex++;
      reviewPractice();
    };
    $("#review-listen").onclick = (e) =>
      action(e.currentTarget, () => speak(n.quote));
    $("#review-source").onclick = () => openBook(n.bookId, n.page);
  }
}
function liveScreen() {
  $("#practice-content").innerHTML =
    `<div class="practice-grid"><section class="card practice-stage"><div class="eyebrow">GEMINI LIVE · KOREAN VOICE</div><h2>အပြန်အလှန် စကားပြောမယ်</h2><label style="text-align:left">စကားပြောအကြောင်းအရာ<input id="live-topic" value="Factory greetings and asking colleagues for help" maxlength="1000"></label><button class="mic-button" id="live-toggle" aria-label="Start live conversation">${icon("mic")}</button><p id="live-status" class="muted">Mic ဖွင့်ပြီး စကားဝိုင်းစမယ်။</p><div class="notice">အသံက ကိုရီးယားလိုသာ ပြောမည်။ တစ်ကြိမ် ၂၀ မိနစ်အထိ လေ့ကျင့်နိုင်သည်။</div><div id="live-transcript" class="chat-thread"></div></section><aside class="card"><div class="eyebrow">SPEAK AT YOUR PACE</div><h3>တိုတိုနဲ့ ဖြည်းဖြည်းပြောပါ</h3><p>AI က မေးခွန်းတစ်ခုချင်း မေးပြီး မင်းအဖြေကို စောင့်ပေးမယ်။ မသိရင် <strong>천천히 말해 주세요.</strong> လို့ ပြောနိုင်သည်။</p><p>အသံမှတ်တမ်းဖွင့်ထားရင် ကိုယ့်အသံနဲ့ AI အသံကို Replays မှာ ပြန်နားထောင်နိုင်သည်။</p><div id="operation-error" class="error" hidden></div></aside></div>`;
  $("#live-toggle").onclick = (e) =>
    action(e.currentTarget, async () => {
      if (liveActive) {
        await endLive();
        return;
      }
      await beginLive();
    });
}
async function beginLive() {
  if (!settings.geminiKeySaved)
    throw Error("Gemini API key ကို Me မှာ အရင်ထည့်ပါ။");
  const stream = await navigator.mediaDevices.getUserMedia({
    audio: {
      echoCancellation: true,
      noiseSuppression: true,
      autoGainControl: true,
    },
  });
  micStream = stream;
  liveActive = true;
  liveReady = false;
  userPcm = [];
  $("#live-status").textContent = "Live ချိတ်ဆက်နေပါသည်…";
  $("#live-toggle").classList.add("recording");
  $("#live-toggle").innerHTML = icon("stop");
  liveAudioCtx = new AudioContext({ sampleRate: 16000 });
  await liveAudioCtx.audioWorklet.addModule(
    new URL("./mic-worklet.js", import.meta.url),
  );
  liveNode = new AudioWorkletNode(liveAudioCtx, "pcm-capture");
  liveSource = liveAudioCtx.createMediaStreamSource(stream);
  liveSource.connect(liveNode);
  const gain = liveAudioCtx.createGain();
  gain.gain.value = 0;
  liveNode.connect(gain).connect(liveAudioCtx.destination);
  liveNextTime = 0;
  liveSources = [];
  liveNode.port.onmessage = (e) => {
    if (!liveReady) return;
    const pcm = new Int16Array(e.data);
    if (state.preferences.record) {
      userPcm.push(pcm);
      if (userPcm.length > 900) userPcm.shift();
    }
    bridge.liveAudio(toBase64(new Uint8Array(pcm.buffer)));
  };
  liveUnsubscribe = bridge.onLive(async (event) => {
    if (!liveActive) return;
    const status = $("#live-status");
    if (event.type === "ready") {
      liveReady = true;
      sessionId = event.session.id;
      status.textContent = "ချိတ်ဆက်ပြီးပြီ · ကိုရီးယားလို ပြောနိုင်ပါပြီ";
    }
    if (event.type === "audio") {
      const pcm = new Int16Array(fromBase64(event.data).buffer);
      const buf = liveAudioCtx.createBuffer(1, pcm.length, 24000);
      const channel = buf.getChannelData(0);
      for (let i = 0; i < pcm.length; i++) channel[i] = pcm[i] / 32768;
      const source = liveAudioCtx.createBufferSource();
      source.buffer = buf;
      source.connect(liveAudioCtx.destination);
      const when = Math.max(liveAudioCtx.currentTime + 0.03, liveNextTime);
      source.start(when);
      liveNextTime = when + buf.duration;
      liveSources.push(source);
      source.onended = () => {
        liveSources = liveSources.filter((s) => s !== source);
      };
    }
    if (event.type === "interrupted") {
      liveSources.forEach((s) => {
        try {
          s.stop();
        } catch {}
      });
      liveSources = [];
      liveNextTime = 0;
    }
    if (event.type === "input" || event.type === "output")
      status.textContent = event.text || "စကားပြောနေပါသည်…";
    if (event.type === "turn") {
      const t = $("#live-transcript");
      if (event.input)
        t.insertAdjacentHTML(
          "beforeend",
          bubbleHtml({ role: "user", text: event.input }),
        );
      if (event.output)
        t.insertAdjacentHTML(
          "beforeend",
          bubbleHtml({ role: "ai", text: event.output }),
        );
      t.scrollTop = t.scrollHeight;
      if (event.input?.trim()) await saveLiveInput(event.input);
      else userPcm = [];
      status.textContent = "ဆက်ပြောနိုင်ပါပြီ";
    }
    if (event.type === "error") {
      toast(event.error);
      await endLive();
    }
    if (event.type === "closed" && liveActive) await endLive(false);
  });
  try {
    await call(
      bridge.liveStart({
        context: $("#live-topic").value,
        title: "Live · " + $("#live-topic").value.slice(0, 80),
      }),
    );
  } catch (e) {
    await endLive(false);
    throw e;
  }
}
async function saveLiveInput(transcript) {
  if (state.preferences.record && sessionId && userPcm.length) {
    const len = userPcm.reduce((n, p) => n + p.length, 0);
    const pcm = new Int16Array(len);
    let off = 0;
    for (const p of userPcm) {
      pcm.set(p, off);
      off += p.length;
    }
    userPcm = [];
    await call(
      bridge.clip(sessionId, {
        speaker: "user",
        text: transcript || "Live အသံမှတ်တမ်း",
        audio: makeWav(pcm, 16000),
        ext: "wav",
        createdAt: Date.now() - pcm.length / 16,
      }),
    );
  }
}
function makeWav(pcm, rate) {
  const b = new ArrayBuffer(44 + pcm.byteLength),
    v = new DataView(b);
  const s = (off, t) => {
    for (let i = 0; i < t.length; i++) v.setUint8(off + i, t.charCodeAt(i));
  };
  s(0, "RIFF");
  v.setUint32(4, 36 + pcm.byteLength, true);
  s(8, "WAVE");
  s(12, "fmt ");
  v.setUint32(16, 16, true);
  v.setUint16(20, 1, true);
  v.setUint16(22, 1, true);
  v.setUint32(24, rate, true);
  v.setUint32(28, rate * 2, true);
  v.setUint16(32, 2, true);
  v.setUint16(34, 16, true);
  s(36, "data");
  v.setUint32(40, pcm.byteLength, true);
  new Int16Array(b, 44).set(pcm);
  return new Uint8Array(b);
}
async function endLive(tellServer = true) {
  liveActive = false;
  liveReady = false;
  await saveLiveInput("Live အသံမှတ်တမ်း");
  if (tellServer) await call(bridge.liveStop());
  liveUnsubscribe?.();
  liveUnsubscribe = null;
  micStream?.getTracks().forEach((t) => t.stop());
  micStream = null;
  liveNode?.disconnect();
  liveSource?.disconnect();
  await liveAudioCtx?.close();
  liveAudioCtx = null;
  const btn = $("#live-toggle");
  if (btn) {
    btn.classList.remove("recording");
    btn.innerHTML = icon("mic");
  }
  if ($("#live-status"))
    $("#live-status").textContent =
      "စကားဝိုင်းရပ်ပြီးပြီ · Replays မှာ ပြန်နားထောင်နိုင်သည်";
}
function cleanupReplay() {
  clearInterval(replayLoop);
  replayLoop = null;
  const a = $("#replay-audio");
  if (a) a.pause();
  audioUrls.forEach(URL.revokeObjectURL);
  audioUrls = [];
  replayA = null;
  replayB = null;
}
function showReplays() {
  const sessions = state.sessions;
  replayId = sessions.some((s) => s.id === replayId)
    ? replayId
    : sessions[0]?.id;
  el.innerHTML =
    title(
      "YOUR VOICE · YOUR PROGRESS",
      "ပြန်နားထောင်ပြီး တိုးတက်မှုကို ကြည့်ပါ",
      "ကိုယ့်အသံနဲ့ AI ရဲ့ တကယ်ထွက်လာတဲ့အသံကို ခွဲနားထောင်နိုင်သည်။",
    ) +
    `<div class="replay-layout"><aside><input id="replay-search" class="w-full" placeholder="စကားဝိုင်းရှာမယ်"><div id="session-list"></div></aside><section class="card" id="replay-detail"></section></div>`;
  $("#replay-search").oninput = renderSessionList;
  renderSessionList();
  renderReplay();
}
function renderSessionList() {
  const q = $("#replay-search").value.toLowerCase();
  $("#session-list").innerHTML =
    state.sessions
      .filter((s) => s.title.toLowerCase().includes(q))
      .map(
        (s) =>
          `<button class="session-card ${s.id === replayId ? "selected" : ""}" data-session="${s.id}"><strong>${esc(s.title)}</strong><span class="muted small">${fmt(s.createdAt)} · ${s.clips.filter((c) => c.file).length} audio turns</span></button>`,
      )
      .join("") ||
    '<div class="empty">အသံမှတ်တမ်း မရှိသေးပါ။<br>Practice မှာ စတင်နိုင်ပါသည်။</div>';
  $$("[data-session]").forEach(
    (b) =>
      (b.onclick = () => {
        cleanupReplay();
        replayId = b.dataset.session;
        replayIndex = 0;
        renderSessionList();
        renderReplay();
      }),
  );
}
function renderReplay() {
  cleanupReplay();
  const s = state.sessions.find((x) => x.id === replayId);
  const target = $("#replay-detail");
  if (!s) {
    target.innerHTML =
      '<div class="empty">စကားဝိုင်းတစ်ခုရွေးပါ။<br>အသံမှတ်တမ်းဖွင့်ထားရင် ဒီနေရာမှာ ပေါ်လာမည်။</div>';
    return;
  }
  const clips = s.clips
    .filter((c) => replayFilter === "all" || c.speaker === replayFilter)
    .sort((a, b) => a.createdAt - b.createdAt);
  replayFiles = clips.filter((c) => c.file);
  replayIndex = 0;
  target.innerHTML = `<div class="replay-controls"><div class="row between"><div><div class="eyebrow no-margin">SAVED CONVERSATION</div><h3>${esc(s.title)}</h3></div><div class="row"><button class="small" id="export-session">Export</button><button class="small ghost danger" id="delete-session">ဖျက်မယ်</button></div></div><div class="row"><select id="speaker-filter" aria-label="Speaker filter">${[
    ["all", "နှစ်ယောက်စလုံး"],
    ["user", "ငါ့အသံ"],
    ["ai", "AI အသံ"],
  ]
    .map(
      ([v, t]) =>
        `<option value="${v}" ${replayFilter === v ? "selected" : ""}>${t}</option>`,
    )
    .join(
      "",
    )}</select><select id="replay-speed" aria-label="Playback speed">${[0.5, 0.75, 1, 1.25, 1.5].map((n) => `<option value="${n}" ${n === 1 ? "selected" : ""}>${n}× speed</option>`).join("")}</select>${s.source ? '<button id="replay-source" class="small">မူရင်းစာမျက်နှာ</button>' : ""}</div><audio id="replay-audio" controls preload="metadata"></audio><div class="row"><button id="play-all" class="primary small">${icon("play")} အစဉ်လိုက်နားထောင်</button><button id="skip-back" class="small">−5s</button><button id="skip-forward" class="small">+5s</button><button id="loop-a" class="small">Set A</button><button id="loop-b" class="small">Set B</button><button id="loop-clear" class="small">Loop ပိတ်</button><span id="loop-label" class="muted small"></span></div><p class="muted small no-margin" style="margin-top:10px">စာကြောင်းကိုနှိပ်ရင် အဲဒီအလှည့်ရဲ့ အသံအစကနေ နားထောင်မည်။</p></div><div id="replay-clips">${clips.map((c) => `<div class="clip" data-clip-row="${c.id}"><span class="avatar">${c.speaker === "user" ? "ME" : "AI"}</span><button data-play-clip="${c.id}" ${c.file ? "" : "disabled"}><strong>${esc(c.text || "အသံမှတ်တမ်း")}</strong><span class="muted small">${c.file ? "▶ သိမ်းထားသော မူရင်းအသံ" : "စာသားသာရှိသည် · အသံမသိမ်းထားပါ"}</span></button></div>`).join("") || '<div class="empty">ဒီ filter အတွက် အသံမရှိသေးပါ။</div>'}</div>`;
  const audio = $("#replay-audio");
  audio.preservesPitch = true;
  audio.onended = async () => {
    if (replayB != null) return;
    if (replayIndex < replayFiles.length - 1) {
      replayIndex++;
      await playClip(replayFiles[replayIndex]);
    }
  };
  $("#speaker-filter").onchange = (e) => {
    replayFilter = e.target.value;
    renderReplay();
  };
  $("#replay-speed").onchange = (e) =>
    (audio.playbackRate = Number(e.target.value));
  $("#play-all").onclick = (e) =>
    action(e.currentTarget, async () => {
      if (!replayFiles.length) throw Error("အသံဖိုင်မရှိပါ။");
      replayIndex = 0;
      await playClip(replayFiles[0]);
    });
  $$("[data-play-clip]").forEach(
    (b) =>
      (b.onclick = (e) =>
        action(e.currentTarget, async () => {
          const c = replayFiles.find((x) => x.id === b.dataset.playClip);
          replayIndex = replayFiles.indexOf(c);
          await playClip(c);
        })),
  );
  $("#skip-back").onclick = () => {
    audio.currentTime = Math.max(0, audio.currentTime - 5);
  };
  $("#skip-forward").onclick = () => {
    audio.currentTime = Math.min(audio.duration || 0, audio.currentTime + 5);
  };
  $("#loop-a").onclick = () => {
    replayA = audio.currentTime;
    replayB = null;
    $("#loop-label").textContent = "A " + replayA.toFixed(1) + "s";
  };
  $("#loop-b").onclick = () => {
    if (replayA == null || audio.currentTime <= replayA + 0.2)
      return toast("Set A လုပ်ပြီး နောက်နေရာကိုရွှေ့ပါ။");
    replayB = audio.currentTime;
    $("#loop-label").textContent =
      `A ${replayA.toFixed(1)}s → B ${replayB.toFixed(1)}s`;
    clearInterval(replayLoop);
    replayLoop = setInterval(() => {
      if (replayB != null && audio.currentTime >= replayB) {
        audio.currentTime = replayA;
        audio.play().catch(() => {});
      }
    }, 50);
    audio.currentTime = replayA;
    audio.play().catch(() => {});
  };
  $("#loop-clear").onclick = () => {
    replayA = replayB = null;
    clearInterval(replayLoop);
    $("#loop-label").textContent = "";
  };
  $("#export-session").onclick = (e) =>
    action(e.currentTarget, async () => {
      if (await call(bridge.exportSession(s.id)))
        toast("စကားဝိုင်းနဲ့အသံ export လုပ်ပြီးပြီ။");
    });
  $("#delete-session").onclick = () => {
    const d = modal(
      '<h2>ဒီစကားဝိုင်းကို ဖျက်မလား?</h2><p>Replays ထဲက ဒီမှတ်တမ်းကို ဖျက်မည်။</p><button class="danger" id="confirm-delete">ဖျက်မယ်</button>',
    );
    $("#confirm-delete", d).onclick = (e) =>
      action(e.currentTarget, async () => {
        cleanupReplay();
        await save("delete-session", s.id);
        d.close();
        showReplays();
      });
  };
  if ($("#replay-source"))
    $("#replay-source").onclick = () => {
      cleanupReplay();
      openBook(s.source.bookId, s.source.page);
    };
}
async function playClip(clip) {
  if (!clip) return;
  const audio = $("#replay-audio");
  replayA = replayB = null;
  clearInterval(replayLoop);
  $("#loop-label").textContent = "";
  const raw = await call(bridge.media(clip.file));
  const ext = clip.file.split(".").at(-1);
  const url = URL.createObjectURL(
    new Blob([raw], {
      type:
        {
          wav: "audio/wav",
          webm: "audio/webm",
          mp3: "audio/mpeg",
          m4a: "audio/mp4",
          ogg: "audio/ogg",
        }[ext] || "audio/wav",
    }),
  );
  audioUrls.forEach(URL.revokeObjectURL);
  audioUrls = [url];
  audio.src = url;
  audio.playbackRate = Number($("#replay-speed").value);
  $$("[data-clip-row]").forEach((e) =>
    e.classList.toggle("active", e.dataset.clipRow === clip.id),
  );
  await audio.play();
}
function showMe() {
  const notes = state.notebook.length + state.annotations.length;
  el.innerHTML =
    title(
      "SETTLE IN · MAKE IT YOURS",
      "မင်းရဲ့ ကိုယ်ပိုင်လေ့လာရေးနေရာ",
      "မှတ်စု၊ အသံ၊ API နဲ့ app ရွေးချယ်မှုတွေကို ဒီနေရာမှာ စီမံပါ။",
    ) +
    `<div class="stats"><div class="stat"><strong>${notes}</strong><small>သိမ်းထားသော မှတ်စုများ</small></div><div class="stat"><strong>${state.sessions.length}</strong><small>စကားဝိုင်းမှတ်တမ်း</small></div><div class="stat"><strong>${Object.values(state.practice).filter((p) => p.completed).length}</strong><small>ပြီးခဲ့သော သင်ခန်းစာ</small></div></div><div class="settings-grid"><section class="card"><div class="row between"><div class="eyebrow">AI & APIs</div><span class="tag">WINDOWS ENCRYPTED STORAGE</span></div><h2>ကိုယ့် API နဲ့ ဆက်လေ့လာပါ</h2><p class="muted small">Key မပါဝင်ပါ။ ကိုယ်တိုင်ထည့်ထားတဲ့ key ကို ဒီ Windows account မှာသာ ဖွင့်ဖတ်နိုင်အောင် သိမ်းထားသည်။</p><form id="api-form"><label>စာသား / PDF အတွက် provider<select name="provider"><option value="gemini" ${settings.provider !== "nvidia" ? "selected" : ""}>Gemini</option><option value="nvidia" ${settings.provider === "nvidia" ? "selected" : ""}>NVIDIA · Text only</option></select></label><label>Gemini API key ${settings.geminiKeySaved ? "· သိမ်းထားပြီး ✓" : ""}<input name="geminiKey" type="password" autocomplete="off" placeholder="${settings.geminiKeySaved ? "မပြောင်းချင်ရင် အလွတ်ထားပါ" : "Gemini key ထည့်ပါ"}"></label><label>NVIDIA API key ${settings.nvidiaKeySaved ? "· သိမ်းထားပြီး ✓" : ""}<input name="nvidiaKey" type="password" autocomplete="off" placeholder="${settings.nvidiaKeySaved ? "မပြောင်းချင်ရင် အလွတ်ထားပါ" : "NVIDIA key ထည့်ပါ"}"></label><div class="form-pair"><label>Gemini Text/PDF model<input name="geminiModel" value="${esc(settings.geminiModel || "gemini-3.5-flash-lite")}" list="gemini-models"></label><label>NVIDIA Text model<input name="nvidiaModel" value="${esc(settings.nvidiaModel || "z-ai/glm-5.3-flash")}" list="nvidia-models"></label></div><div class="form-pair"><label>Gemini Speech model<input name="speechModel" value="${esc(settings.speechModel || "gemini-3.8-flash-tts")}" list="gemini-models"></label><label>Gemini Live model<input name="liveModel" value="${esc(settings.liveModel || "gemini-3.8-live")}" list="gemini-models"></label></div><datalist id="gemini-models"></datalist><datalist id="nvidia-models"></datalist><button class="primary" id="save-api">API settings သိမ်းမယ်</button></form><div class="spacer"></div><div class="row"><select id="probe-provider" aria-label="Test provider"><option value="gemini">Gemini</option><option value="nvidia">NVIDIA</option></select><button id="load-models" class="small">Models ရယူမယ်</button></div><div class="row" style="margin-top:12px">${[
      ["text", "Text"],
      ["pdf", "PDF"],
      ["speech", "Speech"],
      ["transcribe", "Transcribe"],
      ["live", "Live"],
    ]
      .map(([k, v]) => `<button class="small" data-probe="${k}">${v}</button>`)
      .join(
        "",
      )}</div><div id="probe-result"></div><p class="muted small">PDF test က နမူနာစာသားကို စစ်သည်။ Transcribe test က AI အသံနမူနာကို သုံးသည်။ Mic အစစ်က Practice မှာ စမ်းပါ။</p><div id="operation-error" class="error" hidden></div><details><summary class="small muted">Key ဖျက်ရန်</summary><div class="row"><button class="small danger" data-clear-key="gemini">Gemini key ဖျက်မယ်</button><button class="small danger" data-clear-key="nvidia">NVIDIA key ဖျက်မယ်</button></div></details></section><div><section class="card"><div class="eyebrow">PREFERENCES</div><form id="preferences-form"><label>Display name<input name="displayName" maxlength="100" value="${esc(state.preferences.displayName)}" placeholder="မင်းနာမည်"></label><div class="form-pair"><label>Theme<select name="theme">${["system", "light", "dark"].map((t) => `<option ${state.preferences.theme === t ? "selected" : ""}>${t}</option>`).join("")}</select></label><label>Korean voice<select name="voice">${["Kore", "Puck", "Aoede", "Charon", "Fenrir", "Leda", "Orus", "Zephyr"].map((v) => `<option ${state.preferences.voice === v ? "selected" : ""}>${v}</option>`).join("")}</select></label></div><label class="toggle-label">အသံမှတ်တမ်း သိမ်းမယ်<input type="checkbox" name="record" ${state.preferences.record ? "checked" : ""}></label><label class="toggle-label">AI ကိုရီးယားအသံ အလိုအလျောက်ဖွင့်မယ်<input type="checkbox" name="autoSpeak" ${state.preferences.autoSpeak ? "checked" : ""}></label><button>ရွေးချယ်မှု သိမ်းမယ်</button></form></section><div class="spacer"></div><section class="card"><div class="eyebrow">YOUR NOTEBOOK</div><h3>မမေ့ချင်တဲ့ စကားလုံးနဲ့ မှတ်စုများ</h3><p class="muted small">မူရင်းစာမျက်နှာနဲ့ တွဲသိမ်းထားပြီး ပြန်ရှာနိုင်သည်။</p><button id="open-notebook">ကိုယ်ပိုင်မှတ်စုစာအုပ်</button></section><div class="spacer"></div><section class="card"><div class="eyebrow">BACKUP & RESTORE</div><h3>ကိုယ့်တိုးတက်မှုကို သိမ်းထားပါ</h3><p class="muted small">ဒီ Windows app ရဲ့ PDF၊ Track၊ အသံနဲ့ မှတ်စုပါဝင်သည်။ API key မပါဝင်ပါ။ Android backup နဲ့ format မတူသေးပါ။</p><div class="row"><button id="backup" class="primary">Export backup</button><button id="restore">Restore backup</button></div><p class="muted small" style="margin-top:12px">Windows app ဒေတာက Android နဲ့ အလိုအလျောက် sync မလုပ်သေးပါ။</p></section></div></div>`;
  $("#api-form").onsubmit = (e) => {
    e.preventDefault();
    action($("#save-api"), async () => {
      settings = await call(
        bridge.settings(Object.fromEntries(new FormData(e.target))),
      );
      $("#api-form input[name=geminiKey]").value = "";
      $("#api-form input[name=nvidiaKey]").value = "";
      toast("API settings သိမ်းပြီးပြီ။ ခလုတ်တစ်ခုချင်း စမ်းနိုင်ပါသည်။");
    });
  };
  $("#preferences-form").onsubmit = (e) => {
    e.preventDefault();
    action($("button", e.target), async () => {
      const values = Object.fromEntries(new FormData(e.target));
      values.record = $("[name=record]", e.target).checked;
      values.autoSpeak = $("[name=autoSpeak]", e.target).checked;
      await save("preferences", values);
      applyTheme();
      toast("ရွေးချယ်မှုတွေ သိမ်းပြီးပြီ။");
    });
  };
  $("#load-models").onclick = (e) =>
    action(e.currentTarget, async () => {
      const provider = $("#probe-provider").value;
      const models = await call(bridge.api("catalog", { provider }));
      $("#" + provider + "-models").innerHTML = models
        .map((m) => `<option value="${esc(m)}"></option>`)
        .join("");
      $("#probe-result").innerHTML =
        `<div class="notice">Models ${models.length} ခု တွေ့ပါသည်။ Model input မှာ ရွေးနိုင်သည်။ စာရင်းပါခြင်းက quota ရှိကြောင်း မဆိုလိုပါ။</div>`;
    });
  $$("[data-probe]").forEach(
    (b) =>
      (b.onclick = (e) =>
        action(e.currentTarget, async () => {
          const feature = b.dataset.probe;
          if (feature === "live") {
            practiceMode = "live";
            await navigate("practice");
            return;
          }
          const p = $("#probe-result");
          p.innerHTML = '<div class="notice">စမ်းသပ်နေပါသည်…</div>';
          const started = Date.now();
          try {
            const result = await call(
              bridge.api("probe", {
                provider: $("#probe-provider").value,
                feature,
              }),
            );
            p.innerHTML = `<div class="notice">${esc(feature)} ✓ · ${((Date.now() - started) / 1000).toFixed(1)}s${result.heardText ? "<br>" + esc(result.heardText) : ""}</div>`;
            if (feature === "speech") {
              const raw = fromBase64(result.data),
                url = URL.createObjectURL(
                  new Blob([raw], { type: result.mime }),
                );
              blobUrls.push(url);
              p.insertAdjacentHTML(
                "beforeend",
                '<audio id="probe-audio" controls autoplay></audio>',
              );
              $("#probe-audio").src = url;
            }
          } catch (err) {
            p.innerHTML = `<div class="error">${esc(feature)} ✕ · ${esc(err.message)}</div>`;
          }
        })),
  );
  $$("[data-clear-key]").forEach(
    (b) =>
      (b.onclick = (e) =>
        action(e.currentTarget, async () => {
          settings = await call(
            bridge.settings({ ["clear" + b.dataset.clearKey]: true }),
          );
          toast("Key ဖျက်ပြီးပြီ။");
          showMe();
        })),
  );
  $("#open-notebook").onclick = () => showNotebook();
  $("#backup").onclick = (e) =>
    action(e.currentTarget, async () => {
      if (await call(bridge.backup())) toast("Windows backup သိမ်းပြီးပြီ။");
    });
  $("#restore").onclick = (e) =>
    action(e.currentTarget, async () => {
      const value = await call(bridge.restore());
      if (value) {
        state = value;
        applyTheme();
        showMe();
        toast("Backup ပြန်သွင်းပြီးပြီ။");
      }
    });
}
$$("[data-icon]").forEach((e) => (e.innerHTML = icon(e.dataset.icon)));
$("#theme-toggle").innerHTML = icon("sun");
$("#theme-toggle").onclick = () =>
  action(null, async () => {
    await save("preferences", {
      theme: document.body.dataset.theme === "dark" ? "light" : "dark",
    });
    applyTheme();
  });
$$("[data-nav]").forEach(
  (b) => (b.onclick = () => action(b, () => navigate(b.dataset.nav))),
);
document.addEventListener("keydown", (e) => {
  if (
    book &&
    pdf &&
    !$("dialog[open]") &&
    !/INPUT|TEXTAREA|SELECT/.test(e.target.tagName)
  ) {
    if (e.key === "ArrowRight") changePage(pageNo + 1);
    if (e.key === "ArrowLeft") changePage(pageNo - 1);
  }
});
window.addEventListener("unhandledrejection", (e) => {
  e.preventDefault();
  toast(e.reason?.message || "Operation failed");
});
matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () =>
  applyTheme(),
);
try {
  await refresh();
  showBooks();
} catch (e) {
  el.innerHTML = `<div class="error">${esc(e.message)}</div>`;
}
