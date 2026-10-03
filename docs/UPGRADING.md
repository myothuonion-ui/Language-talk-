# Upgrading to v0.5.2

These APKs use runner-generated debug signing keys, so v0.5.2 can have a different certificate from an installed v0.4.0, v0.5.0 or v0.5.1.
Android rejects an in-place update when the certificates differ. Uninstalling the old app removes
its local chats, memories and encrypted API keys. Save your context first.

## If you have v0.5.0 or v0.5.1

1. Keep the old app installed and open **My Context → Export backup**. Save the JSON somewhere you can access after uninstalling. Check the saved file is nonempty.
2. Once the backup succeeds, uninstall the old app and install `language-talk-v0.5.2.apk` from [v0.5.2 Releases](https://github.com/myothuonion-ui/Language-talk-/releases/tag/v0.5.2).
3. Open **My Context → Restore backup** and select that JSON. Check your conversations, memories and recordings.
4. Re-enter your Gemini API key and press **Settings → Test**. This now tests actual text and voice access. If it reports a quota/access error, that provider issue needs to be resolved before voice can work.
5. Try Live. Native Live is checked at session start and can recover through Gemini text + speech.

Saved legacy Gemini model settings, including restored settings, automatically migrate to current defaults. Your custom voice preset and personal context are retained.

## Windows backup helper

1. Keep v0.4.0 installed on your phone.
2. Download `language-talk-legacy-backup-windows.exe` from
   [v0.5.2 Releases](https://github.com/myothuonion-ui/Language-talk-/releases/tag/v0.5.2).
   This is a Windows utility, not the Android app. It bundles Python and Android
   Platform Tools, so neither needs to be installed separately.
3. Enable Android **Developer options → USB debugging**. Connect your unlocked
   phone to your computer by USB and accept **Allow USB debugging** on the phone.
4. Open the helper and click **Save backup**. Choose a JSON filename. Wait for
   **Backup saved**, and check that the reported chat count matches your old app.
   If it fails, keep the old app installed and resolve the connection first.
5. Keep a copy of the JSON file. After a successful backup, remove v0.4.0 and
   install `language-talk-v0.5.2.apk`.
6. Copy the JSON file to your phone. Open **My Context → Restore backup** in
   v0.5.2 and select it. Check your restored conversations and memories.
7. Re-enter your API keys in Settings. Original imported photo/PDF/text files
   should be imported again if needed; their saved summaries are restored.

The helper stops Language Talk briefly to copy a consistent database. It never
installs, uninstalls or deletes phone data. It exports chats, messages, global/chat
memories, document summaries, and available v0.5.0 progress/recordings. It does not
export API key settings. Backups contain personal conversation text; keep your
JSON privately. The app import limit is 32 MB.

ADB backup requires the debuggable APK distributed by this repository. A release
signed by another distributor with debugging disabled cannot use `run-as`.
Some Windows phones need a manufacturer USB driver. See the official
[ADB connection instructions](https://developer.android.com/tools/adb).

## Python source alternative

Install Python 3.10+ and [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools),
add `adb` to PATH, and run the source helper from the repository:

```bash
python tools/backup_legacy_context.py
# Or without the window:
python tools/backup_legacy_context.py --output language-talk-backup.json
```

After v0.5.0, use the app's My Context export/restore controls. The project still
builds debug APKs with runner-generated keys. A privately maintained signing key
must be configured before future releases can promise direct Android updates.
The database migration itself preserves old data when the signing key matches;
emulator tests verify migration and JSON restore separately.
