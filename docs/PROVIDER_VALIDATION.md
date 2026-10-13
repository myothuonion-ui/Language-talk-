# Provider validation for v0.10.0 — 13 October 2026

Temporary user-supplied Gemini and NVIDIA credentials were tested outside the repository. No credential or private learner audio is included in source, CI or APK. These are observed account results on the stated date, not future availability guarantees.

| Real request | Observed result |
| --- | --- |
| Gemini model catalog | HTTP 200; text, speech and native Live models available |
| NVIDIA model catalog | HTTP 200; hosted text models listed |
| Gemini Flash Lite tutor JSON | HTTP 200, 5.82 s; required fields and Korean greeting present |
| Actual PDF page 15, Gemini image + extracted Korean source | HTTP 200, 15.69 s; all page schema sections present, 6/6 exact source-linked sentences, two words and one grammar item |
| Actual PDF page 15, NVIDIA extracted Korean source | HTTP 200, 30.25 s; all sections present, 6/6 source-linked sentences, two words and one grammar item |
| Gemini Korean speech | HTTP 200; actual WAV audio, 8.02 s |
| Gemini recognition of that generated WAV | HTTP 200, 8.89 s; exact Korean sample recognized |
| Gemini native Live with production voice, VAD, transcription, resumption, compression and BLOCKING lesson-tool setup | Setup accepted; 63,842 output audio bytes plus transcription, 9.43 s |

The original Gemini Flash request returned temporary HTTP 503 rather than an invalid-key error. NVIDIA GLM short JSON took about 40–60 seconds. NVIDIA image input and a reasoning-enabled large structured request timed out. The repaired reader sends extracted text to text-only NVIDIA models, uses dedicated page JSON, and disables reasoning for the specific supported GLM/Nemotron routes. After this change the full Korean-source NVIDIA request succeeded in 30.25 seconds. The [official NVIDIA NIM guide](https://docs.nvidia.com/nim/large-language-models/2.0.10/get-started/advanced/get-started-nemotron-3.5-lightning.html) documents the per-request thinking control.

PDF checks used generic published textbook passages, not learner documents. Transcription used a generated sample, not the phone microphone. Original user/AI recording persistence, MediaPlayer playback, filters and UI are tested separately on Android. Physical microphone quality, echo cancellation and accent feedback still require the user's phone. Provider congestion can still produce a timeout; the app shows separate feature results and preserves completed PDF chunks and captured user audio.

## Earlier Gemini validation

# Gemini provider validation for v0.5.2

The reported v0.5.1 failure was reproduced against the Gemini API using a user-supplied key. The credential is kept outside the repository and public CI. Requests use generic test greetings; no personal conversation history is needed.

| Check | Observed result |
| --- | --- |
| Model catalog | HTTP 200; text, TTS and native Live models listed |
| Old TTS request with `delivery: inline` | HTTP 400: `Audio delivery mode is not supported.` |
| Same speech request with delivery omitted | HTTP 200, completed; a valid 24 kHz mono PCM16 WAV |
| Structured text | 3.8 Flash returned temporary HTTP 503; configured 3.7 Flash fallback returned valid tutoring JSON |
| Native Live setup | `setupComplete` received with the selected voice and learning tool |
| Native Live lesson tool | `update_learning_progress` called and a tool response accepted |
| Native Live audio output | Korean audio chunks and output transcription received |
| Native Live audio input | Synthetic 16 kHz learner audio plus silence transcribed as `안녕하세요. 잘 부탁드립니다.` |
| Native Live second turn | Spoken response received; two turns completed |

The WAV used by the Android playback test contains only the generic generated greeting. It contains no API key and ships only with the instrumentation test APK.

## Recheck the exact app requests

`ProviderContractTest` exports credential-free request fixtures from `GeminiClient` and `LiveProtocol` in the `build-test-reports` Actions artifact, under `provider-contract/requests.json`. This avoids keeping a second hand-written request schema as the only test evidence.

With Python 3.10+ and `websockets==16.0` installed, run locally:

```bash
python tools/check_gemini_provider.py requests.json --key-file /private/path/gemini-key --report provider-result.json
```

Alternatively set `GEMINI_API_KEY` locally. Never put the key in the source, APK, command-line arguments or public workflow. The script prints HTTP status, byte counts and test transcriptions; exceptions are redacted. It returns failure unless structured text, speech and two-turn native Live all pass. Its learner audio is synthetic, not a test of the user's microphone.

Provider availability, quota and mobile networking can change. Android checks separately verify WAV playback, visible retry controls, preference migration and backup restoration. The actual phone's microphone, speaker and network remain device-specific.
