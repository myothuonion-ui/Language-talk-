# Language Talk AI v0.8.0

Books / Practice / Me, Korean-only speech with written Myanmar teaching, guided answer frames, and flexible Gemini / NVIDIA / OpenAI / Claude / DeepSeek API connections.

- [Download APK](https://github.com/myothuonion-ui/Language-talk-/releases/download/v0.8.0/language-talk-v0.8.0.apk)
- [Release and complete source](https://github.com/myothuonion-ui/Language-talk-/releases/tag/v0.8.0)
- [New Practice and API guide](docs/AI_AND_PRACTICE.md)
- [Korean Coach and PDF reader guide](docs/LEARNING_READER.md)

48 original units complement the source-faithful 70 bundled TTMIK chapters. New translations, assessments and AI voice require your own configured key; books and cached lookups work offline. Back up from Me → My Context before upgrading. Keep original imported PDFs for reimport after reinstalling.

## Built-in books

**Books → သင်ခန်းစာ** includes both supplied TTMIK Real-Life Korean Conversations PDFs and 70 structured chapters in their original order. Dialogue turns follow the original book; vocabulary/grammar can be explained in Myanmar with your existing Gemini key. Progress and cached explanations are included in backup. See [the course guide](docs/BOOK_COURSES.md).

An Android Korean/English voice tutor with an editable personal profile, persistent learning state, guided speaking practice and Gemini voice output.

[Download the APK](https://github.com/myothuonion-ui/Language-talk-/releases/latest) · [Changes](docs/CHANGELOG.md)

## Practice

**Practice** opens with three choices, with book lessons and Live conversation first:

- **စာအုပ်သင်ခန်းစာ:** the original Beginner 40 and Intermediate 30 chapters, in book order, with role-controlled dialogue and Myanmar explanations.
- **Live စကားပြော:** Guided shows a Korean answer frame with slots and an optional example. Open follows your chosen topic, with help on request. Independent application hides answers by default; an assisted answer does not count as independent mastery.
- **ဒီနေ့ နည်းနည်းစီ:** a 5–10 minute lesson teaches the pattern, Myanmar grammar and two words first, then moves through supported practice, independent use, transfer and a checkpoint. The 48 original units cover six app stages; due speaking reviews and progress remain saved.

Generated speech uses Korean only. Myanmar meanings, explanations and corrections stay as written text. **Me** shows saved words, mistakes, progress and recordings.

Books opens at the saved page and supports PDF imports with dictionary lookup inside the reader. Practice offers the Korean Coach, book lessons and hands-free conversation. Me → My Context holds your profile, memories, documents, learning notes and backup controls. Me → Review & recordings keeps prior conversation reviews.

The initial editable profile focuses on Myanmar explanations and practical Korean for factory work and daily life, within 30 minutes a day. Change it to your own needs in My Context. Employer, shift and proficiency are not assumed.

## Voice and controls

Settings can save a default Gemini voice name, natural-language style, Slow/Natural pace, 0.8–4 second answer pause and spoken-correction preference. New conversation lessons inherit those defaults; Korean Coach also offers Slow/Natural controls within each unit. Each existing lesson has its own settings under Customize, including its goal and practice mode. Apply changes to restart an active voice session with the new configuration.

Voice commands include repeat, speak slowly, normal speed, explain in Myanmar and stay on this topic, including Korean/Myanmar equivalents. These commands do not advance lesson progress. Native Live passes the selected voice, supports barge-in and session resumption. Its lesson tool records one assessment per completed learner turn. Reliable REST/TTS voice uses local VAD and supports interruption; behavior depends on device echo cancellation and ambient noise.

The transcript can be hidden. Live shows the current sentence, practice stage, completed phrase count and a 30-minute session timer. Audio is used for qualitative pronunciation feedback; text-only requests never claim to assess pronunciation.

## Memory and recordings

Room stores chats, messages, scoped memories, source summaries, lesson progress and review items. Context selection prioritizes the current chat's memories and relevant documents rather than sending every document on every request.

Learning notes and recordings each have an off switch. Disabling automatic learning notes stops new notes and review entries; minimal lesson navigation state remains so guided practice can continue. Personal facts are remembered only after an explicit remember request with matching verbatim evidence, and never from roleplay. All memories and learning notes are visible, editable and removable. Recordings remain inside the app and can be replayed or deleted.

**Export** creates a portable JSON backup of conversations, memories, source summaries, progress, reviews, preferences and recordings. API keys and original imported documents are excluded. New Korean Coach progress, saved reader words, lookup cache and reader page/bookmark metadata are included; imported PDF bytes and new Coach audio files are excluded. Keep original PDFs for reimport after reinstalling. **Restore** validates the format and chat references, remaps IDs and adds restored conversations without deleting existing ones. The backup limit is 32 MB; delete old recordings to reduce its size.

## Providers

**Me → Settings → AI & APIs** stores your own encrypted keys. Built-in adapters support Gemini Interactions, NVIDIA's hosted Chat API, OpenAI Chat plus file transcription/speech, native Claude Messages, and direct DeepSeek. Add additional named keys or a custom HTTPS endpoint. No key is shipped or exported.

Each feature has an Auto picker. Choose a primary provider/model, same-key backups and an ordered list of other fallback providers, either for this chat/app session or as the feature default. Auto tries another model/provider after a failure. Independent two-AI review uses a separate profile to correct the draft; an unsuccessful review is labeled honestly. Paid API profiles join automatic fallback only after the user enables it.

Native Live uses Gemini. The hands-free voice pipeline can combine Gemini/OpenAI transcription, a chosen text AI, and Gemini/OpenAI Korean speech. Text-only NVIDIA, Claude and DeepSeek do not generate audio or assess pronunciation from a transcript. Book pronunciation checks use an audio-capable Gemini evaluator. Original finalized history and selected My Context are supplied again when the brain changes.

Test text makes a real structured request; Models only lists the catalog. Speech and native Live access are checked when started. Catalog listings do not establish quota or access. Models are editable, and custom task choices override built-in fast/quality defaults. See [AI setup and fallback behavior](docs/AI_AND_PRACTICE.md).

## Other tools

Quick Translate handles typed/spoken Korean, English and Myanmar. Korean Name Studio preserves the verified Myo Min Thu name spellings and local identity cards. Photo, PDF and text imports produce bounded summaries for practice context.

## Install and development

Android 8.0 or newer. Install the APK from Releases and enter your own API key. **Back up before reinstalling: debug APKs use runner-generated signing keys and can differ between versions.** In v0.5.0 or later use My Context → Export backup. For v0.4.0 use the Windows backup helper from Releases. Restore the JSON in Me → My Context after installing v0.8.0 and re-enter your API key. See [Upgrade instructions](docs/UPGRADING.md). Database v4 includes migrations from previous app databases and does not reset existing chats when the signing key matches.

Requirements: JDK 17, Android SDK 35, Gradle 8.10.2.

```bash
gradle wrapper --gradle-version 8.10.2
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
```

GitHub Actions runs unit tests, lint, APK compilation and Android emulator migration, backup restoration and screen checks on Android 10 and 15 before publishing v0.8.0. HTTP tests simulate Gemini and compatible provider requests, structured output, quota, timeout, cancellation, fallback, transcription and speech. CI does not make real provider calls because no personal API keys are supplied. Actual provider checks are documented in [Provider validation](docs/PROVIDER_VALIDATION.md). Microphone quality, pronunciation judgments and provider availability require device testing.

See [Architecture](docs/ARCHITECTURE.md).
