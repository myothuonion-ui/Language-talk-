# Built-in Korean conversation courses (v0.6.0)

The user-supplied editions of TTMIK Real-Life Korean Conversations are bundled as original PDFs and verified, page-linked course JSON. Beginner has 40 chapters and 332 pages; Intermediate has 30 chapters and 376 pages. No document import or document summarization is needed for these courses.

## Source fidelity

`tools/build_book_courses.py` uses Poppler with layout extraction for study sections and content-stream extraction for the beginner dialogues that use two columns. It preserves the formal/casual split in Intermediate chapter 2, multiple speakers and grouped responses. Korean Only review is extracted from its own page: Beginner Taxi splits one response into two turns there. Beginner chapters 4, 5, 27 and 36 continue grammar exercise 4 on the next page; their grammar section references both pages. Footnotes remain in the displayed answer key but are excluded from the expected learner answer.

All 70 original chapter numbers/page ranges, vocabulary/culture/grammar/pronunciation sections, 507 original exercises and their answer keys are included. Original PDF hashes are checked during extraction and source-file opening. Source references remain visible in the lesson interface.

To regenerate from the same supplied PDFs:

```bash
python3 tools/build_book_courses.py --beginner /path/beginner.pdf --intermediate /path/intermediate.pdf
python3 -m unittest discover -s tools -p 'test_*.py' -v
```

## Study and conversation

Home → My Books opens the two courses. Beginner is the default; the learner can choose the course level. Chapters within each book unlock in order. Previous chapters can be reviewed, and the complete original PDF can be read at any time.

Beginner follows short dialogue → vocabulary → cultural tip → long dialogue → vocabulary → grammar/exercises → pronunciation/exercises → Korean Only short/long review. Intermediate follows the original dialogue scenes → vocabulary → patterns 1–3, with exercises and applied patterns.

`BookEngine` owns the exact current source line and learner role. The assistant speaks only the other characters' source lines until the next learner turn. Gemini transcribes/assesses an attempt and explains in Myanmar; its proposed next line and generated spoken text are ignored. Empty/uncertain attempts, failed answers and repeat/pace/explain commands never advance the cursor. Role changes explicitly restart the current section. Questions and answer keys are separate; review answers are hidden until requested. Chapter completion means studying the book, not independent speaking mastery. At the end of a chapter, the application-practice button opens the existing tutor with that chapter's grammar and the learner's explicit personal context.

Hands-free book practice uses the already supported record/transcribe/assess/TTS transport so dialogue order can be enforced. It inherits the global voice/style/silence settings and has per-book Slow/Natural pace. General native Live remains available outside book courses. Playback/API failures preserve the current attempt, and reconnecting repeats the saved assistant prompt.

## Storage and backup

Course/section/turn/role, completed chapters, actual transcripts/corrections and up to 100 review mistakes per book are saved in a separate DataStore. Textbook identities never enter personal memory. Myanmar explanations are generated on demand with the learner's configured key and cached for later offline reading; they are identified as AI explanations rather than an official Myanmar edition. A key/network is needed for uncached explanations and voice. Original book reading, exact typed answers and cached explanations work offline.

Existing portable backup also exports/restores course progress and cached explanations. Restore validates the book edition/cursor before changing existing chat data. No provider keys are embedded in course assets or backup. Book-specific source data does not change the existing Room schema or conversation state.

## Verification

Python checks cover PDF hashes, chapter/section coverage, cross-page exercises, the two-column overtime dialogue, formal/casual scenes, multiple/grouped speakers, answer-key separation and clean Hangul. JVM checks cover authoritative turns, retry/uncertainty/command handling, role changes, chapter gates and invalid restore states. Android checks exercise packaged PDF rendering, an exact offline learner answer, course advancement, persistence/backup and book screenshots. Live microphone/uncached provider behavior still depends on the learner's device and API access.
