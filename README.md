# Language Talk AI v0.6.0

## Built-in books

Home → **My Books** includes both supplied TTMIK Real-Life Korean Conversations PDFs and 70 structured chapters. Dialogue turns follow the original book; vocabulary/grammar can be explained in Myanmar with your existing Gemini key. Progress and cached explanations are included in backup. See [the course guide](docs/BOOK_COURSES.md).

An Android Korean/English voice tutor with an editable personal profile, persistent learning state, guided speaking practice and Gemini voice output.

[Download the APK](https://github.com/myothuonion-ui/Language-talk-/releases/latest) · [Changes](docs/CHANGELOG.md)

## Practice

- **Guided Practice:** introduce one useful sentence, repeat it, then use it independently in a new situation. Move forward only after the independent attempt. Five useful phrases lead to review.
- **Roleplay:** practice with a coworker, manager, friend or other chosen character while staying in the selected situation.
- **Free Talk:** follow your chosen topic and correction preference.

Home continues the most recent lesson. Practice offers Message Chat and hands-free Live. Review contains due phrases and saved recordings. My Context holds your profile, memories, documents, learning notes and backup controls.

The initial editable profile focuses on Myanmar explanations and practical Korean for factory work and daily life, within 30 minutes a day. Change it to your own needs in My Context. Employer, shift and proficiency are not assumed.

## Voice and controls

Settings can save a default Gemini voice name, natural-language style, Slow/Natural pace, 0.8–4 second answer pause and spoken-correction preference. New lessons inherit those defaults. Each existing lesson has its own settings under Customize, including its goal and practice mode. Apply changes to restart an active voice session with the new configuration.

Voice commands include repeat, speak slowly, normal speed, explain in Myanmar and stay on this topic, including Korean/Myanmar equivalents. These commands do not advance lesson progress. Native Live passes the selected voice, supports barge-in and session resumption. Its lesson tool records one assessment per completed learner turn. Reliable REST/TTS voice uses local VAD and supports interruption; behavior depends on device echo cancellation and ambient noise.

The transcript can be hidden. Live shows the current sentence, practice stage, completed phrase count and a 30-minute session timer. Audio is used for qualitative pronunciation feedback; text-only requests never claim to assess pronunciation.

## Memory and recordings

Room stores chats, messages, scoped memories, source summaries, lesson progress and review items. Context selection prioritizes the current chat's memories and relevant documents rather than sending every document on every request.

Learning notes and recordings each have an off switch. Disabling automatic learning notes stops new notes and review entries; minimal lesson navigation state remains so guided practice can continue. Personal facts are remembered only after an explicit remember request with matching verbatim evidence, and never from roleplay. All memories and learning notes are visible, editable and removable. Recordings remain inside the app and can be replayed or deleted.

**Export** creates a portable JSON backup of conversations, memories, source summaries, progress, reviews, preferences and recordings. API keys and original imported documents are excluded. **Restore** validates the format and chat references, remaps IDs and adds restored conversations without deleting existing ones. The backup limit is 32 MB; delete old recordings to reduce its size.

## Providers

Enter your Gemini key in Settings; add/test/replace/remove controls are available. Test runs a short structured text request and a short speech request, and shows Ready only when both succeed. Native Live access is checked when a Live session starts; a model catalog listing is not proof of voice access or remaining quota. NVIDIA is optional. Keys are encrypted locally using Android Keystore and are not committed or exported.

- **Gemini Only:** native Live with learning tools, or structured Gemini REST + Gemini TTS recovery.
- **Hybrid Auto:** Gemini tutoring with configured NVIDIA verification when useful.
- **Best Quality:** Gemini and configured NVIDIA review, then Gemini final structured response.
- **NVIDIA Brain:** configured NVIDIA reasoning with Gemini multimodal understanding/final formatting and Gemini speech.

NVIDIA-enabled voice sessions use the REST/TTS path so the selected brain mode is honored. Gemini uses the Interactions API for text, audio understanding and speech. Model selection paginates the available catalog and uses only listed compatible modern fallbacks; NVIDIA stays on the exact configured model. Provider model names are editable. New defaults use Gemini 3.8 Flash, Live and Flash TTS; saved legacy Gemini 1.x/2.x and 3.1 preview preferences migrate to modern defaults. Compatible modern custom choices, voice presets and personal context are preserved. Gemini 3.8 speech style uses metadata so instructions are not read aloud. Audio delivery is left at the provider default because explicitly requesting inline delivery is rejected by TTS.

## Other tools

Quick Translate handles typed/spoken Korean, English and Myanmar. Korean Name Studio preserves the verified Myo Min Thu name spellings and local identity cards. Photo, PDF and text imports produce bounded summaries for practice context.

## Install and development

Android 8.0 or newer. Install the APK from Releases and enter your own API key. **Back up before reinstalling: debug APKs use runner-generated signing keys and can differ between versions.** In v0.5.0 or later use My Context → Export backup. For v0.4.0 use the Windows backup helper from Releases. Restore the JSON in My Context after installing v0.6.0 and re-enter your API key. See [Upgrade instructions](docs/UPGRADING.md). Database v3 includes migrations from previous app databases and does not reset existing chats when the signing key matches.

Requirements: JDK 17, Android SDK 35, Gradle 8.10.2.

```bash
gradle wrapper --gradle-version 8.10.2
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
```

GitHub Actions runs unit tests, lint, APK compilation and Android emulator migration, backup restoration and screen checks before publishing v0.6.0. HTTP tests use simulated Gemini catalog, tutoring, speech and quota responses. CI does not make real Gemini/NVIDIA calls because no personal API keys are supplied. Actual provider checks are documented in [Provider validation](docs/PROVIDER_VALIDATION.md). Microphone quality, pronunciation judgments and provider availability require device testing.

See [Architecture](docs/ARCHITECTURE.md).
