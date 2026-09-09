# Language Talk AI

Premium Android language tutor for natural Korean and English practice. It combines Gemini Live hands-free conversation, Gemini voice, multimodal document understanding, persistent scoped memory, chat history, and optional NVIDIA Nemotron verification.

## What works

- Korean, English, or mixed-language tutor chats
- Hands-free Gemini Live audio-to-audio conversations with barge-in (speak while the AI is talking)
- Cinematic Listening / Thinking / Speaking orb and a transcript that can be shown or hidden
- Message Chat for typed messages and push-to-talk voice messages
- Gemini 3.8 Flash reasoning and Gemini 3.1 Flash TTS
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
2. Paste a Gemini API key and tap **Save settings**.
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

Gemini Live always uses `gemini-3.1-flash-live-preview` directly for low-latency native audio. The other brain modes apply to Message Chat; all non-live speech output still uses Gemini TTS.

## Development

Requirements:

- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35
- Gradle 8.10.2

The GitHub workflow installs the required Gradle version, generates a wrapper for the CI job, runs unit tests and lint, builds an installable debug APK, uploads it as a workflow artifact, and publishes it to the `v0.2.0` release.

```bash
gradle wrapper --gradle-version 8.10.2
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Privacy and safety

- Personal memories and chat history are stored locally in Room.
- API keys are encrypted locally with Android Keystore.
- Imported files are sent to Gemini only when the user imports them for analysis; a compact summary is stored locally for retrieval.
- Removing a memory or knowledge source removes it from future prompt context.
- This personal-client architecture sends API requests directly over HTTPS. For a public multi-user deployment, replace direct permanent keys with a backend and Gemini Live ephemeral tokens.

See [Architecture](docs/ARCHITECTURE.md) for implementation details.

## License

Private/personal project. No third-party voice may be cloned without the speaker's consent.
