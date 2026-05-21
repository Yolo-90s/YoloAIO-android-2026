# YoloAIO

A multi-feature Android lifestyle app built end-to-end with Jetpack Compose and Firebase. Eleven independent modules sit on a shared backend — auth, chat, music, movies, weather, wallpapers, ringtones, quotes, an audio trimmer, a video PlayGround, and an educational Wi-Fi Lab — wrapped in a Material 3 theme system with seven color palettes.

Distributed via Firebase App Distribution to a small group of testers; not on the Play Store. Reasons in [Distribution & limitations](#distribution--limitations).

---

## Table of contents

- [Features](#features)
- [Tech stack](#tech-stack)
- [Architecture overview](#architecture-overview)
- [Project layout](#project-layout)
- [Setup](#setup)
- [Firestore configuration](#firestore-configuration)
- [Firestore security rules](#firestore-security-rules)
- [Building & releasing](#building--releasing)
- [Distribution & limitations](#distribution--limitations)
- [Known caveats](#known-caveats)

---

## Features

### Identity & social
- **Auth** — email/password + Google Sign-In via Credential Manager (`GetSignInWithGoogleOption` primary, legacy `GetGoogleIdOption` fallback).
- **Chat** — 1:1 conversations with gradient outgoing bubbles, glass incoming bubbles, message grouping (consecutive same-sender messages cluster with shared timestamp), date separators, GIF + emoji + location attachments, and a tappable header avatar that routes to a user profile screen.
- **User profile screen** — name, email, join date, and "last known location" (auto-populated by a throttled background presence write — see [LocationPresence](app/src/main/java/com/example/yoloaio/data/LocationPresence.kt)).
- **Voice / video calling** — native Jitsi Meet SDK launched from chat. Server URL is Firestore-configurable (`config/app.jitsiServerUrl`) so you can point at `meet.jit.si`, a community instance, or self-hosted Jitsi without rebuilding.
- **Community Channel** — open group chat readable + writable by every signed-in user.

### Media
- **Music** — JioSaavn-backed streaming. Foreground service with `MediaStyle` notification keeps audio alive across screens. Beat-reactive circular FFT visualizer around the album art. Settings screen for audio quality (96 / 160 / 320 kbps) and language preferences. Cast support via the Google Cast SDK.
- **Movies & TV** — TMDB-driven browsing with bento hero layout, genre filters, seasons + episodes, watch progress synced to Firestore. (Playback path: see caveats.)
- **PlayGround** — two modes: a Drive-backed video library (lists + streams from a private Google Drive folder via a Vercel serverless proxy) and an in-screen WebView browser that auto-loads a Firestore-configurable URL.
- **Wallpapers** — Unsplash search + set-as-wallpaper + favorites + scheduled rotation (`WorkManager`).
- **Ringtones** — Freesound API search + install as ringtone / notification / alarm.
- **Audio Trimmer** — pick a song from JioSaavn or local files, trim, preview, save to `MediaStore.Music`.

### Utilities
- **Weather** — OpenWeatherMap-driven current conditions, hourly forecast (next 24h, 3h resolution), 5-day daily forecast, sun arc visualization, condition-aware animated backdrops (sun, clouds, rain, thunderstorm, snow, mist, stars).
- **Quotes** — personal + community feed with public/private visibility, custom backgrounds (solid color / gradient / Unsplash image), font + alignment controls.
- **Wi-Fi Lab** — educational concept screen visualizing the WPA-PSK 4-way handshake, a PBKDF2 cost estimator that uses real Java crypto, WPA2 vs WPA3 comparison, and the user's own current Wi-Fi info. Reads scan results only; performs no real attack.

### App-wide
- **Theme system** — 7 curated color palettes (Aurora, Ember, Mint, Cobalt, Bloom, Sage, Neon) + an animated 3-blob background that drifts behind glass surfaces. Adaptive dark mode.
- **Settings** — Profile editing, change password (with Google-only-account detection), privacy preferences, palette picker, PlayGround URL, in-app update checker.
- **In-app updates** — Firebase App Distribution SDK shows an "Update Available" dialog automatically when a new APK is pushed; manual check via Settings → About → Check for updates.

---

## Tech stack

| Layer | What |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Build | AGP 8 + Gradle Kotlin DSL |
| Min / target SDK | 24 / 36 |
| Backend | Firebase (Auth, Firestore, Storage) |
| Image loading | Coil |
| Navigation | androidx.navigation:navigation-compose |
| Media playback | `android.media.MediaPlayer` + `MediaSessionCompat` + foreground service |
| Audio analysis | `android.media.audiofx.Visualizer` (FFT) |
| Cast | `play-services-cast-framework` + custom Compose-only `CastButton` |
| Video | `android.widget.VideoView` (PlayGround) + Jitsi Meet Native SDK (calling) + WebView (Movies player + Browser mode) |
| Location | `android.location.LocationManager` (no Play Services dep) |
| Background work | `WorkManager` (wallpaper rotation) |
| Crypto | `javax.crypto` for PBKDF2 visualization + DES decryption of JioSaavn URLs |

---

## Architecture overview

- **Single-activity app.** [`MainActivity`](app/src/main/java/com/example/yoloaio/MainActivity.kt) hosts the entire Compose tree; the only other activities are SDK-supplied (Jitsi `JitsiMeetActivity`, Firebase App Distribution `InstallActivity`).
- **Feature packages** under `com.example.yoloaio.features.*` — each feature is self-contained (model + repository + screen). No cross-feature dependencies except via shared `data/` and `ui/` packages.
- **Firestore config-driven**. API keys + behaviour flags live in a single Firestore document `config/app` that's pulled into a `CompositionLocal` ([LocalAppConfig](app/src/main/java/com/example/yoloaio/data/AppConfig.kt)). Changing a feature flag or key in the Firebase Console takes effect on every device within seconds, no rebuild.
- **Background presence**. Every time the user opens the app (`MainActivity.onResume`) or a chat, [`LocationPresence`](app/src/main/java/com/example/yoloaio/data/LocationPresence.kt) writes their last known coordinates into their own user doc (throttled to once per 5 min). Drives the "Active now" ring on chat headers + the location card on user profile screens.

---

## Project layout

```
app/src/main/java/com/example/yoloaio/
├── MainActivity.kt
├── cast/                      Compose-only Cast button + CastManager singleton
├── data/                      AppConfig, FirebaseModule, UserSession, presence, update checker
├── features/
│   ├── audio/                 Audio trimmer (MediaExtractor + MediaMuxer)
│   ├── auth/                  AuthRepository (sign in/up/Google/password)
│   ├── chat/                  Conversations, calls, profile, community
│   ├── community/             Community Channel (group chat)
│   ├── home/                  Home bento grid
│   ├── movies/                TMDB browsing + Vidking WebView player
│   ├── music/                 JioSaavn client, player, settings, visualizer
│   ├── quotes/                Quote editor + viewer + community
│   ├── ringtones/             Freesound + ringtone installer
│   ├── settings/              Settings screens + preference stores
│   ├── videos/                PlayGround (Drive library + WebView browser)
│   ├── wallpaper/             Unsplash + wallpaper installer + rotation worker
│   ├── weather/               OpenWeatherMap + animated backdrops
│   └── wifi/                  Wi-Fi Lab educational screens
├── navigation/                Routes + AppNavGraph
├── notifications/             Channels + chat notification listener
└── ui/
    ├── components/            FeatureScaffold, GlassCard, AppBackground
    └── theme/                 Color, Type, Shapes, ThemePalette, Theme
```

---

## Setup

### Prerequisites

- Android Studio Iguana or newer
- JDK 17
- Android SDK 36
- A Firebase project (free Spark plan is enough for ≤10 users)

### One-time setup

1. **Clone & open** in Android Studio. Gradle will sync.
2. **Add a Firebase Android app** with the package `com.example.yoloaio`. Download `google-services.json` and drop into `app/`.
3. **Enable Firebase services**: Authentication (Email/Password + Google), Firestore (in Native mode), Storage.
4. **Configure Firestore** — set the `config/app` document, add the security rules in [Firestore security rules](#firestore-security-rules).
5. **Register the release SHA-1 + SHA-256** for Google Sign-In in Firebase Console → Project settings → Your apps → SHA certificate fingerprints. See [keystore/README.md](keystore/README.md) for the values.
6. **External API keys** — see [Firestore configuration](#firestore-configuration) below.
7. **Run** — `./gradlew assembleDebug` and install on a device.

### Generating a release keystore

```sh
keytool -genkeypair -v \
  -keystore keystore/yolo-release.jks \
  -alias yolo \
  -keyalg RSA -keysize 2048 -validity 10000
```

The app reads the keystore path + passwords from `local.properties`:

```
YOLO_KEYSTORE_FILE=keystore/yolo-release.jks
YOLO_KEYSTORE_PASSWORD=...
YOLO_KEY_ALIAS=yolo
YOLO_KEY_PASSWORD=...
```

Back the keystore up — losing it means losing the ability to update the published app.

---

## Firestore configuration

The app reads a single document at `config/app`. Empty/missing fields are tolerated — the corresponding feature shows a "not configured" empty state. Fields:

| Field | What | Required for |
|---|---|---|
| `googleWebClientId` | OAuth web client ID from Firebase Console → Authentication → Sign-in method → Google → Web SDK configuration | Google Sign-In |
| `tmdbApiKey` or `tmdbAccessToken` | TMDB v3 key or v4 bearer | Movies |
| `weatherApiKey` | OpenWeatherMap API key | Weather |
| `unsplashAccessKey` | Unsplash access key | Wallpapers, Quote backgrounds |
| `freesoundApiKey` | Freesound API key | Ringtones |
| `videosApiBaseUrl` | Vercel proxy URL for the Drive-backed PlayGround library | PlayGround library mode |
| `jitsiServerUrl` | Jitsi Meet server URL — defaults to `https://meet.jit.si` when blank | Voice / video calls |
| `moviesUrl`, `wallpapersUrl` | Per-feature toggles / URLs | Optional |
| `showMoviesMenu`, `showMusicMenu`, `showWallpapersMenu`, `showWeatherMenu` | Boolean feature gates that hide tiles on the home screen | Per-tester rollout |

---

## Firestore security rules

Production rules with proper per-collection ownership:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // Public app-wide config (read before sign-in for googleWebClientId).
    match /config/{docId} {
      allow read: if true;
      allow write: if false;
    }

    // User profile — signed-in users read, owner writes.
    match /users/{uid} {
      allow read: if request.auth != null;
      allow create, update: if request.auth != null && request.auth.uid == uid;
      allow delete: if false;

      // All user subcollections strictly owner-only.
      match /{subCollection}/{document=**} {
        allow read, write: if request.auth != null && request.auth.uid == uid;
      }
    }

    // Public quotes — community feed.
    match /publicQuotes/{quoteId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null
        && request.resource.data.ownerUid == request.auth.uid
        && request.resource.data.visibility == "public";
      allow update, delete: if request.auth != null
        && resource.data.ownerUid == request.auth.uid;
    }

    // Community Channel.
    match /communityMessages/{messageId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null
        && request.resource.data.senderId == request.auth.uid
        && request.resource.data.text is string
        && request.resource.data.text.size() > 0
        && request.resource.data.text.size() <= 2000;
      allow update: if false;
      allow delete: if request.auth != null
        && resource.data.senderId == request.auth.uid;
    }

    // 1:1 chats — permissive within signed-in scope.
    match /chats/{chatId} {
      allow read, write: if request.auth != null;
      match /messages/{messageId} {
        allow read, write: if request.auth != null;
      }
    }
  }
}
```

---

## Building & releasing

### Debug build (local dev)

```sh
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Release build (signed, for distribution)

```sh
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk` (~62 MB).

### ABI footprint

The release build packages native libraries for `arm64-v8a` only. The Jitsi Meet SDK alone adds ~30 MB per ABI, so dropping `armeabi-v7a` / `x86` / `x86_64` keeps the APK from ballooning past 150 MB. Effectively every Android phone made in the last 5 years is `arm64-v8a`. If you need 32-bit support, add `"armeabi-v7a"` back to `ndk { abiFilters }` in [app/build.gradle.kts](app/build.gradle.kts).

### Versioning

`versionCode` + `versionName` live in [app/build.gradle.kts](app/build.gradle.kts). Bump both before each release — Firebase App Distribution rejects duplicate `versionCode`s.

### Distribute to testers (Firebase App Distribution)

1. Firebase Console → App Distribution → Releases → drag the APK.
2. Add release notes.
3. Pick a tester group → Distribute.

Testers on v1.4.0+ get an in-app "Update available" dialog automatically; older versions still install via the email-link flow.

---

## Distribution & limitations

This app is shipped via **Firebase App Distribution to a private tester list** — not Google Play. Two features make Play Store distribution non-viable in the current build:

1. **Movies feature uses Vidking embed** — Vidking streams pirated content. Google Play's Deceptive Behavior policy + AdMob's copyright policies would both reject the listing.
2. **JioSaavn private API** — the music streaming uses a scraped, undocumented JioSaavn endpoint. Saavn could change or block this at any time, and large-scale use has legal exposure.

For Play distribution, the realistic paths are: (a) remove Movies, (b) replace Vidking with TMDB watch-provider deep-links into Netflix / Prime / etc., and (c) replace JioSaavn with Spotify Web API or YouTube Music.

The **Wi-Fi Lab** screen is genuinely educational and performs no real attack, but the strings "deauth", "handshake capture", "dictionary attack" can trip Play's automated malware/security scanners. Mild rewording (e.g. "Wi-Fi Concepts") plus removing the simulated-attack visualization is enough to clear that bar.

---

## Known caveats

- **OEM theme quirks**: some Vivo / older Oppo WebView builds couldn't enumerate the camera, which is why calling moved from a WebView path to the Jitsi Native SDK. The SDK adds ~30 MB to the APK.
- **`meet.jit.si` moderator wall** (late 2024+): the first participant must be authenticated, otherwise everyone sits in the lobby. Workaround: set `config/app.jitsiServerUrl` in Firestore to a community-run or self-hosted Jitsi instance.
- **JioSaavn DES key is the public web key** baked into Saavn's front-end JS. Decryption is "reverse engineering for personal use" territory — fine for friends-only, not for commercial distribution.
- **Location accuracy** is what the OS hands us via `getLastKnownLocation()`. Typically 10–50 m outdoors with GPS, larger indoors. Live location streaming isn't implemented.
- **Firestore reads on the free plan** are 50k/day. Heavy chat usage by 5–10 users stays well within that; scaling beyond ~100 active users needs the Blaze plan.
- **Notifications** for incoming chats use a foreground-listener pattern (a singleton observing all chats while the app is running). Push notifications via FCM aren't wired up — if the app is killed, no notifications fire.

---

## Useful commands

```sh
# Read SHA-1 / SHA-256 of the release keystore (needed for Google Sign-In)
keytool -list -v -keystore keystore/yolo-release.jks -alias yolo

# Verify the release APK is properly signed
$ANDROID_HOME/build-tools/<version>/apksigner verify --verbose app/build/outputs/apk/release/app-release.apk

# Clean build (when Gradle caches misbehave)
./gradlew clean assembleRelease
```
