# Language Talk v0.9.0 beta

This update connects PDF reading, written Myanmar teaching, Korean audio and deliberate speaking practice.

## Reader

- Swipe horizontally by default; choose vertical page swiping in reading settings.
- Long-press to select a word or passage. Selection itself stays offline.
- Highlight, underline, save a passage or add a note. Bold applies in Text / Study mode.
- Annotation rectangles follow the original PDF during zoom; extracted text has its own styling.
- Search the entire book, with progress and cancellation. The first search may need OCR; later searches reuse an on-device text index.
- Open page thumbnails, enter a page number, bookmark a page or export its original page image.
- Me → saved reader collections searches words, passages, notes and page bookmarks; each item links to its source.
- Practise a selected or saved passage directly. For a known built-in book, page practice can open the matching chapter/section.

## Explain an entire page

The sparkle action opens an inline teaching panel while the original page stays available.
Teaching separates overview, sentence meanings, vocabulary, grammar rules, cultural notes and exercises.
Exercise answers are hidden until selected. Additional tasks are labelled AI-created.
Gemini routes receive a compressed page image and extracted text; text-only routes use extracted text.
Cached explanations are available offline. New explanations require a configured API and network.

## Audio and speaking

Speaker actions replay Korean sentences and grammar examples.
Generated speech is AI audio, not the publisher's printed Track recording.
Use reading settings to import a matching original audio file for that page.
Requested generated speech is cached locally; original audio files remain on the device.

Book practice and guided Live/daily practice keep the current target after a successful answer.
Continue moves to the next task only when deliberately selected. Replay, explanations, unclear audio
and voice commands do not complete the task. Choose any book chapter/section without marking skipped material complete.
Unfinished chapter/section cursors are retained.

The microphone area shows real local input level and recording duration where available.
Recognized text appears only when returned. Local amplitude is not proof that AI understood the recording.
The REST voice pipeline returns recognized text after processing a submitted utterance.
Microphone capture and playback stop when their screen loses foreground lifecycle.

## Appearance and existing data

System / Light / Dark is saved in app settings and exported preferences.
Reader Original / Sepia / Night remains separate. Navigation and key actions use consistent glass icon styling.
Room migrates v4→v5 for waiting guided-practice state. Existing settings and learning metadata remain compatible.
Backups include annotations, page teaching, reading preferences, audio mappings and waiting lesson progress.
API keys, original imported PDF/audio bytes and generated speech caches are excluded. Keep the original files.

## Release scope

This is a beta release, not a claim that every generated explanation or real microphone scenario was verified.
The required CI checks include unit tests, lint, APK compilation and Android 10/15 migration, restore and screen tests.
Real provider quota, API access, Korean/Myanmar teaching quality, microphone acoustics and echo require account/device checks.
Use one protected release signing identity before a stable public distribution. The beta debug signing cache
helps subsequent beta builds but cannot recover earlier runner-generated signing keys.
