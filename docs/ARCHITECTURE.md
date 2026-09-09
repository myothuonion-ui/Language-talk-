# Architecture

## Modules

The app intentionally uses one Android application module while keeping package boundaries clear:

- `ui` — Compose screens, theme, and `AppViewModel`
- `data` — Room persistence, encrypted secrets, settings, and provider routing
- `network` — Gemini/NVIDIA REST clients and the Gemini Live WebSocket session
- `model` — tutor, voice, language, correction, and brain-mode contracts
- `util` — microphone recording and Gemini PCM/encoded-audio playback

## Conversation path

1. The learner starts a chat with a target language, level, topic, role, correction policy, voice, brain mode, and optional custom prompt.
2. The app retrieves recent messages, enabled global memory, matching per-chat memory, and enabled knowledge summaries.
3. `TutorRepository` builds a bounded system instruction so irrelevant history is not resent indefinitely.
4. The selected brain mode routes the request to Gemini, NVIDIA, or both.
5. A structured `TutorReply` is persisted, including the target-language answer, Myanmar translation, correction, explanation, and follow-up question.
6. When auto-speak is enabled, the final target-language text is synthesized only by Gemini TTS.

Gemini calls pass through a task-specific fallback router. The user-selected model is attempted first, followed by compatible known models. Authentication/authorization errors stop immediately; NVIDIA is never silently rerouted.

## Voice modes

Message Chat implements push-to-talk:

- Android records AAC audio in an MPEG-4 container.
- The audio is sent inline to Gemini with the current tutor context.
- Gemini understands the utterance and returns a structured lesson response.
- Gemini TTS returns audio, which is played through `AudioTrack` for raw 16-bit PCM or `MediaPlayer` for encoded formats.

This avoids sending microphone data through NVIDIA. NVIDIA is a text reasoning and verification provider only.

Gemini Live implements continuous audio-to-audio conversation:

- Android records mono PCM16 at 16 kHz in 100 ms chunks.
- Chunks stream to the configured Gemini Live model through the Live API WebSocket after setup completes.
- A setup deadline, server-error parsing, and compatible-model rotation prevent the interface from remaining indefinitely in a connecting state.
- Korean, Myanmar, and English transcription hints improve multilingual recognition.
- Automatic voice activity detection ends turns; start-of-activity interruption provides barge-in.
- Gemini audio streams back as 24 kHz PCM and plays with a low-latency `AudioTrack`.
- Interim/final input and output transcriptions drive the hideable transcript.
- Session resumption and sliding-window context compression keep long live conversations usable.
- Completed live turns are stored in the same chat history as message turns.

## Home tools

Korean Name Studio requests three pronunciation-oriented Hangul candidates through Gemini structured output. The client rejects Myanmar/non-Hangul values and enforces unique animals, palettes, light patterns, and layouts. Every identity card is rendered locally with Compose Canvas, so the feature consumes no image-generation quota. A normalized English prompt is attached for external image generators.

Quick Translate accepts typed text or recorded audio. Gemini returns a compact structured Myanmar meaning plus pronunciation, word breakdown, and grammar fields when useful. The Listen action routes only through Gemini TTS.

## Hybrid routing

- **Gemini Only:** one reasoning request.
- **Hybrid Auto:** Gemini first; Nemotron verification is used for long text and grammar, contract, law, or explanation-oriented prompts when an NVIDIA key is configured.
- **Best Quality:** Gemini and Nemotron run concurrently. Gemini receives both drafts and creates the final structured lesson.
- **NVIDIA Brain:** Nemotron produces text. Audio input still passes through Gemini for multimodal understanding. All output speech remains Gemini TTS.

## Persistence

Room tables:

- `chats`
- `messages`
- `memories` (`scopeChatId = null` for global memory; a chat ID for per-chat memory)
- `knowledge_sources`

Only the latest bounded chat window, enabled relevant memories, and enabled source summaries are placed in a model request. Global behavior is followed first, then the selected chat's behavior. The learner can disable or delete every memory source.

## Credentials

Keys are stored in a private SharedPreferences file after AES/GCM encryption with a non-exportable key created by Android Keystore. Keys are never logged, exported, backed up, or embedded in the APK source. A new Gemini key is validated before the encrypted stored key is replaced; removal deletes the encrypted preference entry.
