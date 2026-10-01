# ARISE for Android

Package `com.minimaldesigner.arise`. It uses Kotlin, Jetpack Compose, Room and Health Connect. There's no network code, and no INTERNET permission.

## Building

GitHub Actions is the usual compiler. A push to `main` or `dev` that touches `android/` runs `.github/workflows/android.yml`:

1. `:core:test`, `testDebugUnitTest` and `lintDebug`.
2. `assembleRelease`, signed with the key from the repo secrets. If the `ARISE_CERT_SHA256` variable is set, the workflow checks the certificate against it.
3. Uploads the `arise-apk` artifact (kept 14 days) and the Room schemas as `room-schemas`.
4. On a `v*` tag, it also creates a GitHub Release with the APK, attached twice: as `arise-<version>-<run>.apk`, and as `arise.apk` for the README's stable download link.

`versionCode` is the run number plus 100. `versionName` comes from `VERSION` in `app/build.gradle.kts`, plus `+<commit>` on untagged builds.

For local builds, open this folder in Android Studio or run `./gradlew assembleDebug`. You need JDK 21 and plenty of RAM (about 16 GB is comfortable).

When the Room schema changes, commit the new `app/schemas/.../N.json` from the `room-schemas` artifact straight away.

## Signing

Every update has to be signed with the same key. Android refuses an update signed with a different one, and the only way past that is an uninstall, which wipes the app's data. Keep the keystore and its passwords safe and backed up. See "Build it yourself" in the [main README](../README.md) for the secrets.

## Layout

- `core/`: pure Kotlin with no Android dependency. It holds the programs, `compute()` / `weekStats()` / `statusOf()`, the workout, missed-day, pause and free-pass rules, photos, reading, Health Connect maths, the backup format, and the nav geometry. Most of the tests live here.
- `app/`: the Compose UI, Room, DataStore, Health Connect and the notifications.
  - `ui/theme/` has the colour tokens (light and dark, 7 accents), the fonts and the type scale.
  - `ui/components/` has the card, panel, chips, orb, floating nav, sheets, glyphs and confetti.
  - `ui/screens/` has Home, Week, Food, Photos, Profile & settings, and the sheets.
  - `data/` has Room (`Db.kt`), the repositories, backups, Health Connect (including food and water) and reminders.
