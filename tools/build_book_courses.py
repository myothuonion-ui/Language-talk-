"""Extract the two user-supplied TTMIK PDFs into page-linked Android courses.

Poppler is deliberate: some other PDF extractors corrupt the Hangul in these files.
Every dialogue translation and exercise answer is checked before writing assets.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess

GROUPS = ["Introductions", "Friends", "Invitations", "Family", "Shopping", "On a Date",
          "At Work", "School", "Food", "Health", "Transportation"]
KO_ROLE = re.compile(r"^\s*([가-힣][가-힣\s,0-9]{0,35}):\s*(.+)$")
EN_ROLE = re.compile(r"^\s*([A-Z][A-Za-z\s,.'’()\-0-9]{0,85}):\s*(.+)$")


def clean_page(text):
    lines = []
    for line in text.splitlines():
        s = line.strip()
        if not s or "RealLifeConversation_" in s:
            continue
        if "Real-Life Korean Conversations" in s or re.fullmatch(r"\d+", s):
            continue
        if re.fullmatch(r"Dialogue\s+\d+\s*-.*", s):
            continue
        if any(re.fullmatch(re.escape(g) + r"\s*\d*", s) or
               re.fullmatch(r"\d*\s*" + re.escape(g), s) for g in GROUPS):
            continue
        line = re.sub(r"\s+Track\s+\d+\s*$", "", line).rstrip()
        if re.fullmatch(r"Track\s+\d+", line.strip()):
            continue
        lines.append(line)
    return "\n".join(lines)


def role_blocks(text, pattern):
    blocks = []
    current = None
    scene = ""
    for line in clean_page(text).splitlines():
        s = line.strip()
        if s in ("Formal Conversation", "Casual Conversation"):
            scene = s
            current = None
            continue
        m = pattern.match(line)
        if m:
            current = {"speaker": m[1].strip(), "lines": [m[2].strip()], "scene": scene}
            blocks.append(current)
        elif current and s:
            current["lines"].append(s)
    return blocks


def beginner_turns(text, page):
    blocks = role_blocks(text, KO_ROLE)
    turns = []
    for b in blocks:
        ko, en = [], []
        english_started = False
        for line in b["lines"]:
            if not re.search(r"[가-힣]", line):
                english_started = True
            (en if english_started else ko).append(line)
        assert ko and en, (page, b)
        turns.append({"speaker": b["speaker"], "text": " ".join(ko),
                      "english": " ".join(en), "page": page})
    return turns


def beginner_review(text, page, originals):
    result = {"short": [], "long": []}
    part, current = "short", None
    translations = {re.sub(r"[^가-힣0-9]", "", t["text"]): t["english"] for t in originals}
    for line in clean_page(text).splitlines():
        condensed = re.sub(r"\s+", "", line)
        if "ShortDialogue" in condensed:
            part, current = "short", None
            continue
        if "LongDialogue" in condensed:
            part, current = "long", None
            continue
        if "AnswerKey" in condensed:
            current = None
            continue
        m = KO_ROLE.match(line)
        if m:
            current = {"speaker": m[1].strip(), "text": m[2].strip(), "english": "", "page": page}
            result[part].append(current)
        elif current and line.strip():
            current["text"] += " " + line.strip()
    for turns in result.values():
        for turn in turns:
            turn["english"] = translations.get(re.sub(r"[^가-힣0-9]", "", turn["text"]), "")
    return result


def answers(text):
    # Keys can be on one line or wrap across lines. Numbered exercises are excluded.
    result = {}
    text = re.sub(r"\n\s*\*.*", "", text, flags=re.S)
    for m in re.finditer(r"(?<!\d)([1-9])\.\s*(.*?)(?=(?<!\d)[1-9]\.\s|\Z)", text, re.S):
        answer = " ".join(m[2].split()).replace("*", "").strip()
        if answer:
            result[int(m[1])] = answer
    return result


def exercises(text, keys, page):
    entries = []
    for m in re.finditer(r"^\s*([1-9])\.\s*(.*?)(?=^\s*[1-9]\.\s|\Z)", text, re.M | re.S):
        n = int(m[1])
        if n not in keys:
            continue
        prompt = " ".join(m[2].split()).strip()
        entries.append({"number": n, "prompt": prompt, "answer": keys[n], "page": page})
    assert len(entries) == len(keys), (page, len(entries), keys)
    return entries


def hide_key(text):
    return re.sub(r"Answer Key\s*\n.*?(?=\n\s*Applied Patterns|\Z)", "", text, flags=re.S).strip()


def section(sid, label, kind, text, page_list, turns=None, exercise_list=None, answer_text="", note=""):
    value = {"id": sid, "label": label, "kind": kind,
             "sourceText": "\n".join(s.strip() for s in text.splitlines()).strip(),
             "pages": page_list, "turns": turns or [], "exercises": exercise_list or [],
             "answerText": answer_text.strip(), "sourceNote": note}
    assert value["sourceText"] and page_list, sid
    return value


def extract(path, book_id):
    raw = subprocess.check_output(["pdftotext", "-layout", str(path), "-"], text=True)
    assert not re.search(r"[\u0590-\u05ff\u0a00-\u0aff]", raw), "Corrupt Hangul extraction"
    pages = raw.split("\f")
    if not pages[-1].strip():
        pages.pop()
    toc = []
    for text in pages[3:5]:
        for line in text.splitlines():
            m = re.search(r"Dialogue\s*#(\d+)\s+(.+?)\s+(\d+)\s*$", line)
            if m:
                toc.append((int(m[1]), m[2].strip(), int(m[3])))
    beginner = book_id == "ttmik-beginner"
    # Some beginner dialogues have two columns. Content-stream order preserves
    # the left column followed by the right, while -layout interleaves speakers.
    raw_pages = subprocess.check_output(["pdftotext", "-raw", str(path), "-"], text=True).split("\f") if beginner else pages
    assert len(toc) == (40 if beginner else 30)
    chapters = []
    for i, (number, title, start) in enumerate(toc):
        end = toc[i + 1][2] - 1 if i + 1 < len(toc) else len(pages) - 1
        category = GROUPS[next(j for j, threshold in enumerate(
            ([2, 4, 6, 8, 15, 20, 25, 27, 32, 35, 40] if beginner else
             [2, 4, 6, 8, 12, 16, 19, 21, 24, 27, 30])) if number <= threshold)]
        sections = []
        if beginner:
            short = beginner_turns(raw_pages[start], start + 1)
            long = beginner_turns(raw_pages[start + 2], start + 3)
            review = beginner_review(raw_pages[end - 1], end, short + long)
            assert len(short) == 2 and len(long) >= 8, (number, len(short), len(long))
            assert len(review["short"]) == 2 and len(review["long"]) >= 8, (number, review)
            vocab_culture = clean_page(pages[start + 1]).split("Cultural Tip", 1)
            assert len(vocab_culture) == 2
            last = clean_page(pages[end - 1])
            grammar_key, pron_key = last.split("Answer Key for grammar exercises", 1)[1].split(
                "Answer Key for pronunciation exercises", 1)
            gkeys, pkeys = answers(grammar_key), answers(pron_key)
            assert len(gkeys) == 4, (number, gkeys)
            grammar = clean_page(pages[start + 4])
            pron = clean_page(pages[start + 5])
            prefix, pron_body = pron.split("Pronunciation Points & Exercises", 1)
            spill_numbers = [int(n) for n in re.findall(r"^\s*([1-9])\.\s", prefix, re.M)]
            grammar_pages = [start + 5]
            if spill_numbers:
                grammar += "\n" + prefix
                grammar_pages.append(start + 6)
            pron = "Pronunciation Points & Exercises\n" + pron_body
            grammar_chunks = re.split(r"^\s*[AB]\.\s+", grammar, flags=re.M)[1:]
            assert len(grammar_chunks) == 2
            # Exercise spans end at the next A/B rule heading rather than swallowing it.
            gex = []
            for chunk in grammar_chunks:
                chunkkeys = {n: a for n, a in gkeys.items() if re.search(r"^\s*" + str(n) + r"\.\s", chunk, re.M)}
                gex += exercises(chunk[re.search(r"^\s*[1-9]\.\s", chunk, re.M).start():], chunkkeys, start + 5)
            assert {e["number"] for e in gex} == set(gkeys), (number, gex, gkeys)
            for e in gex:
                if e["number"] in spill_numbers:
                    e["page"] = start + 6
            pstart = re.search(r"^\s*1\.\s", pron, re.M).start()
            # A two-column rule heading can share the last exercise's line.
            # Keep the rule in sourceText, excluding it from that exercise prompt.
            pexercise_text = re.sub(r"(?<!\w)[ABC]\.\s+.*?(?=^[ \t]*[1-9]\.\s|\Z)", "", pron[pstart:], flags=re.M | re.S)
            sections = [
                section("short", "စကားပြောတို", "DIALOGUE", clean_page(raw_pages[start]), [start + 1], short),
                section("short-vocab", "ဝေါဟာရ · စကားပြောတို", "VOCABULARY", vocab_culture[0], [start + 2]),
                section("culture", "ကိုရီးယားယဉ်ကျေးမှု", "CULTURE", "Cultural Tip\n" + vocab_culture[1], [start + 2]),
                section("long", "စကားပြောရှည်", "DIALOGUE", clean_page(raw_pages[start + 2]), [start + 3], long),
                section("long-vocab", "ဝေါဟာရ · စကားပြောရှည်", "VOCABULARY", clean_page(pages[start + 3]), [start + 4]),
                section("grammar", "Grammar A/B နဲ့ လေ့ကျင့်ခန်း", "GRAMMAR", grammar, grammar_pages, exercise_list=gex, answer_text=grammar_key),
                section("pronunciation", "အသံထွက်နဲ့ လေ့ကျင့်ခန်း", "PRONUNCIATION", pron, [start + 6], exercise_list=exercises(pexercise_text, pkeys, start + 6), answer_text=pron_key),
                section("review-short", "Korean Only · စကားပြောတို", "REVIEW", "\n".join(t["speaker"] + ": " + t["text"] for t in review["short"]), [end], review["short"]),
                section("review-long", "Korean Only · စကားပြောရှည်", "REVIEW", "\n".join(t["speaker"] + ": " + t["text"] for t in review["long"]), [end], review["long"]),
            ]
        else:
            vocab_page = end - 5
            by_scene = {}
            scene = "dialogue"
            for p in range(start + 2, vocab_page, 2):
                ko = role_blocks(pages[p - 1], KO_ROLE)
                en = role_blocks(pages[p], EN_ROLE)
                assert len(ko) == len(en), (number, p, len(ko), len(en))
                for k, e in zip(ko, en):
                    if k["scene"]:
                        scene = "formal" if k["scene"] == "Formal Conversation" else "casual"
                    by_scene.setdefault(scene, []).append({"speaker": k["speaker"], "text": " ".join(k["lines"]),
                        "english": " ".join(e["lines"]), "page": p})
            for sid, turns in by_scene.items():
                label = {"formal": "ယဉ်ကျေးတဲ့ စကားပြော", "casual": "ရင်းနှီးတဲ့ စကားပြော"}.get(sid, "စကားပြော")
                sections.append(section(sid, label, "DIALOGUE", "\n".join(t["speaker"] + ": " + t["text"] + "\n" + t["english"] for t in turns), sorted(set(t["page"] for t in turns)), turns))
            sections.append(section("vocab", "ဝေါဟာရ", "VOCABULARY", clean_page(pages[vocab_page - 1]), [vocab_page]))
            # Retain source page boundaries while splitting the three original patterns.
            pattern_blocks = []
            current = None
            for p in range(vocab_page + 1, end + 1):
                for line in clean_page(pages[p - 1]).splitlines():
                    m = re.fullmatch(r"\s*Pattern ([123])\.\s*", line)
                    if m:
                        current = {"number": int(m[1]), "pages": [], "lines": [], "linePages": []}
                        pattern_blocks.append(current)
                    if current:
                        current["lines"].append(line)
                        current["linePages"].append(p)
                        if p not in current["pages"]:
                            current["pages"].append(p)
            assert len(pattern_blocks) == 3
            for block in pattern_blocks:
                source = "\n".join(block["lines"])
                extext, remainder = source.split("Exercises", 1)[1].split("Answer Key", 1)
                keytext = remainder.split("Applied Patterns", 1)[0]
                keys = answers(keytext)
                assert len(keys) == 2, (number, block["number"], keys)
                original_exercises = exercises(extext, keys, block["pages"][0])
                exercise_offset = source.index("Exercises") + len("Exercises")
                question_pages = {int(m[1]): block["linePages"][source[:exercise_offset + m.start()].count("\n")]
                                  for m in re.finditer(r"^[ \t]*([1-9])\.\s", extext, re.M)}
                for exercise in original_exercises:
                    exercise["page"] = question_pages[exercise["number"]]
                sections.append(section("pattern-" + str(block["number"]), "Grammar Pattern " + str(block["number"]), "GRAMMAR", hide_key(source), block["pages"],
                    exercise_list=original_exercises, answer_text=keytext))
        chapters.append({"number": number, "title": title, "category": category, "startPage": start,
                         "endPage": end, "sections": sections})
    return {"id": book_id, "title": "Real-Life Korean Conversations " + ("For Beginners" if beginner else "Intermediate"),
            "level": "Beginner" if beginner else "Intermediate", "version": "user-pdf-1",
            "pdfAsset": "books/" + book_id + ".pdf", "pdfSha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "pageCount": len(pages), "chapters": chapters}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--beginner", type=Path, required=True)
    parser.add_argument("--intermediate", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path("app/src/main/assets/books"))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    for path, bid in [(args.beginner, "ttmik-beginner"), (args.intermediate, "ttmik-intermediate")]:
        book = extract(path, bid)
        (args.output / (bid + ".json")).write_text(json.dumps(book, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
        shutil.copyfile(path, args.output / (bid + ".pdf"))
        print(bid, len(book["chapters"]), "chapters;", sum(len(s["turns"]) for c in book["chapters"] for s in c["sections"]), "dialogue turns;", sum(len(s["exercises"]) for c in book["chapters"] for s in c["sections"]), "exercises")


if __name__ == "__main__":
    main()
