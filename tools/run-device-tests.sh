#!/usr/bin/env bash
set -uo pipefail
./gradlew connectedDebugAndroidTest --stacktrace
test_status=$?
# connectedAndroidTest removes its installed APKs and their app data, even on failure.
# Capture the UI separately and retain the original test failure status.
capture_test_status=0
    adb install -t app/build/outputs/apk/debug/app-debug.apk &&
        adb install -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk &&
        adb shell am instrument -w -r -e class com.myothuonion.languagetalk.PracticeScreensTest,com.myothuonion.languagetalk.LiveErrorScreenTest,com.myothuonion.languagetalk.BookCoursesTest,com.myothuonion.languagetalk.ReaderAndLearningTest,com.myothuonion.languagetalk.ReaderStudyUpgradeTest com.myothuonion.languagetalk.test/androidx.test.runner.AndroidJUnitRunner > ui-instrumentation.log
    capture_test_status=$?
    if [ "$capture_test_status" -eq 0 ]; then
        python3 -c 'from pathlib import Path; text = Path("ui-instrumentation.log").read_text(); print(text); assert "OK (9 tests)" in text, "UI capture test failed"'
        capture_test_status=$?
    fi
    if [ "$test_status" -eq 0 ] && [ "$capture_test_status" -eq 0 ]; then
        python3 tools/backup_legacy_context.py --output ci-phone-backup.json
        capture_test_status=$?
    fi
if [ "$test_status" -eq 0 ]; then test_status=$capture_test_status; fi
adb pull /sdcard/Android/data/com.myothuonion.languagetalk/files/screenshots ui-screenshots
capture_status=$?
if [ "$test_status" -eq 0 ]; then test_status=$capture_status; fi
exit "$test_status"
