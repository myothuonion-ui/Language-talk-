const { test } = require("node:test");
const assert = require("node:assert/strict");
const book = require("../../app/src/main/assets/books/ttmik-beginner.json");
test("character practice stays on the current source page and excludes translated/review duplicates", async () => {
  const { dialogueForPage, speakersOf } = await import("../src/dialogue.mjs");
  const long = dialogueForPage(book, 15, "");
  assert.equal(long.length, 12);
  assert.deepEqual(speakersOf(long), ["성찬", "지연"]);
  assert.equal(dialogueForPage(book, 13, "").length, 2);
  assert.equal(dialogueForPage(book, 16, "").length, 0);
  assert.ok(long.every((t) => t.page === 15));
});
test("new books can parse named Korean turns without treating English translation as another character", async () => {
  const { parseDialogue, dialogueForPage, speakersOf } = await import(
    "../src/dialogue.mjs"
  );
  const source =
    "민수: 안녕하세요.\nHello.\n수진： 반가워요.\nTranslation: Nice to meet you.\nTrack 03\n";
  const turns = dialogueForPage({}, 7, source);
  assert.equal(turns.length, 2);
  assert.deepEqual(speakersOf(turns), ["민수", "수진"]);
  assert.ok(turns.every((t) => t.page === 7));
  assert.deepEqual(parseDialogue("안녕하세요. Vocabulary book", 1), []);
});
test("source quotes locate the complete sentence instead of highlighting repeated greetings elsewhere", async () => {
  const { quoteSpanIndexes } = await import("../src/dialogue.mjs");
  const lines = [
    "성찬: ",
    "안녕하세요.",
    "Hello.",
    "지연: ",
    "네, 안녕하세요. ",
    "처음 ",
    "뵙겠습니다.",
    "성찬: ",
    "네.",
  ];
  assert.deepEqual(
    quoteSpanIndexes(lines, "네, 안녕하세요. 처음 뵙겠습니다."),
    [4, 5, 6],
  );
  assert.deepEqual(quoteSpanIndexes(lines, "없는 문장"), []);
});
