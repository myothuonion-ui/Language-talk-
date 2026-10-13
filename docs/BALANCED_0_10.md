# Language Talk AI 0.10.0

App name, package ID and existing books remain the same. The selected Book & Voice symbol replaces the old icon. Balanced navigation connects **Books / Practice / Replays / Me**.

## API စမ်းသပ်ခြင်း

Me → Settings → AI & APIs → provider profile မှာ ကိုယ့် key ထည့်ပြီး သိမ်းပါ။ Models က စာရင်းသာ ဖတ်သည်။ Text / PDF / Speech / Transcribe / Live ခလုတ်များက သီးခြား request ပို့ပြီး အောင်မြင်မှု သို့မဟုတ် အကြောင်းရင်းကို ပြသည်။

Text checks structured tutor JSON. PDF checks extracted-text source JSON; the actual reader also sends its page image to supported models. Speech generates and plays Korean audio. Transcribe recognizes a generated Korean sample; it does not test the physical microphone. Live waits for actual Gemini socket audio. NVIDIA's text API does not provide Gemini native Live, speech or transcription.

Fast task defaults favor Gemini Flash Lite, with NVIDIA GLM Flash as the configured text route. Use Gemini for Myanmar PDF teaching; text-only NVIDIA receives extracted source text. Explicit model choices take priority. Slow replies have longer bounded timeouts and fallback. Authentication, quota, request format, congestion and timeouts have distinct reasons. No personal key is included in the APK, GitHub or backups.

## PDF ရှင်းပြချက်

Books မှ PDF ဖွင့်ပြီး စာမျက်နှာရှင်းပြချက်ကို နှိပ်ပါ။ အဓိပ္ပာယ် / ဝေါဟာရ / Grammar / လေ့ကျင့်ခန်း အလိုက် ကြည့်နိုင်သည်။ Source link မှ မူရင်းစာနေရာကို ပြန်ပြသည်။ တစ်ပိုင်းပျက်လျှင် ရထားသည့် အပိုင်းများ ဆက်ရှိပြီး ကျန်အပိုင်းကို ပြန်စမ်းနိုင်သည်။ ထပ်ထည့်ထားသော လေ့ကျင့်ခန်းများကို သီးခြားပြသည်။

Select a passage to ask a Myanmar question about that source. Save words, grammar and notes into the source-linked notebook. Due cards support review; successful answers wait for **Next**.

Android native extraction and Korean/Latin OCR provide selectable text. Poor scans can prevent source selection; pages without usable extracted text need a clearer PDF or a selectable passage. Missing quotations are not fabricated.

## Track အသံဖိုင်

စာမျက်နှာပေါ်က **Track 02** ကို နှိပ်ပါ။ မချိတ်ရသေးလျှင် သက်ဆိုင်ရာ မူရင်းအသံဖိုင် ရွေးပေးပါ။ နောက်တစ်ကြိမ် အဲဒီစာမျက်နှာရောက်လျှင် Track နံပါတ်နှိပ်ရုံဖြင့် offline နားထောင်နိုင်သည်။

Batch import accepts filenames such as `Track 01.mp3`, `Track 02.mp3` or `001.mp3`. Ambiguous filenames with multiple numbers are skipped. If the printed number is unreadable, enter it manually to link the current page. Track numbers are scoped to each book. Files are copied into app storage, with a 48 MB limit per file.

Publisher audio must be supplied by the learner. AI speech is not presented as an original textbook Track.

## နားထောင် → လိုက်ပြော → ယှဉ်နားထောင်

ကိုရီးယားဝါကျ ရွေးပြီး လိုက်ပြောခြင်း ဖွင့်ပါ။ AI reference ကို နားထောင်၊ ကိုယ့်အသံ ဖမ်းပြီး ရပ်၊ နှစ်ခုကို ယှဉ်နားထောင်နိုင်သည်။ အသံအကဲဖြတ်နိုင်သော Gemini model မှ ကြားရသည့် စာသားနှင့် pronunciation feedback ရယူသည်။ စာသားတစ်ခုတည်းကနေ အသံထွက်အမှတ် မတပ်ပါ။ Microphone permission လိုပြီး တစ်ကြိမ် 90 စက္ကန့်အထိ ဖမ်းနိုင်သည်။

## Replays

Me → Settings မှ အသံမှတ်တမ်းဖွင့်ထားလျှင် Practice / Live / shadowing မှ ကိုယ့်အသံနှင့် AI ၏ တကယ်ထွက်လာသော အသံကို စကားဝိုင်းအလိုက် သိမ်းသည်။ API ပျက်လည်း ဖမ်းပြီးသား ကိုယ့်အသံ ဆက်ရှိသည်။

- **နှစ်ယောက်စလုံး / ငါ့အသံ / AI အသံ** filters
- **0.5× / 0.75× / 1× / 1.25×** speed, normal pitch
- Seek slider, five-second skips and transcript-to-turn playback
- Set **A**, move forward and set **B** for repeated playback
- Session ZIP with original saved audio and transcript
- Source page link for reading sessions

Filters play saved turns in order. Transcript links seek to the start of a turn, without word-level timing. Silence between turns is not preserved as a continuous room recording. Old unsaved AI audio cannot be recovered; text-only entries are labeled without audio.

## Update / backup

Database 5→6 adds replay tables and Track page links without resetting chats, annotations or progress. My Context → Export includes replay metadata and audio. Each audio entry has a 4 MB limit and the full JSON a 32 MB limit; use session ZIP for longer recordings. Keep imported PDFs and original Tracks separately: JSON stores their links, not document/Track bytes. Use in-app backup for new replays; the Windows legacy helper is intended for older conversation databases.

Use the GitHub release APK for updates. CI reuses main's development signing identity while its cache exists; losing that cache can require backup/reinstall. This is a development APK, not a Play Store release.

Android emulator checks cover migration, actual encoded audio playback, speaker controls, source anchors and screens. Physical microphone quality, echo cancellation and accent feedback depend on the phone/provider. Live account checks are recorded in [Provider validation](PROVIDER_VALIDATION.md).
