# Distributing Yolo AIO via Firebase App Distribution

This is the step-by-step for getting the signed release APK onto your 5–10 testers' phones.

## One-time setup (you, ~5 min)

1. **Enable App Distribution** in your existing Firebase project:
   - Go to <https://console.firebase.google.com/>
   - Pick your Yolo AIO project
   - Left sidebar → **Release & Monitor → App Distribution**
   - Click **Get started** (free, no credit card needed)

2. **(Optional) Create a tester group** — makes it easy to add/remove people in bulk:
   - App Distribution → **Testers & Groups** tab → **New group**
   - Name it e.g. `early-access`
   - Add tester emails (the Gmail / Google-account email they use on their Android phone)

## Each release (3 steps, ~30 sec)

After every `./gradlew assembleRelease`:

1. **Firebase Console → App Distribution → Releases → Drag-and-drop the APK**
   - File location: `app/build/outputs/apk/release/app-release.apk`
2. **Add release notes** in the panel that appears, e.g.:
   ```
   v1.0
   - Initial build for testing
   - Sign-in: email/password or Google
   - Known issue: weather forecast may take a few seconds on first load
   ```
3. **Distribute to → pick your `early-access` group → Distribute**

That's it. Each tester gets an email within ~30 seconds.

## Tester onboarding (each tester, one-time, ~2 min)

Send them this short message:

> Hey — invite to test Yolo AIO. You'll get an email from `noreply@firebase.com`. Tap the link on your Android phone, install the "Firebase App Tester" app it suggests, then install Yolo AIO from inside App Tester. Next time I push an update, App Tester will notify you.

For future updates, testers just tap the notification → install.

## Things to check before each release

- [ ] `versionCode` bumped in [app/build.gradle.kts](app/build.gradle.kts) (Firebase rejects duplicate codes)
- [ ] `versionName` updated for human readability (e.g. `1.1`, `1.2.0`)
- [ ] Release notes accurate
- [ ] Tested the APK on your own phone first (`adb install app-release.apk`)

## Troubleshooting

**"App not installed" on tester's phone**
→ They're trying to install over a debug build with a different signature. They must uninstall the debug version first.

**Tester doesn't get the email**
→ Make sure the email you added matches the Google account they're signed into on their Android phone. Personal Gmail works; work accounts may block.

**APK upload fails with "invalid version code"**
→ Bump `versionCode` (must be strictly higher than the last upload) in `app/build.gradle.kts`.

**You want to send updates via CLI instead of Console drag-drop**
```
npm install -g firebase-tools
firebase login
firebase appdistribution:distribute \
  app/build/outputs/apk/release/app-release.apk \
  --app <YOUR_FIREBASE_APP_ID> \
  --groups early-access \
  --release-notes "v1.1 - fixed cast button crash"
```
The Firebase App ID is at Firebase Console → Project Settings → General → Your apps → `App ID`.

## Reminders for this build specifically

- **Movies / Vidking is included.** Don't share the install link outside the trusted-tester circle — it streams pirated content, which is on you legally if it gets widely distributed.
- **Wi-Fi Lab is concept/educational only**, but mention that to testers so they understand the disclaimer banner.
- **The keystore is in `keystore/yolo-release.jks`** — back it up to Drive + a password manager **today**. Losing it = no more updates ever.
