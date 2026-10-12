# Language Talk 0.9.0 — Reader and practice update

Reader: choose horizontal swipe or vertical continuous reading in Reading appearance. At normal zoom a sideways swipe turns one page. Pinch to zoom and pan; double tap returns to fit. Hold and drag Korean/English text to select it. Selection opens inline tools, keeping the book visible.

Highlight, underline, Bold, notes and saved passages are persisted in Room database v5, keyed to the document fingerprint and page. Bold is shown in Text/Aa mode. Original PDF font weights are preserved. The saved collection in Books/Me searches passages, notes, source titles, existing saved words and page bookmarks. Each entry opens its source page. Deleting an imported PDF retains study metadata; reimporting identical PDF bytes reconnects it.

The sparkle action explains the current page in Myanmar, with source-linked sentence cards, vocabulary, grammar, printed exercises and separately labelled added practice. Hints and answers are initially hidden. Source-linked output is validated before it is cached, including exact quotations and coverage of extracted blocks. Tap Source to highlight its original region. Cached explanations are available offline. New explanations and uncached speech require the user's configured provider; model availability and language quality depend on that account. Korean/English OCR is supported; image-only Myanmar text is not supported by the bundled OCR.

Per-line speakers use cached Korean AI speech. Slow/Natural and Stop are available. A printed Track number alone is not an audio file. Import your audio and assign its Track number in the reader menu to play it offline. Backup saves these mappings; source PDF/audio bytes and speech-cache bytes are not included in the metadata JSON.

Book chapters and sections can be chosen freely. Moving between sections saves unfinished cursors, including a passed response waiting for Next. Skipped chapters are not marked completed. Correct responses enable Next and stay on the current turn until it is pressed. Non-exercise sections require an explicit review acknowledgment. Daily guided practice and guided chat also wait for Next; replay/explanation/commands do not advance them. Open conversation remains free conversation.

Book microphone feedback displays actual local capture level and elapsed recording time. The recognized transcript appears only after the provider responds. Level movement alone is not proof of AI recognition. No numerical pronunciation scores are manufactured.

System/Light/Dark app appearance is persisted independently of Original/Sepia/Night reader appearance. Extended backup includes annotations, cached page explanations, imported audio mappings, reader settings, pending guided progress and section cursors; API keys remain excluded.

## Installation and validation

Back up first in Me → My Context → Export backup. Keep imported PDF/audio source files. The earlier 0.8.1 release used a newly generated CI debug signing key, so Android may reject an in-place upgrade. Do not uninstall until you have exported the data. Restore the JSON after reinstalling and re-enter your API keys if necessary.

This is a development update (0.9.0), not a v1.0 production claim. Automated tests exercise progress gating, source validation, migrations, backup round trips, OCR selection, swipe navigation and settings. Real noisy-microphone and provider-account checks still require the user's device and credentials. Publisher audio is not bundled by this update.
