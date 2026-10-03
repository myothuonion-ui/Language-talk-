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
