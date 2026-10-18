# Language Talk for Windows · v0.1.0 preview

A native Windows desktop package of Language Talk, with the selected Balanced layout adapted for a laptop screen. The Android app, app name and package are preserved. Desktop code lives under `desktop/`; Android code is unchanged.

## Download and install

Windows **10 or 11, x64** (including the user's Windows 11 Lenovo laptop). No Android emulator, Node.js or developer tools are required.

- [Setup installer](https://github.com/myothuonion-ui/Language-talk-/releases/download/windows-v0.1.0/Language-Talk-Setup-0.1.0-x64.exe)
- [Portable executable](https://github.com/myothuonion-ui/Language-talk-/releases/download/windows-v0.1.0/Language-Talk-Portable-0.1.0-x64.exe)
- [Checksums](https://github.com/myothuonion-ui/Language-talk-/releases/download/windows-v0.1.0/SHA256SUMS-Windows.txt)

Setup ကို download လုပ်ပြီး ဖွင့်ပါ။ Install folder ကို ရွေးနိုင်ပြီး admin account မလိုပါ။ Portable က install မလုပ်ဘဲ ဖွင့်နိုင်သည်။ ဗားရှင်းနှစ်ခုက တူညီသော Windows app ဒေတာကို သုံးသည်။ ဒီ preview က code-signing certificate မပါသေးလို့ Windows က unknown publisher ပြနိုင်သည်။

## စတင်သုံးခြင်း

1. **Books** — ပါပြီးသား Beginner/Intermediate စာအုပ်နှစ်အုပ်ကို ဖွင့်နိုင်သည်။ မူရင်းအခန်း ၇၀ ကို တိုက်ရိုက်ရွေးနိုင်ပြီး ကျော်သွားတာကို ပြီးပြီဟု မသတ်မှတ်ပါ။ ကိုယ်ပိုင် PDF ထည့်နိုင်သည် (တစ်ဖိုင် 80 MB အထိ)။
2. **Reader** — mouse နဲ့စာသားရွေးပြီး highlight၊ underline၊ note၊ save လုပ်နိုင်သည်။ Bold က Text mode မှာ ပြသည်။ စာအုပ်နဲ့ page progress ကို ပိတ်/ဖွင့်ပြီးလည်း မှတ်ထားသည်။ Keyboard ← / → နဲ့လှန်နိုင်ပြီး touch screen မှာလည်း လက်နဲ့လှန်နိုင်သည်။ Zoom နဲ့ Original/Sepia/Night ရွေးနိုင်သည်။
3. **Track** — စာမျက်နှာပေါ်က printed Track number ကို နှိပ်ပြီး မူရင်းအသံဖိုင်ကို ချိတ်ပါ။ `Track 01.mp3`, `002.mp3` စသည့် ဖိုင်များကို batch import လုပ်နိုင်သည်။ Track ကို စာအုပ်အလိုက်သီးခြားသိမ်းသည်။ ဖိုင်အမည်ထဲက နံပါတ်မရှင်းလျှင် Track နံပါတ်ကို ကိုယ်တိုင်ထည့်ပြီး page နဲ့ချိတ်ပါ။ Audio file တစ်ခု 48 MB အထိ။ Publisher Track အသံဖိုင်ကို ကိုယ်တိုင်ထည့်ရမည်။
4. **Me → AI & APIs** — ကိုယ့် Gemini/NVIDIA key ထည့်ပြီး သိမ်းပါ။ Models မှာ ကိုယ့် key က ရရှိတဲ့စာရင်းကိုယူပြီး model input မှာရွေးနိုင်သည်။ Text / PDF / Speech / Transcribe / Live သီးခြားစမ်းပါ။ Speech၊ transcription နဲ့ Live က Gemini ကိုသုံးသည်။ NVIDIA hosted text endpoint က အသံလုပ်ဆောင်ချက် မပါပါ။
5. **စာဖတ်ဖော်** — အဓိပ္ပာယ်၊ ဝေါဟာရ၊ Grammar၊ လေ့ကျင့်ခန်းကို တစ်ပိုင်းချင်း ရှင်းပြနိုင်သည်။ ရပြီးသားအပိုင်းကို local cache သိမ်းထားပြီး ကျန်အပိုင်းပျက်လည်း မပျောက်ပါ။ Source quote နှိပ်ပြီး မူရင်းနေရာကို ပြန်ကြည့်ပါ။ ကိုယ်ပိုင်မှတ်စုစာအုပ်ထဲ သိမ်းနိုင်သည်။ ပုံစကင် PDF တွေမှာ selectable text မရှိနိုင်ပါ။ Windows v0.1 မှာ OCR မပါသေးပါ။ Source quote တိကျမှု မစစ်နိုင်သော scan အချို့ကို AI ရှင်းပြချက်က ငြင်းနိုင်သည်။
6. **Practice** — နေ့စဉ်သင်ခန်းစာ ၄၈ ခု၊ မူရင်းစာအုပ်ဝါကျများ၊ open conversation၊ native Gemini Live နဲ့ သိမ်းထားတဲ့ဝေါဟာရပြန်လေ့ကျင့်မှု ပါသည်။ အဖြေမှန်လည်း Next ကို ကိုယ်တိုင်နှိပ်မှ ဆက်မည်။ Mic ဖြင့်ပြောရင် ကိုယ့်မူရင်းအသံကို API မခေါ်မီ သိမ်းပေးသည် (recording ဖွင့်ထားလျှင်)။ Text/transcript တစ်ခုတည်းအပေါ် အသံထွက်အမှတ် မတပ်ပါ။
7. **Replays** — မင်းအသံနဲ့ တကယ်ရခဲ့တဲ့ AI အသံကို session အလိုက် နားထောင်ပါ။ နှစ်ယောက်စလုံး/ငါ့အသံ/AI အသံ filter၊ 0.5–1.5× speed၊ ±5s၊ A–B repeat ပါသည်။ Transcript ကိုနှိပ်ရင် အဲဒီ turn အစကို ရောက်သည်။ အသံမသိမ်းခဲ့တဲ့အဟောင်းတွေကို ပြန်ဖန်တီးပြီး original ဟု မဖော်ပြပါ။ Continuous room recording မဟုတ်ဘဲ turn အလိုက်မှတ်တမ်းဖြစ်သည်။ Live ပြတ်တောက်တဲ့ AI turn အပိုင်းအစတချို့ မသိမ်းနိုင်သေးပါ။

## Local data and backups

App data is under Windows `%APPDATA%/Language Talk/`. API keys are separately encrypted by Electron safeStorage using the current Windows user's protected storage; no key is shipped in source, CI, backup or executable. Do not copy encrypted key files between Windows accounts. Enter your keys again on a new PC.

**Me → Export backup** creates a Windows JSON backup of saved progress, annotations, teaching, imported PDFs, Track files and paired replay audio. It excludes keys. Backup media is bounded to 180 MB (individual PDF 80 MB, recording clip 20 MB); reduce old data or export sessions when reaching the limit. Restore validates all file digests and references before replacing state. A pre-restore state file is preserved. Session export includes original recording bytes and transcripts.

Windows v0.1.0 has its own local data. It **does not automatically sync with Android and cannot import Android backup JSON yet**. The included book/curriculum data is reused, but annotations and progress start separately on the PC. Desktop backup cannot be restored into the Android app. Keep Android backups in the original app.

## Microphone and network

Use a headset for Live to reduce speaker echo. Windows Settings → Privacy & security → Microphone must allow desktop apps. Practice and Live show microphone/connection errors and do not count a failed audio request as success. Manual recordings stop after 90 seconds; Live stops after 20 minutes. Recording can be disabled in Me. Original textbook audio must be supplied by the learner; AI reference speech is explicitly separate.

Offline: bundled/imported books, tracks, annotations, notebook, cached teaching, course text and existing replays. Online: new AI teaching/replies, speech generation, transcription, model catalog and Live. API quota and model availability depend on the account/provider. Enter a currently listed model if a default is unavailable. There is no automatic cloud data sync or provider billing subscription included.

## Development and verification

```bash
cd desktop
npm ci
npm test
npm run test:ui
npm start
npm run build:win
```

The Windows Actions workflow runs persistence/backup/security/provider contract tests, then real Electron UI tests with bundled PDF text selection, highlights, microphone capture using a synthetic input, explicit Next, unlocked chapters, dark mode, actual WAV playback, speaker filters, playback speed and A–B repeat. It packages installer/portable targets, runs the same UI suite against the packaged Windows EXE, and publishes only after those pass. Tests use no real learner API keys or private recordings. Physical microphone quality and real provider account access remain device/account dependent.

The renderer is sandboxed with context isolation, no Node access, restrictive CSP, local packaged content, validated IPC operations and no remote navigation. PDF.js disables dynamic evaluation. Provider requests and encrypted secrets remain in the main process.
