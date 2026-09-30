#!/usr/bin/env bash
set -uo pipefail
./gradlew connectedDebugAndroidTest --stacktrace
test_status=$?
if [ "$test_status" -eq 0 ]; then
    python3 tools/backup_legacy_context.py --output ci-phone-backup.json
    test_status=$?
fi
adb pull /sdcard/Android/data/com.myothuonion.languagetalk/files/screenshots ui-screenshots || true
exit "$test_status"
