# Nexus Android App — Setup Guide

This is a **native Android WebView wrapper** (Kotlin + Gradle only — no Capacitor/Node)
around your Hugging Face Space at `https://hemel24-massanger.hf.space`. It was built
this way on purpose: fewer moving parts means fewer places for GitHub Actions to fail.

## What's included
- `MainActivity.kt` — loads your animated splash screen first, then the live site.
  Also wires up file uploads (`<input type="file">`), microphone access (for the
  voice/PTT feature), and the Firebase push-notification token handoff.
- `NexusFirebaseMessagingService.kt` — receives push notifications even when the
  app is fully closed, and shows a system notification.
- `app/src/main/assets/www/splash.html` — the loading screen: your logo in the
  center with 5 orbiting rings that light up one after another, Facebook-Lite style.
- `.github/workflows/build-apk.yml` — builds a debug APK on every push and makes
  it downloadable as a workflow artifact. **No `gradlew` wrapper is committed** —
  the workflow installs Gradle directly via the official `setup-gradle` action.
  A corrupted or non-executable wrapper is one of the most common causes of
  the exact kind of repeated CI failures you were hitting, so this avoids that
  whole category of problems.

## The notification reality (please read this first)
A WebView-only app **cannot** notify you once Android has fully closed it — that
needs Firebase Cloud Messaging (FCM), Google's push system, which wakes the app
via the OS even when it's killed. This project is wired for FCM already; you just
need to connect it to a Firebase project (free, ~10 minutes, one-time).

Without doing the Firebase steps below, **the app still builds and works
perfectly** — you just won't get notifications while it's closed. So you can
build and test the APK today, and add Firebase whenever you're ready.

---

## Step 1 — Create a Firebase project (free)
1. Go to https://console.firebase.google.com → **Add project** → name it anything
   (e.g. "Nexus").
2. Once created, click **Add app → Android**.
3. Package name: enter exactly `com.nexus.app` (must match `app/build.gradle`).
4. Download the file it gives you: **`google-services.json`**. Keep it — don't
   commit it to GitHub (it's already in `.gitignore`).
5. Back in the Firebase console, go to **Project settings → Service accounts →
   Generate new private key**. This downloads a second, different JSON file —
   this one is for your **server** (Hugging Face Space), not the app.

You now have two separate files. Don't mix them up:
| File | Goes where | Used for |
|---|---|---|
| `google-services.json` | GitHub secret `GOOGLE_SERVICES_JSON` (this repo) | lets the **app** talk to Firebase |
| service-account JSON | Hugging Face Space secret `FIREBASE_CREDENTIALS_JSON` | lets your **server** send pushes |

## Step 2 — Add the GitHub secret (for the app build)
1. In this GitHub repo → **Settings → Secrets and variables → Actions → New
   repository secret**.
2. Name: `GOOGLE_SERVICES_JSON`
3. Value: paste the **entire contents** of the `google-services.json` file you
   downloaded in Step 1.

## Step 3 — Add the Hugging Face secret (for the server)
1. On your Space (`hemel24-massanger`) → **Settings → Variables and secrets**.
2. Add a new **secret**: name `FIREBASE_CREDENTIALS_JSON`, value = the entire
   contents of the service-account JSON file from Step 1.
3. Make sure `firebase-admin` is in your Space's `requirements.txt` (see the
   `requirements_addition.txt` file provided alongside this project — just add
   that one line to your existing `requirements.txt`).
4. Restart the Space so it picks up the new secret and dependency.

That's the entire server side — the updated `app.py` you already have registers
each phone's push token and sends a push automatically whenever a message
arrives for someone who isn't actively looking at that chat.

## Step 4 — Build the APK
1. Push this project to a new GitHub repository (or the repo you've been using).
2. Go to the **Actions** tab → **Build Nexus APK** → **Run workflow** (or just
   push a commit — it runs automatically).
3. Once it finishes (green check), open the workflow run → scroll to
   **Artifacts** → download `nexus-debug-apk`. Unzip it — that's your `.apk`.
4. Transfer it to your Android phone and install it (you'll need to allow
   "install unknown apps" for whatever app you used to open the file).

## Step 5 — Confirm it all works
- Open the app → you should see the orbiting-lights splash for ~2.4s, then it
  loads your live site.
- Log in. Send yourself a test message from another account/browser while the
  app is in the background — you should get a system notification, and tapping
  it should open that exact chat.
- Try uploading a profile photo — this needs the file-picker wiring in
  `MainActivity.kt`, already included.
- Try the voice/PTT button — needs the microphone permission, already wired
  (Android will prompt you for mic access the first time you open the app).

---

## If the build still fails
Open the failed workflow run and read the **first** red error line under
"Build debug APK" (later lines are usually just consequences of the first
one). The most likely causes, in order of likelihood:

1. **`GOOGLE_SERVICES_JSON` secret has extra quotes/whitespace** — when pasting,
   paste the raw JSON exactly as downloaded, nothing added around it.
2. **Package name mismatch** — `applicationId` in `app/build.gradle` must be
   exactly `com.nexus.app`, matching what you typed into the Firebase console
   in Step 1.3. If you want a different package name, change it in *both*
   places.
3. Something unrelated to Firebase entirely — since the Firebase plugin only
   applies when `google-services.json` exists, you can isolate the problem by
   temporarily removing the `GOOGLE_SERVICES_JSON` secret and re-running the
   build. If it succeeds without Firebase, the issue is specifically in Step
   1–3 above, not the Android project itself.

## Customizing
- **App name / package**: `app/build.gradle` (`applicationId`) and
  `app/src/main/res/values/strings.xml` (`app_name`).
- **Live URL**: `MainActivity.kt`, the `LIVE_URL` constant at the top.
- **Icon**: replace the PNGs in `app/src/main/res/mipmap-*/`. A placeholder
  purple "N" icon is included so the build works out of the box — swap in
  your real logo whenever you like (same filenames, same folders).
- **Splash animation**: `app/src/main/assets/www/splash.html` — pure HTML/CSS,
  easy to tweak colors/timing/logo without touching any Kotlin code.

## Signing a release build (optional, for Play Store later)
The workflow currently builds a **debug** APK, which is enough to install and
test directly on your phone. Play Store submission needs a signed **release**
build with your own keystore — that's a separate step involving a keystore
file + 3-4 more GitHub secrets. Ask me when you're ready for that and I'll add
it without touching anything that currently works.
