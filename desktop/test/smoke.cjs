const { _electron: electron } = require("playwright");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const os = require("node:os");
(async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "language-talk-ui-"));
  const errors = [];
  let app;
  const { Store, wav } = require("../lib/core.cjs");
  const seed = new Store(dir);
  const session = seed.session("Replay validation");
  seed.clip(session.id, {
    speaker: "user",
    text: "안녕하세요",
    audio: wav(Buffer.alloc(96000)),
    ext: "wav",
  });
  seed.clip(session.id, {
    speaker: "ai",
    text: "반가워요",
    audio: wav(Buffer.alloc(96000)),
    ext: "wav",
  });
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
      timeout: 30000,
    });
    const page = await app.firstWindow();
    await page.setViewportSize({ width: 1380, height: 900 });
    fs.mkdirSync(path.join(__dirname, "../test-output"), { recursive: true });
    page.on("pageerror", (e) => errors.push(e.message));
    await page.waitForSelector('[data-book="ttmik-beginner"]', {
      timeout: 20000,
    });
    assert.equal(await page.title(), "Language Talk");
    await page.screenshot({
      path: path.join(__dirname, "../test-output/books.png"),
    });
    await page.locator('[data-book="ttmik-beginner"]').click();
    await page.waitForSelector("#next-page");
    await page.waitForFunction(
      () => document.querySelector("#pdf-canvas")?.height > 0,
    );
    await page.waitForTimeout(500);
    await page.locator("#page-input").fill("13");
    await page.locator("#page-input").press("Tab");
    await page.waitForFunction(() =>
      document.querySelector("#text-layer")?.textContent.includes("안녕하세요"),
    );
    await page.screenshot({
      path: path.join(__dirname, "../test-output/reader.png"),
    });
    await page.locator('[data-tab="text"]').click();
    await page.waitForSelector(".reader-text");
    assert.ok(
      (await page.locator(".reader-text").textContent()).includes("안녕하세요"),
    );
    await page.evaluate(() => {
      const node = document.querySelector(".reader-text");
      const range = document.createRange();
      range.selectNodeContents(node);
      const s = getSelection();
      s.removeAllRanges();
      s.addRange(range);
      node.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));
    });
    await page.locator('[data-annotate="highlight"]').click();
    await page.locator("#reader-notes").click();
    await page.waitForSelector(".note");
    await page.locator("dialog .close").click();
    await page.locator("#shadow-selection").click();
    await page.locator("#shadow-record").click();
    await page.waitForTimeout(1100);
    await page.locator("#shadow-record").click();
    await page.waitForSelector("#shadow-playback audio");
    await page.locator("dialog .close").click();
    await page.locator('[data-nav="practice"]').click();
    await page.waitForSelector("#record-mic");
    const initial = await page.locator(".practice-stage h2").textContent();
    await page.locator("#next-unit").click();
    await page.waitForFunction(
      (t) => document.querySelector(".practice-stage h2")?.textContent !== t,
      initial,
    );
    await page.locator('[data-practice="book"]').click();
    await page.locator('[data-book-practice="ttmik-beginner"]').click();
    await page.locator('[data-ch="3"]').click();
    await page.waitForSelector("#next-turn");
    await page.screenshot({
      path: path.join(__dirname, "../test-output/practice.png"),
    });
    await page.locator('[data-nav="me"]').click();
    await page.waitForSelector("#api-form");
    await page.locator("#theme-toggle").click();
    await page.waitForFunction(() => document.body.dataset.theme === "dark");
    await page.screenshot({
      path: path.join(__dirname, "../test-output/settings-dark.png"),
    });
    await page.locator('[data-probe="text"]').click();
    await page.waitForFunction(() =>
      document.querySelector("#probe-result")?.textContent.includes("API key"),
    );
    await page.locator('[data-nav="replays"]').click();
    await page.waitForSelector("#replay-detail");
    await page.waitForSelector("#speaker-filter");
    await page.locator("[data-session]").last().click();
    await page.locator("#speaker-filter").selectOption("user");
    assert.equal(await page.locator("[data-play-clip]").count(), 1);
    await page.locator("#play-all").click();
    await page.waitForFunction(
      () => document.querySelector("#replay-audio")?.currentTime > 0,
    );
    await page.locator("#replay-speed").selectOption("0.75");
    assert.equal(
      await page.locator("#replay-audio").evaluate((a) => a.playbackRate),
      0.75,
    );
    await page.locator("#loop-a").click();
    await page.locator("#replay-audio").evaluate((a) => (a.currentTime = 1.4));
    await page.locator("#loop-b").click();
    await page.waitForFunction(() =>
      document.querySelector("#loop-label").textContent.includes("→"),
    );
    await page.screenshot({
      path: path.join(__dirname, "../test-output/replays.png"),
    });
    assert.deepEqual(errors, []);
    await app.close();
    app = null;
    const saved = JSON.parse(
      fs.readFileSync(path.join(dir, "state.json"), "utf8"),
    );
    assert.equal(saved.annotations.length, 1);
    assert.ok(
      saved.sessions.some(
        (s) =>
          s.title.startsWith("Shadowing") &&
          s.clips.some((c) => c.speaker === "user" && c.file),
      ),
    );
    assert.ok(
      saved.sessions.every((s) =>
        s.clips.every(
          (c) => !c.file || fs.existsSync(path.join(dir, "media", c.file)),
        ),
      ),
    );
    assert.equal(saved.pages["ttmik-beginner"], 13);
    assert.equal(saved.preferences.theme, "dark");
    assert.ok(saved.practice["course-0-1"].completed);
    console.log(
      "Desktop smoke passed: bundled PDF, text selection, persisted highlight, explicit Next, unlocked chapters, themes and honest missing-key error.",
    );
  } finally {
    await app?.close();
    fs.rmSync(dir, { recursive: true, force: true });
  }
})().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
