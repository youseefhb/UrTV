# UrTV: matches, local wallpapers, and two featured apps

Branch: `feature/urtv-matches-wallpapers-two-apps`, based on local branch `work`.
Repository: https://github.com/youseefhb/UrTV

## Behavior

The home hero has exactly two featured slots, IPTV Smarters and YouTube. Other installed apps remain in the all-apps grid below. Settings, inputs, Wi-Fi, screensaver, and the optional screensaver card remain accessible. The optional card is below the hero so it does not cover the new fixtures.

Smarters is discovered from installed launch activities using their labels and package names, rather than a fixed package. A TV launch activity is preferred; a normal launch activity is used when needed. Multiple matches or an absent match require a choice in **تخصيص**. No arbitrary alternative or store page is launched. YouTube is detected independently; SmartTube and YouTube Music are not substituted automatically. Long-press a featured tile to customize, or long-press an app in the grid to assign it to a slot. The old `DockActivity` settings entry opens the new customization screen. Saved legacy favorites are left in preferences, but do not populate the two fixed roles. `dock.sh set PKG1,PKG2` assigns the slots in Smarters/YouTube order; `reset` restores automatic discovery. Existing hide/show controls still affect the all-apps grid.

**تخصيص → اختيار صورة أو عدة صور** opens Android's system document picker. Select up to 20 images; a single image remains fixed. Supported intervals: 1, 5, 10, 15, 30, or 60 minutes. Selecting new images replaces the previous selection. Images are imported into private app storage, downsampled to bound memory use, corrected for JPEG EXIF orientation, and saved independently of document-provider URI grants. Input limit: 20 MiB per image. Unreadable files are skipped; if none can be imported the old selection remains. Selection and interval survive process death/reboot, but uninstalling the app removes them. The reset button restores UrTV's bundled/hosted backgrounds. A TV without a compatible document picker needs a remote-compatible document-provider/file-manager app.

## Match data and limits

Uses the existing project's public TheSportsDB v1 API and shared free key `123`, with no paid account, subscription, or embedded private credential:

- `eventsnext.php?id=136013` (Al-Hilal)
- `eventsnext.php?id=133667` (AC Milan)
- `lookupteam.php?id=...` (team badges)

Each club cycles through up to three upcoming fixtures, sorted by kickoff, every 10 seconds. The next fixture is visible first. Team names, badges, competition, and kickoff are shown. Times are converted from the source's UTC/offset timestamps to `Asia/Riyadh`, independently of the TV timezone. A date without a time is labeled **الوقت غير محدد**, never invented as midnight. Started, finished, cancelled, abandoned, and postponed fixtures are excluded until the source supplies a confirmed future event.

Refresh is attempted when needed on home resume and every 30 minutes while home is visible. Cached fixtures are shown immediately, with last-update time; failures preserve the last successful response. Expired fixtures are filtered even offline. Badges are cached, with an icon placeholder when unavailable.

TheSportsDB is a community-maintained source, not an official league feed or a guaranteed live service. Coverage, cross-competition fixtures, badge availability, publication delays, shared-key limits, and the source's future free-access policy can affect results. Names/competition may remain in the source language. Empty data is labeled as unavailable, not treated as proof the club has no scheduled matches. No paid fallback is enabled.

**Live verification limitation:** this environment's outbound proxy rejects TheSportsDB with HTTP 403. Live payloads and real match coverage could not be verified here. The endpoints were already used by UrTV; the new integration still needs an online TV check. No fabricated fixture data is bundled.

## Android 7 and build

Launcher minimum SDK is now 24; screensaver minimum remains 21. `getChangedPackages`/`ChangedPackages` are used only inside an API >= 26 branch. On every resume the launchable app list is rebuilt, so customization and package changes are picked up on Android 7. `String.join` in launcher helpers is replaced by `android.text.TextUtils.join`, also needed on API 24. No new library dependencies.

With JDK 17+, Android SDK platform/build-tools 34, and Gradle 8.9:

```sh
python3 src/build.py
gradle -p android :launcher:assembleDebug :app:assembleDebug
```

Outputs:

- `android/launcher/build/outputs/apk/debug/launcher-debug.apk`
- `android/app/build/outputs/apk/debug/app-debug.apk`

Both debug APKs use the same local debug signing identity so the signature-protected screensaver configuration provider works. Installing over an existing APK requires the **same signing certificate** and a suitable version code. These locally signed APKs cannot replace production-signed installs without the original signing key. Do not uninstall the existing TV app merely to bypass a signature mismatch; uninstalling loses its private settings.

For a signed release using the existing repository setup:

```sh
# Set ANDROID_KEYSTORE and ANDROID_KEYSTORE_PASSWORD securely to the original signing identity.
# Set GITHUB_RUN_NUMBER to an integer greater than the currently installed versionCode.
python3 src/build.py
gradle -p android :launcher:assembleRelease :app:assembleRelease
```

Release outputs are under each module's `build/outputs/apk/release/`. No deployment, installation, push, or release publication was performed.

## Validation

```sh
python3 tests/home_features_test.py
gradle -p android :launcher:lintDebug
```

The standalone Java rules tests cover UTC/offset parsing, Saudi midnight rollover, unspecified/invalid dates, exclusion of non-upcoming statuses, identifying Smarters with an unknown package name, ambiguity/manual selection, removal of saved apps, and avoiding YouTube Music/SmartTube substitution. `tests/run.sh` includes them.

Lint has two pre-existing distribution errors: `MissingLeanbackLauncher` (this app is a HOME launcher) and `ExpiredTargetSdkVersion` (target 30 is retained for the existing Wi-Fi behavior). Neither is an API 24 availability error. The current Gradle configuration does not fail builds on lint errors. Inspect the report rather than treating build success as a clean lint report.

Before installing as the default home on a real API 24 TV, verify:

1. Cold launch/resume and app install/removal do not raise `NoSuchMethodError`.
2. D-pad navigates both featured slots, top controls, and all-apps grid; OK launches the selected activity; Back returns from grid/settings; long-press opens customization.
3. Both actual clubs load online with logos and Riyadh dates/times; offline resume retains valid cached data; unavailable data shows its status.
4. One and multiple local images work, including large and EXIF-rotated photos; interval/selection survive app restart and TV reboot; reset restores defaults.
5. Old screensaver settings, optional home card, Wi-Fi, inputs, and other apps still work.

No Android emulator or connected TV was used for runtime testing in this environment.
