"""Cross-check the packaged course against original files and book-specific cases."""
import hashlib
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/books"


class BuiltInBooksTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.beginner = json.loads((ASSETS / "ttmik-beginner.json").read_text(encoding="utf8"))
        cls.intermediate = json.loads((ASSETS / "ttmik-intermediate.json").read_text(encoding="utf8"))

    def test_exact_edition_and_all_chapter_page_ranges(self):
        for book, count, pages in [(self.beginner, 40, 332), (self.intermediate, 30, 376)]:
            self.assertEqual(book["pageCount"], pages)
            self.assertEqual([c["number"] for c in book["chapters"]], list(range(1, count + 1)))
            self.assertEqual(book["chapters"][0]["startPage"], 12)
            self.assertEqual(book["chapters"][-1]["endPage"], pages - 1)
            for left, right in zip(book["chapters"], book["chapters"][1:]):
                self.assertEqual(left["endPage"] + 1, right["startPage"])
            pdf = ASSETS / (book["id"] + ".pdf")
            self.assertEqual(hashlib.sha256(pdf.read_bytes()).hexdigest(), book["pdfSha256"])
            self.assertEqual(pdf.read_bytes()[:5], b"%PDF-")

    def test_beginner_original_section_order_and_answers(self):
        expected = ["short", "short-vocab", "culture", "long", "long-vocab", "grammar", "pronunciation", "review-short", "review-long"]
        for chapter in self.beginner["chapters"]:
            self.assertEqual([s["id"] for s in chapter["sections"]], expected)
            self.assertEqual(len(chapter["sections"][0]["turns"]), 2)
            self.assertEqual(len(chapter["sections"][5]["exercises"]), 4)
            if chapter["number"] in [4, 5, 27, 36]:
                grammar = chapter["sections"][5]
                self.assertEqual(grammar["exercises"][-1]["page"], chapter["startPage"] + 6)
                self.assertEqual(grammar["pages"], [chapter["startPage"] + 5, chapter["startPage"] + 6])
            self.assertGreaterEqual(len(chapter["sections"][6]["exercises"]), 2)
            self.assertGreaterEqual(len(chapter["sections"][8]["turns"]), 8)
        exercises = self.beginner["chapters"][0]["sections"][5]["exercises"]
        self.assertEqual([e["answer"] for e in exercises], ["저는 회사원이에요.", "저는 엔지니어예요.", "일하세요?", "가르치세요?"])
        self.assertEqual(self.beginner["chapters"][-1]["sections"][6]["exercises"][-1]["answer"], "조은 거야")
        self.assertEqual(self.beginner["chapters"][29]["sections"][6]["exercises"][-1]["answer"], "안자서")

    def test_two_column_overtime_dialogue_keeps_reading_order(self):
        turns = self.beginner["chapters"][20]["sections"][3]["turns"]
        self.assertEqual(len(turns), 17)
        self.assertEqual(turns[0]["text"], "여보, 오늘 언제 와요?")
        self.assertEqual(turns[1]["text"], "오늘 늦을 거예요.")
        self.assertEqual(turns[10]["text"], "오늘 우리 결혼기념일이에요.")
        self.assertEqual(turns[-1]["text"], "네.")
        self.assertEqual(turns[-2]["text"], "오늘 야근해야 돼요. 25일에는 일찍 갈게요.")
        # The printed Korean Only taxi dialogue splits one turn into two.
        taxi = self.beginner["chapters"][35]["sections"]
        self.assertEqual(len(taxi[3]["turns"]), 13)
        self.assertEqual(len(taxi[8]["turns"]), 14)

    def test_intermediate_formal_casual_and_multiple_speakers(self):
        first_patterns = [s for s in self.intermediate["chapters"][0]["sections"] if s["kind"] == "GRAMMAR"]
        self.assertEqual([[e["page"] for e in s["exercises"]] for s in first_patterns], [[19, 19], [21, 21], [23, 23]])
        split = self.intermediate["chapters"][1]["sections"]
        self.assertEqual([s["id"] for s in split], ["formal", "casual", "vocab", "pattern-1", "pattern-2", "pattern-3"])
        self.assertEqual({t["speaker"] for t in split[0]["turns"]}, {"미영", "수철"})
        self.assertEqual({t["speaker"] for t in split[1]["turns"]}, {"주희", "성진"})
        hospital = self.intermediate["chapters"][25]["sections"][0]["turns"]
        self.assertEqual({t["speaker"] for t in hospital}, {"간호사", "수민", "의사"})
        meeting = self.intermediate["chapters"][17]["sections"][0]["turns"]
        self.assertTrue(any(t["speaker"] == "성 대리, 최 대리" for t in meeting))
        for chapter in self.intermediate["chapters"]:
            grammar = [s for s in chapter["sections"] if s["kind"] == "GRAMMAR"]
            self.assertEqual(len(grammar), 3)
            self.assertTrue(all(len(s["exercises"]) == 2 for s in grammar))
            self.assertTrue(all("Applied Patterns" in s["sourceText"] for s in grammar))
            self.assertTrue(all("Answer Key" not in s["sourceText"] for s in grammar))

    def test_all_dialogue_translations_and_references_are_clean(self):
        for book in [self.beginner, self.intermediate]:
            for chapter in book["chapters"]:
                for section in chapter["sections"]:
                    self.assertTrue(all(chapter["startPage"] <= p <= chapter["endPage"] for p in section["pages"]))
                    self.assertTrue(section["sourceText"])
                    self.assertFalse(re.search(r"[\u0590-\u05ff\u0a00-\u0aff]", section["sourceText"]))
                    for turn in section["turns"]:
                        self.assertTrue(re.search("[가-힣]", turn["text"]))
                        self.assertFalse(re.search(r"Answer|Dialogue|Exercises|Track|Real-Life", turn["text"]))
                        if section["kind"] == "DIALOGUE":
                            self.assertTrue(turn["english"])
                            self.assertFalse(re.search("[가-힣]", turn["english"]))
                    for ex in section["exercises"]:
                        self.assertTrue(ex["prompt"] and ex["answer"])
                        self.assertIn(ex["page"], section["pages"])
                        self.assertFalse("Answer Key" in ex["prompt"])
                        self.assertFalse(re.search(r"\b[ABC]\.\s+When", ex["prompt"]))


if __name__ == "__main__":
    unittest.main()
