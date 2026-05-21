# Firebase setup for YoloAIO

The build will fail until you complete steps 1–3. Steps 4–6 are required for the app to actually work; step 7 only needed if you want the cloud music library populated.

## 1. Create the Firebase project
1. Open <https://console.firebase.google.com> → **Add project** → name it (e.g., `YoloAIO`).
2. Disable Analytics (optional) and create.

## 2. Register the Android app
1. In the project, click the Android icon to add an app.
2. Use package name **`com.example.yoloaio`** (must match `applicationId` in `app/build.gradle.kts`).
3. Skip the SHA-1 unless you plan to add Google sign-in later.
4. Download **`google-services.json`** and drop it at `app/google-services.json`.

After this file is in place, `./gradlew :app:assembleDebug` will succeed.

## 3. Enable services in Firebase Console
- **Authentication** → Sign-in method → enable **Email/Password**.
- **Firestore Database** → Create database → start in **test mode** (we'll lock it down in step 5).
- **Storage** → Get started → start in **test mode** (we'll lock it down in step 6).

## 4. Firestore data shape
The app expects these collections (created automatically as users sign up and chat):

```
users/{uid}
  uid: string
  email: string
  displayName: string
  initials: string
  avatarColor: number    // ARGB long, e.g. 0xFF6A1B9A
  createdAt: number      // ms epoch

chats/{chatId}                   // chatId = sorted([uidA, uidB]).join("_")
  participants: [uidA, uidB]
  lastMessage: string
  lastTime: timestamp

chats/{chatId}/messages/{messageId}
  senderId: string
  type: "text" | "image" | "gif"
  text: string?           // present when type == "text"
  mediaUrl: string?       // download URL for image/gif
  mediaLabel: string?     // optional caption / file name
  timestamp: timestamp    // server timestamp

users/{uid}/watchHistory/{tmdbId}    // populated by the Movies player
  tmdbId: string
  mediaType: "movie" | "tv"
  currentTime: number       // seconds
  duration: number          // seconds
  progress: number          // 0..100 (percent)
  updatedAt: number         // ms epoch

users/{uid}/favoriteWallpapers/{photoId}     // saved from the Wallpaper detail screen
  photoId: string
  smallUrl: string
  regularUrl: string
  fullUrl: string
  authorName: string
  description: string
  width: number
  height: number
  addedAt: number       // ms epoch

users/{uid}/settings/wallpaperRotation       // populated by the rotation toggle
  enabled: boolean
  intervalMinutes: number    // 15..1440
  updatedAt: number          // ms epoch

users/{uid}/favoriteRingtones/{toneId}       // saved from the Ringtones action sheet
  toneId: string
  name: string
  username: string                  // Freesound contributor
  durationSec: number
  tags: array<string>
  previewUrl: string                // playable mp3
  addedAt: number                   // ms epoch

users/{uid}/customQuotes/{quoteId}   // populated by the Quotes editor
  text: string
  author: string
  textColor: number               // ARGB Long
  fontSize: number                // sp
  bold: boolean
  italic: boolean
  alignment: "start" | "center" | "end"
  backgroundType: "gradient" | "solid" | "image"
  backgroundColors: array<number> // ARGB Longs (1 for solid, 2+ for gradient)
  backgroundImageUrl: string?     // when backgroundType == "image"
  createdAt: number               // ms epoch

config/app                 // single document holding remote feature flags & API keys
  admin: boolean
  moviesUrl: string
  showMoviesMenu: boolean
  showMusicMenu: boolean
  showNewsMenu: boolean
  showSettingsMenu: boolean
  showWallpapersMenu: boolean
  showWeatherMenu: boolean
  unsplashAccessKey: string    // public client ID; treat as secret-ish
  unsplashSecretKey: string    // not used by the client; admin-only
  wallpapersUrl: string        // e.g. "https://unsplash.com/s/photos/car" — query is parsed
  weatherApiKey: string
  weatherWebUrl: string
  tmdbApiKey: string           // TMDB v3 key (32-char hex); powers the Movies catalog/search
  tmdbAccessToken: string      // optional — TMDB v4 bearer (starts with "eyJ"). Prefer this if set.
  freesoundApiKey: string      // Freesound API token; powers the Ringtones catalog/search
```

### Seeding `config/app`
1. Firestore Database → Start collection → ID `config`.
2. Document ID: `app`. Add fields above with the values you want.
3. The Wallpaper tab parses the segment after `/photos/` from `wallpapersUrl` as the Unsplash search query (e.g. `…/photos/car` → `car`). Falls back to `nature` if not parseable.
4. The Home grid hides Music / Movies / Wallpaper tiles when `showMusicMenu` / `showMoviesMenu` / `showWallpapersMenu` are `false`.
5. The Movies tab needs **`tmdbApiKey`** — get a free TMDB API key at <https://www.themoviedb.org/settings/api>.
   - Paste the **API Key (v3 auth)** value (e.g. `abcdef0123456789…`) into the field, or the **API Read Access Token (v4)** which starts with `eyJ`.
   - The client auto-detects which one you provided.
6. The Ringtones tab needs **`freesoundApiKey`** — get a free token at <https://freesound.org/apiv2/apply>.
   - Create a Freesound account, request an API key (instant), and paste the token string into the field.

## 5. Firestore security rules
Replace the default rules with these (Console → Firestore → Rules):

```
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {
    // Anyone signed in can read user profiles; only the owner can write their own.
    match /users/{uid} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == uid;

      // Watch history is private to each user.
      match /watchHistory/{tmdbId} {
        allow read, write: if request.auth != null && request.auth.uid == uid;
      }

      // User-authored quotes from the Quotes editor.
      match /customQuotes/{quoteId} {
        allow read, write: if request.auth != null && request.auth.uid == uid;
      }

      // Saved wallpapers + per-user app settings (e.g. wallpaper rotation).
      match /favoriteWallpapers/{photoId} {
        allow read, write: if request.auth != null && request.auth.uid == uid;
      }
      match /settings/{settingName} {
        allow read, write: if request.auth != null && request.auth.uid == uid;
      }

      // Saved ringtones from the Ringtones tab.
      match /favoriteRingtones/{toneId} {
        allow read, write: if request.auth != null && request.auth.uid == uid;
      }
    }

    // Only chat participants can read/write a chat doc and its messages.
    match /chats/{chatId} {
      allow read, write: if request.auth != null
        && request.auth.uid in resource.data.participants;
      allow create: if request.auth != null
        && request.auth.uid in request.resource.data.participants;

      match /messages/{messageId} {
        allow read: if request.auth != null
          && request.auth.uid in get(/databases/$(db)/documents/chats/$(chatId)).data.participants;
        allow create: if request.auth != null
          && request.auth.uid == request.resource.data.senderId
          && request.auth.uid in get(/databases/$(db)/documents/chats/$(chatId)).data.participants;
      }
    }

    // App-wide config doc — readable by any signed-in user, writable only via Console/Admin.
    match /config/{document} {
      allow read: if request.auth != null;
      allow write: if false;
    }
  }
}
```

## 6. Storage security rules
```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /chats/{chatId}/{allPaths=**} {
      allow read, write: if request.auth != null;   // tighten later if needed
    }
  }
}
```

## Troubleshooting
- **`File google-services.json is missing`** at build time → step 2 not done.
- **Empty user list in Chat** → only one account has signed up. Create a second account on another device/emulator.
- **`PERMISSION_DENIED` in logcat** → security rules denied the request. Either you're not signed in or the rules don't match the data shape (re-check step 4 keys).
- **`UnknownHostException`** during music playback / image upload → device has no network.
