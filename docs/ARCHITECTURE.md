# Architecture

## Tutor state

`PracticeEngine` owns the deterministic INTRO → REPEAT → APPLY → REVIEW transitions. A successful repeat is distinct from independent use. Retry, uncertain audio and voice commands never count as mastery. Guided state is persisted separately from personal facts. Roleplay/Free Talk keep their conversation mode rather than enforcing repetition steps.

The REST tutor sends structured assessment fields to `TutorRepository`, which saves progress, bounded notes and due review items. Native Live calls `update_learning_progress`; the app returns the authoritative next state before the tutor gives feedback. The model supplies qualitative assessment, while the application owns progression and limits.

## Voice

`LiveProtocol` builds the raw WebSocket setup: generationConfig/voice, answer-silence configuration, interruption, transcription, resumption and context compression. Completed conversation turns are stored locally. Reconnects use the latest server resumption handle, or replay bounded recent turns as conversation content. Native Live uses Gemini; selected NVIDIA modes use the structured REST/TTS transport.

Reliable voice records local VAD utterances without Send, applies the same repository tutor path and plays Gemini TTS. Microphone capture continues during playback for barge-in with device echo cancellation. No NVIDIA speech provider or silent NVIDIA fallback is used.

## Context and persistence

Room database v3 stores chats, messages, memories, knowledge sources, learning_progress and review_items. Migration 1→2 preserves scoped memory; migration 2→3 adds practice/voice/recording fields and learning tables without destructive migration.

`ContextSelector` excludes other-chat and disabled memories, prioritizes scoped context and ranks matching source summaries. Real personal facts need an explicit remember request and matching transcript evidence; verbatim evidence is saved instead of an unverified model paraphrase. Roleplay facts are excluded. Learning notes and raw audio each have separate controls. Recording filenames are generated inside app-private storage; external backup paths are never trusted.

`BackupStore` exports public preference fields and the learning database, including bounded recording data. It never reads SecretStore. Restore validates format, IDs and references before writing, uses a database transaction, remaps chat IDs, cleans up staged recordings on rollback, and merges without deleting current data. Original imported documents are not embedded; their analyzed summaries are portable.

## Interface and validation

Home focuses on continuing a lesson. Practice retains typed and hands-free modes. Review shows due phrases and recordings. My Context exposes all stored facts/notes, profile and backup controls. Voice presets are editable defaults plus per-chat overrides; changes refresh active sessions.

JVM tests exercise progression, scoped retrieval, memory evidence, backup validation, Live protocol configuration and audio headers. Android tests verify v2 database upgrade, scoped backup/recording restoration and the rendered main screens. Live provider calls and real microphone behavior require user keys and hardware and are not claimed as CI-tested.
