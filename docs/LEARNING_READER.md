# Korean Coach and PDF reader — v0.7.1

Books / Practice / Me are the three main destinations. Model IDs, provider choices and custom prompts live under Me → Settings → Advanced.

## Read and translate

Import a PDF with the Android document picker. The app copies it into private storage (maximum 100 MB, 5,000 pages), deduplicates by SHA-256, and remembers its page and bookmarks. The two original TTMIK PDFs remain bundled.

Long-press a word on the page and drag to extend the selection. A Myanmar dictionary sheet opens inside the reader. It supports base forms, context-sensitive meanings, optional grammar, listening, sentence lookup and saving. Selected OCR text can be edited. The Translate toolbar also accepts manually entered text.

Korean and Latin OCR models are bundled. Recognition boxes are cached per opened page. Android 15+ additionally supplies native PDF text; older versions use OCR text. PDF layout, pinch zoom and Text mode are separate. Only Text mode changes font size. Day, Sepia and Night work in both modes.

The original PDF, bookmarks and cached dictionary results work offline. New meanings, assessments and uncached AI audio require the user's working Gemini key and network. Scan quality affects OCR. AI explanations and audio feedback are estimates; the app does not invent pronunciation percentages.

## Study

The original 70 TTMIK chapters retain their source sequence and controlled roles. The additional curriculum contains 48 ORIGINAL units across six stages: Hangul introduction, Beginner, Elementary, Intermediate, Upper-intermediate and Advanced. These are app learning stages, not official Sejong/CEFR/TOPIK certification.

Each unit practices listening comprehension, shadowing, guided response, role swap, response without hints, application to a new situation and an independent checkpoint. The app owns task order; generated next questions are ignored. Only an intelligible PASSED answer advances one step. RETRY stays; UNSURE, empty input and voice commands do not advance.

Practice → Start today selects a due review before the current unfinished unit. Daily goals are 10/20/30 minutes; actual time spent in the lesson screen is recorded. The level check is an adaptive app estimate with two tasks per stage; learners can choose a stage manually.

Mistakes and reader words become speaking review cards. Correct reviews return after 1/3/7/14/30/60 days; a mistake returns after 10 minutes. Factory, social, everyday and discussion topics are available. Settings control retained learner recordings; recordings can be played and deleted. Sample audio is clearly labelled AI audio, not human recordings.

Me shows completed task goals, saved words, corrections, recordings and previous conversations. Explicit personal profile and enabled global memories guide assessments. Fictional lesson identities are never saved as personal facts.

## Back up and upgrade

Me → My Context → Export backup saves prior chats/audio, book progress, the new learning progress, words, dictionary cache, reader page/bookmark metadata and daily study time. Old backups still load.

Imported PDF bytes and NEW Coach audio files are not embedded in JSON backups. Keep the original PDFs; reimporting the same file restores its reading position through the content hash. Built-in books need no reimport. New Coach attempt text remains in backup, with local audio references cleared.

CI APKs use runner-generated debug signing keys. If Android rejects an update, export backup before uninstalling, install the new APK, restore the JSON and re-enter the key. Keys are never included in exports, code or the APK.
