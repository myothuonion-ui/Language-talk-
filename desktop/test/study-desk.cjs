const { _electron: electron } = require("playwright");
const assert = require("node:assert/strict"),
  fs = require("node:fs"),
  path = require("node:path"),
  os = require("node:os");
const { Store, wav } = require("../lib/core.cjs");
(async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "language-talk-desk-"));
  let app;
  const errors = [];
  const seed = new Store(dir);
  seed.state.pages["ttmik-beginner"] = 15;
  seed.state.preferences.autoSpeak = false;
  const file = seed.put(wav(Buffer.alloc(96000)), "wav");
  seed.state.tracks.push({
    bookId: "ttmik-beginner",
    number: 3,
    file,
    title: "Track 03.wav",
    pages: [15],
  });
  seed.save();
  const out = path.join(__dirname, "../test-output");
  fs.mkdirSync(out, { recursive: true });
  try {
    app = await electron.launch({
      executablePath: process.env.LANGUAGE_TALK_EXECUTABLE
        ? path.resolve(process.env.LANGUAGE_TALK_EXECUTABLE)
        : undefined,
      args: [
        ...(process.platform === "linux"
          ? ["--no-sandbox", "--ozone-platform=headless"]
          : []),
        "--use-fake-device-for-media-stream",
        "--use-fake-ui-for-media-stream",
        ...(process.env.LANGUAGE_TALK_EXECUTABLE
          ? []
          : [path.resolve(__dirname, "..")]),
      ],
      env: { ...process.env, LANGUAGE_TALK_TEST_DIR: dir },
    });
    const p = await app.firstWindow();
    p.setDefaultTimeout(15000);
    await p.setViewportSize({ width: 1380, height: 900 });
    p.on("pageerror", (e) => errors.push(e.message));
    // Stub paid AI responses only; native PDF, IPC persistence and WAV playback remain real.
    await app.evaluate(
      ({ ipcMain }, audio) => {
        ipcMain.removeHandler("api");
        ipcMain.handle("api", (_event, action, p) => {
          if (action === "speech")
            return { ok: true, value: { data: audio, mime: "audio/wav" } };
          if (action === "roleplay") {
            if (p.myRole !== "지연" || p.aiRole !== "성찬")
              return { ok: false, error: "Wrong role mapping" };
            return {
              ok: true,
              value: {
                reply: p.mode === "free" ? "어디에 사세요?" : "",
                meaning: "ဘယ်မှာ နေပါသလဲ။",
                explanation: "အဓိပ္ပာယ်ကို စစ်ပြီးပြီ။",
                correction: "",
                assessment: "PASSED",
              },
            };
          }
          return { ok: false, error: "Unexpected test API action" };
        });
      },
      wav(Buffer.alloc(96000)).toString("base64"),
    );
    await p.locator('[data-book="ttmik-beginner"]').click();
    await p.waitForFunction(() =>
      document
        .querySelector("#text-layer")
        ?.textContent.includes("처음 뵙겠습니다"),
    );
    const initial = await p
      .locator("#pdf-canvas")
      .evaluate((c) => c.getBoundingClientRect().width);
    await p.locator("#reader-panel-toggle").click();
    await p.waitForFunction(
      (w) =>
        document.querySelector("#pdf-canvas").getBoundingClientRect().width >
        w + 200,
      initial,
    );
    await p.locator("#reader-focus").click();
    assert.equal(await p.locator(".sidebar").isVisible(), false);
    await p.locator("#fit-page").click();
    await p.waitForTimeout(400);
    const fit = await p.locator("#pdf-frame").boundingBox(),
      area = await p.locator("#pdf-area").boundingBox();
    assert.ok(fit.height <= area.height + 1);
    await p.locator("#fit-width").click();
    const before = await p.locator("#pdf-canvas").evaluate((c) => c.width);
    await p.locator("#zoom-in").click();
    await p.waitForFunction(
      (w) => document.querySelector("#pdf-canvas").width > w,
      before,
    );
    await p.locator("#zoom-out").click();
    await p.locator("#reader-focus").click();
    await p.locator("#reader-panel-toggle").click();
    await p.waitForTimeout(600);
    await p.screenshot({ path: path.join(out, "study-desk.png") });
    await p.locator('.pdf-track-hit[data-track="3"]').click();
    await p.waitForFunction(
      () => document.querySelector("#track-audio")?.currentTime > 0,
    );
    await p.locator("#track-audio").evaluate((a) => a.pause());
    await p.waitForFunction(
      () => document.querySelectorAll("#text-layer span").length > 10,
    );
    await p.evaluate(() => {
      const span = [...document.querySelectorAll("#text-layer span")].find(
        (s) => /안녕/.test(s.textContent),
      );
      const range = document.createRange();
      range.selectNodeContents(span);
      getSelection().removeAllRanges();
      getSelection().addRange(range);
      span.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));
    });
    await p.locator('[data-annotate="underline"]').click();
    assert.ok((await p.locator(".pdf-mark.underline").count()) > 0);
    await p.locator('[data-annotate="bold"]').click();
    assert.equal(await p.locator("#reading-mode").inputValue(), "text");
    assert.ok((await p.locator("#reader-text-main strong").count()) > 0);
    await p.locator("#reading-mode").selectOption("pdf");
    await p.locator("#reader-roleplay").click();
    await p.locator('[data-role-choice="지연"]').click();
    await p.locator("#role-start").click();
    await p.locator("#role-listen").click();
    await p.waitForFunction(
      () => !document.querySelector("#role-next").disabled,
    );
    await p.locator("#role-next").click();
    await p.locator("#role-answer").fill("네, 안녕하세요. 처음 뵙겠습니다.");
    await p.locator("#role-send").click();
    await p.waitForSelector(".role-feedback");
    assert.ok(
      (await p.locator("#book-role-panel").textContent()).includes(
        "အလှည့် 2 / 12",
      ),
    );
    await p.screenshot({ path: path.join(out, "study-roleplay.png") });
    await p.locator("#role-stop").click();
    assert.equal(await p.locator(".reader-side").isVisible(), false);
    await p.locator("#reader-roleplay").click();
    await p.locator('[data-role-choice="지연"]').click();
    await p.locator("#role-mode").selectOption("free");
    await p.locator("#role-start").click();
    await p.locator("#role-next").click();
    await p.locator("#role-answer").fill("저는 서울에 살아요.");
    await p.locator("#role-send").click();
    await p.waitForSelector(".role-feedback .korean");
    await p.locator("#role-next").click();
    assert.ok(
      (await p.locator("#book-role-panel").textContent()).includes(
        "အလှည့် 4 / 12",
      ),
    );
    await p.locator("#role-stop").click();
    await p.locator('[data-nav="me"]').click();
    await app.evaluate(async ({ clipboard }) => {
      await clipboard.writeText("sample-paste-test-only");
    });
    await p.locator('[data-paste-key="geminiKey"]').click();
    assert.equal(
      await p.locator('[name="geminiKey"]').inputValue(),
      "sample-paste-test-only",
    );
    await p.locator('[data-show-key="geminiKey"]').click();
    assert.equal(
      await p.locator('[name="geminiKey"]').getAttribute("type"),
      "text",
    );
    await p.locator('[name="geminiKey"]').fill("");
    assert.equal(
      await app.evaluate(({ BrowserWindow }) =>
        BrowserWindow.getAllWindows()[0].webContents.listenerCount(
          "context-menu",
        ),
      ),
      1,
    );
    await p.locator('[name="nvidiaKey"]').focus();
    await p.keyboard.press("Control+V");
    assert.equal(
      await p.locator('[name="nvidiaKey"]').inputValue(),
      "sample-paste-test-only",
    );
    await p.locator('[name="nvidiaKey"]').fill("");
    await p.locator('[data-nav="replays"]').click();
    await p.locator("[data-session]").last().click();
    assert.ok(
      (await p.locator("#replay-clips").textContent()).includes(
        "처음 뵙겠습니다",
      ),
    );
    assert.ok(
      (await p.locator("[data-play-clip]:not([disabled])").count()) > 0,
    );
    await p.locator('[data-nav="books"]').click();
    const importedPdf = path.resolve(
      __dirname,
      "../../app/src/main/assets/books/ttmik-beginner.pdf",
    );
    await app.evaluate(({ dialog }, file) => {
      dialog.showOpenDialog = async () => ({
        canceled: false,
        filePaths: [file],
      });
    }, importedPdf);
    await p.locator("#import-pdf").click();
    await p.waitForSelector("#page-input");
    await p.locator("#page-input").fill("15");
    await p.locator("#page-input").press("Tab");
    await p.waitForFunction(() =>
      document
        .querySelector("#text-layer")
        ?.textContent.includes("처음 뵙겠습니다"),
    );
    await p.locator("#reader-roleplay").click();
    await p.locator('[data-role-choice="지연"]').waitFor();
    await p.locator("dialog .close").click();
    await p.setViewportSize({ width: 960, height: 700 });
    await p.waitForTimeout(500);
    const dims = await p.evaluate(() => ({
      w: innerWidth,
      scroll: document.documentElement.scrollWidth,
    }));
    assert.ok(dims.scroll <= dims.w);
    await p.screenshot({ path: path.join(out, "study-desk-small.png") });
    assert.deepEqual(errors, []);
    await app.close();
    app = null;
    const saved = JSON.parse(
      fs.readFileSync(path.join(dir, "state.json"), "utf8"),
    );
    assert.equal(saved.books.length, 1);
    assert.equal(saved.annotations.length, 2);
    assert.ok(saved.annotations[0].rects.length);
    assert.equal(
      saved.sessions.filter((s) => s.title.startsWith("Roleplay")).length,
      2,
    );
    assert.ok(
      saved.sessions.some((s) =>
        s.clips.some((c) => c.speaker === "ai" && c.file),
      ),
    );
    assert.ok(
      !fs
        .readFileSync(path.join(dir, "state.json"), "utf8")
        .includes("sample-paste-test-only"),
    );
    console.log(
      "Study Desk passed: full-page zoom/fit/focus, original Track hotspot playback, coordinate annotations, Text mode, role mapping/feedback/explicit Next, free mode, paired saved AI audio, native Paste/Ctrl+V and context menu.",
    );
  } finally {
    await app?.close();
    fs.rmSync(dir, { recursive: true, force: true });
  }
})().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
