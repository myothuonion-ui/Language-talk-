# Architecture

## Modules

The first release intentionally uses one Android application module while keeping package boundaries clear:

- `ui` — Compose screens, theme, and `AppViewModel`
- `data` — Room persistence, encrypted secrets, settings, and provider routing
- `network` — Gemini and NVIDIA REST clients
- `model` — tutor, voice, language, correction, and brain-mode contracts
- `util` — microphone recording and Gemini PCM/encoded-audio playback

## Conversation path

1. The learner starts a chat with a target language, level, topic, role, correction policy, voice, brain mode, and optional custom prompt.
2. The app retrieves recent messages, enabled personal memory, and enabled knowledge summaries.
3. `TutorRepository` builds a bounded system instruction so irrelevant history is not resent indefinitely.
4. The selected brain mode routes the request to Gemini, NVIDIA, or both.
5. A structured `TutorReply` is persisted, including the target-language answer, Myanmar translation, correction, explanation, and follow-up question.
6. When auto-speak is enabled, the final target-language text is synthesized only by Gemini TTS.

## Voice

The installable `v0.1.0` release implements push-to-talk:

- Android records AAC audio in an MPEG-4 container.
- The audio is sent inline to Gemini with the current tutor context.
- Gemini understands the utterance and returns a structured lesson response.
- Gemini TTS returns audio, which is played through `AudioTrack` for raw 16-bit PCM or `MediaPlayer` for encoded formats.

This avoids sending microphone data through NVIDIA. NVIDIA is a text reasoning and verification provider only.

## Hybrid routing

- **Gemini Only:** one reasoning request.
- **Hybrid Auto:** Gemini first; Nemotron verification is used for long text and grammar, contract, law, or explanation-oriented prompts when an NVIDIA key is configured.
- **Best Quality:** Gemini and Nemotron run concurrently. Gemini receives both drafts and creates the final structured lesson.
- **NVIDIA Brain:** Nemotron produces text. Audio input still passes through Gemini for multimodal understanding. All output speech remains Gemini TTS.

## Persistence

Room tables:

- `chats`
- `messages`
- `memories`
- `knowledge_sources`

Only the latest bounded chat window, enabled memories, and enabled source summaries are placed in a model request. The learner can disable or delete every memory source.

## Credentials

Keys are stored in a private SharedPreferences file after AES/GCM encryption with a non-exportable key created by Android Keystore. Keys are never logged, exported, backed up, or embedded in the APK source.
