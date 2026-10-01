# ARISE

**A challenge tracker for Android: 75 Hard, 75 Soft, or a challenge you design yourself.**
It's free and works offline. There's no account, no ads, and it has no internet permission at all.

<p align="center">
  <a href="https://github.com/minimal-designer/arise/releases/latest/download/arise.apk"><b>⬇&nbsp;&nbsp;Download ARISE for Android (APK)</b></a>
</p>

<p align="center">
  <img src="docs/download-qr.svg" width="164" alt="QR code: scan with your phone to download ARISE">
  <br><sub>On a computer? Scan this with your phone's camera.</sub>
</p>

## Install

1. **On your Android phone**, tap the download link above, or scan the QR code.
2. **Open** the downloaded `arise.apk`, from the download notification or the Files app.
3. If Android asks, **allow your browser (or Files) to install unknown apps**, then go back.
4. Tap **Install**. If Play Protect says the app is unknown, tap **More details → Install anyway**. ARISE isn't on the Play Store, so Android hasn't seen it before.

You need **Android 10 or newer**. Health Connect is optional: it's built into Android 14 and later, and on older phones it's a free app from the Play Store.

**Updating:** download the latest APK the same way and install it over the old one. Every release is signed with the same key, so your challenge, photos and settings stay. You can see all versions and their changes on the [releases page](https://github.com/minimal-designer/arise/releases).

## What it does

- **Challenges:** 75 Hard (miss a task and restart at Day 1), 75 Soft, or Custom. For Custom you pick from 12 preset tasks or add your own, choose a length of 30–100 days, and decide whether a miss means a restart.
- **Home:** a Today ring with one arc per task, your day and streak, this week's score, today's tasks, recent workouts and a sleep panel.
- **Tasks with a bit of help:**
  - a workout sheet (type, minutes, outdoors)
  - a Read sheet that remembers your book and page
  - a Meditate sheet that opens your meditation app
  - the photo task ticks itself when you take a progress photo
- **Missed days (75 Hard):** restart from Day 1, or use one of **3 free passes per attempt** when you did the work but forgot to tick it.
- **Pause:** name a break ("Bali trip") and choose how long. ARISE stays quiet, then welcomes you back to a fresh Day 1.
- **Week:** a task × day grid with scores, workout minutes and pages read.
- **Photos:**
  - camera or gallery, with front, side and back angles
  - photos grouped by the week of each attempt
  - a before/after slider with pinned photos
- **Food:** calories, protein, carbs, fat and every meal, against goals you set, read from **Health Connect**. Log food in any app that writes to it (MyFitnessPal, Cronometer, Samsung Health…), and the Food tab appears once you let ARISE read it.
- **Health Connect (read-only):** weight, height, workouts, sleep, steps, mindfulness, food and water. Workouts are offered as picks and are never ticked automatically.
- **Reading log, evening reminder,** and a **backup and restore** of everything (photos included) in one zip.
- **Appearance:** light, dark or system, with 7 accent colours, dot-matrix or mono numbers, and confetti with haptics when you clear a day.

## Privacy

- ARISE has **no internet permission**, so it can't send anything anywhere.
- Your challenge, photos and settings live only in the app's private storage. Backups go wherever you save the zip.
- Health Connect access is read-only, and only for the data types you allow.

---

## For developers

The app is in [`android/`](android/README.md) (Kotlin, Jetpack Compose, Room, Health Connect). Most of the logic is plain Kotlin in `android/core` and has unit tests.

**CI** (`.github/workflows/android.yml`) tests, lints and builds a signed APK on every push to `main` or `dev`. A `v*` tag publishes a release with the APK, as `arise-<version>-<run>.apk` and as `arise.apk`.

**Signing your own builds in a fork:**
1. Make a keystore: `keytool -genkeypair -v -keystore arise.jks -alias arise -keyalg RSA -keysize 4096 -validity 10000`
2. Add the repo secrets `ARISE_KEYSTORE_B64` (`base64 -w0 arise.jks`), `ARISE_KEY_ALIAS`, `ARISE_STORE_PASS` and `ARISE_KEY_PASS`.
3. This step is optional. Add the repo variable `ARISE_CERT_SHA256` with your certificate's SHA-256 (lower case, no colons), and CI checks every APK against it.

**Local builds:** open `android/` in Android Studio, or run `./gradlew assembleDebug` there. You need JDK 21 and plenty of RAM.

`scripts/arise-install.sh` downloads a CI or release APK, checks its package and signature, and installs it over wireless adb.

## License

[MIT](LICENSE). The bundled Doto and Geist fonts are under the SIL Open Font License ([android/FONTS-LICENSE.txt](android/FONTS-LICENSE.txt)).

ARISE is an independent project. It isn't affiliated with or endorsed by the 75 Hard program or its creators.
