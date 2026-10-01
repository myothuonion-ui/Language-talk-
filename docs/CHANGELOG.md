# v0.5.1 — Gemini access and voice fixes

- Fixes the new-user unavailable Gemini 2.5 error by migrating saved legacy model preferences, including settings restored from backups.
- Replaces Gemini REST generateContent calls with fresh, non-stored Interactions requests; prior dialogue and personal context remain in Room.
- Uses the Gemini 3.8 TTS request schema, with style in speech metadata and exact spoken text separate. Requests inline WAV and sends recorded M4A with the supported MIME type.
- Paginates the model list, filters by task capability, and removes legacy/unlisted fallback attempts. Quota and access errors remain visible.
- Settings Test now actually requests structured text and speech. It reports voice readiness only when both succeed, with native Live checked at session start.
- Extends the native Live handshake allowance and switches to reliable text + voice when no compatible Live model is listed or Live access is denied.
- Adds HTTP regression coverage and an Android preferences-file migration test; checks the API Settings screen as well as backup/restore and practice screens.

Real provider access, quota and microphone behavior must be checked with Settings → Test and a Live session on the user's device. Back up before reinstalling an APK with a different debug signing key; see [Upgrade instructions](UPGRADING.md).

# v0.5.0 — Personal Korean voice practice

- Adds Guided Practice, Roleplay and Free Talk. Guided progress moves through introduction, repetition, independent use and review; repetition alone does not count as mastery.
- Saves lesson state, bounded learning notes, corrections and due review phrases in Room. Existing v0.4.0 databases migrate without dropping chats or memories.
- Adds editable learner profile, global/chat memories and learning notes. Explicitly remembered real facts require matching transcript evidence; roleplay facts are excluded.
- Fixes raw Live setup generation configuration and passes the selected voice. Adds adjustable answer silence, session resumption, context compression and conversation restoration.
- Live calls a bounded learning-progress tool and receives the authoritative next step. Selected NVIDIA modes use the structured REST tutor and Gemini TTS.
- Reliable voice supports interruption with local VAD and platform echo cancellation. Threshold behavior still depends on the microphone, speaker and ambient noise.
- Adds saved voice presets, Slow/Natural pace, correction speech, configurable answer pauses, and applying changes to active sessions.
- Adds voice recordings, replay/deletion and portable JSON backup/restore. Backups include recordings and context, exclude API keys, and merge into existing data.
- Reorganizes Home, Practice, Review, My Context and Settings. Live displays the current sentence, stage, phrase count and a 30-minute session timer.
- Adds JVM regressions, an Android v2-to-v3 migration test, recording/backup restoration test and emulator screenshots of the main screens.
- Adds a Windows legacy backup helper because the published v0.4.0 APK uses a different debug certificate. Save context before reinstalling; API key settings are excluded.

Live provider calls are not exercised by credential-free CI. Pronunciation feedback uses actual audio and is qualitative; no fabricated numerical pronunciation score is shown.
