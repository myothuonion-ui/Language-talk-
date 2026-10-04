# Language Talk v0.8.0

Practice opens with three choices: original book lessons, Live conversation, and a small daily lesson.
Daily lessons explain the Korean pattern, Myanmar meaning and two useful words before asking for an answer.
The existing seven checks sit inside Learn → Together → Independently. Assisted practice is preparation;
the later independent, transfer and checkpoint tasks still require an actual successful learner response.
Myanmar explanations are written. Generated speech contains Korean only.

## Connect your APIs

Me → Settings → AI & APIs. Tap a provider, enter your own key, choose a text model and save.
“Test text” performs a real structured text request, without creating a lesson or personal memory.
Model listing is separate from testing: a listed model is not a guarantee of account access.
Speech and Live are checked when those features are started.

Built-in profiles:

| Provider | Text endpoint | Credential |
| --- | --- | --- |
| Gemini | Google Gemini Interactions | Gemini key |
| NVIDIA | integrate.api.nvidia.com/v1/chat/completions | NVIDIA key |
| OpenAI | api.openai.com/v1/chat/completions | OpenAI API key |
| Claude | api.anthropic.com/v1/messages | Claude API key |
| DeepSeek | api.deepseek.com/chat/completions | direct DeepSeek key |

NVIDIA-hosted DeepSeek and Kimi use the NVIDIA key. Direct DeepSeek uses a different profile and key.
“Add API” supports additional named keys for a built-in provider and custom HTTPS endpoints.
Custom formats are OpenAI Chat, Claude Messages, or Gemini. The base URL includes the API version prefix;
the adapter appends the operation path. Redirects are not followed with credentials.
Keys are encrypted with Android Keystore, never packaged into the APK, and excluded from portable backups.

## Choose models by feature

The small Auto picker opens the current feature's choices. Settings also lists every task.

- Auto: one response; fallback occurs on failure.
- Two-AI review: another profile corrects the draft and returns the final JSON directly.
  No third Gemini finalization is required. If review fails, the original stays with an explicit unchecked status.
- Custom: choose primary profile/model, same-key backup models, and the order of other fallback profiles.

Choices can apply to this chat/app session or become the feature default. Future paid profiles are not
automatically used until “use as fallback” is enabled. Explicitly selecting a paid primary or reviewer authorizes
that request independently of its fallback switch.

Auto uses Gemini Flash-Lite for translation/dictionary, Gemini Flash for tutoring, then available NVIDIA models.
NVIDIA defaults include GLM 5.3 Flash and DeepSeek V4.1 Flash for short tasks, and GLM 5.3 / Kimi K3 for book
explanations. These are capability-based starting choices, not a measured Korean–Myanmar ranking.
The user can enter a different model ID or select one from the model list.

Missing/unsupported models, model rate limits, invalid structured replies, network failures and timeouts can
move to another model. Authentication failures and provider-wide quota errors skip that credential's remaining
models. Attempts and total duration are bounded; cancellation stops further calls.

## Speech and Live

Native Live uses Gemini Live models. If native connection setup fails, the app can use a hands-free pipeline:
recorded utterance → Gemini/OpenAI transcription → selected text AI → Gemini/OpenAI Korean speech.
OpenAI's file transcription and speech adapters are available when its key is added. Its native Realtime
socket is not implemented in this release. Text-only NVIDIA, Claude and DeepSeek models do not generate audio.
Custom OpenAI-compatible speech/transcription endpoints require explicit audio settings and matching model IDs.
When no audio service is available, use the text-conversation action.

Non-Gemini text brains receive a transcript, not the original audio. They are instructed not to judge
pronunciation from text. Book pronunciation tasks must retain an audio-capable evaluator.
My Context and finalized recent conversation turns are supplied again when a text provider changes.

## Guided conversation

Guided mode displays a Korean answer frame with slots and a Myanmar hint. Tap to reveal the matching complete
example. Open mode shows hints only on request. Independent application stages hide answers by default.
Personal facts are still saved only after an explicit remember request with verbatim evidence; textbook
and roleplay identities never become personal memory.

## Update and verification

Database version 4 adds answer scaffold fields and keeps migrations from versions 1–3.
Keep your source PDFs and export My Context before uninstalling an APK signed with an earlier CI key.
The source repository includes mocked provider HTTP/routing tests and Android UI/upgrade tests.
Automated contract tests do not establish your account's live access, latency or Korean–Myanmar quality.
