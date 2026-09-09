# Language Talk AI

Premium Android language tutor for natural Korean and English practice. It combines Gemini Live hands-free conversation, Gemini voice, Korean Name Studio, Quick Translate, multimodal document understanding, persistent scoped memory, chat history, and optional NVIDIA Nemotron verification.

## What works

- Korean, English, or mixed-language tutor chats
- Hands-free Gemini Live audio-to-audio conversations with barge-in (speak while the AI is talking)
- Futuristic A.R.I.A Live Core HUD with Listening / Processing / Speaking states and a transcript that can be shown or hidden
- Live setup timeout, visible diagnostics, multilingual speech hints, reconnection, and Gemini Live model fallback
- Message Chat for typed messages and push-to-talk voice messages
- Gemini 3.8 Flash reasoning and Gemini 3.1 Flash TTS
- Gemini model auto-fallback for Message Chat, tools, TTS, documents, and Live
- Gemini key add, test, atomic replace, and remove controls (an invalid new key never overwrites a working key)
- Korean Name Studio: validates Hangul-only output and produces three sound-oriented Korean spellings
- Three quota-free local Canvas identity cards with guaranteed-distinct palette, animal, light pattern, and layout
- A copyable English prompt on each name card for use in external image generators
- Quick Translate for typed or spoken Korean/English/Myanmar into Myanmar meaning, pronunciation, words, and grammar
- Optional Hybrid Auto, Best Quality, and NVIDIA Brain modes
- NVIDIA Nemotron 3 Ultra text reasoning/verification
- Global behavior and memory shared across every conversation
- Per-chat topic, level, tutor role, correction mode, behavior, memory, brain mode, and voice
- Gemini voice presets for Korean, English, and EPS listening style
- Persistent chats and messages with Room
- Editable personal memories
- Image, PDF, and text document import into the context vault
- Local encrypted API-key storage backed by Android Keystore
- Dark/light premium Jetpack Compose UI

## Install

Download the latest APK from [Releases](https://github.com/myothuonion-ui/Language-talk-/releases/latest), allow installation from your browser or file manager, and install it on Android 8.0 or newer.

On first launch:

1. Open **Settings**.
2. Paste a Gemini API key and tap **Add key** (or **Replace** when changing it). Use **Test** to verify it and **Remove** to delete it.
3. Optionally paste an NVIDIA API key to unlock hybrid brain modes.
4. Keep **Gemini Only** for the fastest and simplest setup.
5. Open **Chat**, then choose **Message Chat** or **Gemini Live**.

API keys are never committed to the repository. They are entered by the user and encrypted at rest with a non-exportable Android Keystore key.

## AI routing

| Mode | Reasoning | Speech output |
|---|---|---|
| Gemini Only | Gemini 3.8 Flash | Gemini 3.1 Flash TTS |
| Hybrid Auto | Gemini; Nemotron checks complex text | Gemini TTS |
| Best Quality | Gemini + Nemotron; Gemini composes final | Gemini TTS |
| NVIDIA Brain | Nemotron text reasoning | Gemini TTS |

NVIDIA is optional. The complete app continues to work with only a Gemini key.

Gemini first uses the model entered in Settings. If that Gemini model is unavailable, unsupported, rate-limited, or returns an invalid structured/audio result, the app moves through the current compatible fallback chain and shows the active model. Invalid credentials never trigger fallback. NVIDIA always uses exactly the configured model and has no automatic fallback.

Gemini Live starts with `gemini-3.1-flash-live-preview` by default and can fall back to `gemini-2.5-flash-native-audio-preview-12-2025`. All non-live speech output uses Gemini TTS only.

## Development

Requirements:

- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35
- Gradle 8.10.2

The GitHub workflow installs the required Gradle version, generates a wrapper for the CI job, runs unit tests and lint, builds an installable debug APK, uploads it as a workflow artifact, and publishes it to the `v0.3.0` release.

```bash
gradle wrapper --gradle-version 8.10.2
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Privacy and safety

- Personal memories and chat history are stored locally in Room.
- API keys are encrypted locally with Android Keystore.
- Identity-card animals and effects are drawn locally with Compose Canvas; Korean Name Studio does not call an image-generation model or consume image quota.
- Imported files are sent to Gemini only when the user imports them for analysis; a compact summary is stored locally for retrieval.
- Removing a memory or knowledge source removes it from future prompt context.
- This personal-client architecture sends API requests directly over HTTPS. For a public multi-user deployment, replace direct permanent keys with a backend and Gemini Live ephemeral tokens.

See [Architecture](docs/ARCHITECTURE.md) for implementation details.

## License

Private/personal project. No third-party voice may be cloned without the speaker's consent.
