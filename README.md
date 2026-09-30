# ARISE

A native Android tracker for **75 Hard**, **75 Soft** or a **custom challenge** you design yourself. It's built with Kotlin and Jetpack Compose, in a black-and-white "Nothing phone" style with one accent colour.

Everything stays on your phone. There's no account, no ads and no analytics.

## Features
- **Challenges:** 75 Hard (miss a task and restart at Day 1), 75 Soft, or Custom. For Custom you pick from 12 preset tasks or add your own, choose a length of 30–100 days, and decide whether a miss means a restart.
- **Home:** a Today ring with one arc per task, your day and streak, this week's score, today's tasks, recent workouts, and a sleep panel.
- **Tasks with a bit of help:**
  - a workout sheet (type, minutes, outdoors)
  - a Read sheet that remembers your book and page
  - a Meditate sheet that opens your meditation app
  - the photo task ticks itself when you take a progress photo
- **Missed days (75 Hard):** restart from Day 1, or use one of **3 free passes per attempt** when you did the work but forgot to tick it.
- **Pause:** name a break ("Bali trip") and choose how long it lasts. ARISE stays quiet until it's over, then welcomes you back to a fresh Day 1.
- **Week:** a task × day grid with scores, workout minutes and pages read.
- **Photos:**
  - camera or gallery, with front, side and back angles
  - photos grouped by the week of each attempt
  - a before/after slider with pinned photos
- **Health Connect (read-only):** weight, height, workouts, sleep, steps and mindfulness. Workouts are offered as picks and are never ticked automatically.
- **Reading log:** pages per day and books finished.
- **Evening reminder:** one notification, and only if tasks are still open.
- **Backup and restore:** everything, including photos, goes into one zip.
- **Appearance:** light, dark or system, with 7 accent colours, dot-matrix or mono numbers, and confetti with haptics when you clear a day.
- **Food (optional):** if you run your own [arise-food](https://github.com/minimal-designer/arise-food) server, a Food tab shows calories, macros, meals and weigh-ins. Without one, Food stays hidden.

## Install
Download the APK from the [latest release](https://github.com/minimal-designer/arise/releases/latest) on your phone and open it. The first time, Android will ask you to allow your browser to install apps.
Every release is signed with the same key, so a new version installs over the old one and keeps your data.

It needs Android 10 (API 29) or newer. Health Connect needs its app (built into Android 14 and later).

## Privacy
- Challenge data, photos and settings live only in the app's private storage on your phone. Backups go wherever you save the zip.
- Health Connect is read-only, and only for the data types you allow.
- The only network access is the optional food sync, and it goes to the server you set up.

## Build it yourself
GitHub Actions builds, tests and signs the app (`.github/workflows/android.yml`). To build your own signed APKs in a fork:

1. Make a keystore: `keytool -genkeypair -v -keystore arise.jks -alias arise -keyalg RSA -keysize 4096 -validity 10000`
2. Add the repo secrets `ARISE_KEYSTORE_B64` (`base64 -w0 arise.jks`), `ARISE_KEY_ALIAS`, `ARISE_STORE_PASS` and `ARISE_KEY_PASS`.
3. This step is optional. Add the repo variable `ARISE_CERT_SHA256` with your certificate's SHA-256 (lower case, no colons), and CI checks every APK against it.
4. Push to `main` or `dev`. The signed APK is the run's `arise-apk` artifact. A `v*` tag also publishes a release.

To build locally, open `android/` in Android Studio, or run `./gradlew assembleDebug` inside `android/`. You need JDK 21 and about 16 GB of RAM. See [android/README.md](android/README.md) for the project layout.

`scripts/arise-install.sh` downloads a CI or release APK, checks its package and signature, and installs it over wireless adb.

## Food sync (optional)
[arise-food](https://github.com/minimal-designer/arise-food) is a small self-hosted service that serves a food log to ARISE. It comes with sample data to try. Once it's running, open **Profile & settings → Data → Food log**, enter its address and token, then tap **Test connection** and **Save**.

## License
[MIT](LICENSE). The bundled Doto and Geist fonts are under the SIL Open Font License ([android/FONTS-LICENSE.txt](android/FONTS-LICENSE.txt)).

ARISE is an independent project. It isn't affiliated with or endorsed by the 75 Hard program or its creators.
