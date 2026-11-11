#!/usr/bin/env bash
# Executed exclusively on a clean GitHub Actions Android emulator.
# Uses disposable synthetic keystores; never connects to a user's device.
set -euo pipefail

adb install qa-upgrade/old-A-301.apk
adb install qa-upgrade/test.apk

adb shell am instrument -w -r -e class org.openarchive.telegram.SignedUpgradeSentinelTest -e upgradePhase seed org.openarchive.telegram.qa.previewstable.test/androidx.test.runner.AndroidJUnitRunner | tee qa-upgrade/seed.txt
grep -q "OK (1 test)" qa-upgrade/seed.txt

# Android should reject the replacement signed by an unrelated key while
# retaining the previously installed app's private DB, notes and media.
if adb install -r qa-upgrade/new-B-302.apk > qa-upgrade/rejected-on-device.txt 2>&1; then
    echo "ERROR: device accepted incompatible cert"
    exit 1
fi
grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE" qa-upgrade/rejected-on-device.txt

adb shell am instrument -w -r -e class org.openarchive.telegram.SignedUpgradeSentinelTest -e upgradePhase verify org.openarchive.telegram.qa.previewstable.test/androidx.test.runner.AndroidJUnitRunner | tee qa-upgrade/retained-after-reject.txt
grep -q "OK (1 test)" qa-upgrade/retained-after-reject.txt

# A properly signed newer APK of exactly the same package should upgrade
# in place and leave the synthetic user data intact.
adb install -r qa-upgrade/new-A-302.apk
adb shell am instrument -w -r -e class org.openarchive.telegram.SignedUpgradeSentinelTest -e upgradePhase verify org.openarchive.telegram.qa.previewstable.test/androidx.test.runner.AndroidJUnitRunner | tee qa-upgrade/retained-after-update.txt
grep -q "OK (1 test)" qa-upgrade/retained-after-update.txt

printf 'PASS: mismatched signature rejected, private data preserved; matching signature upgraded, private data preserved\n'
