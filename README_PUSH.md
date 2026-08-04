# Nexus: Web Push integration & instructions

This README explains how to finish the Web Push integration and Android/TWA signing.

1) Generate VAPID keys (locally):

- Install node web-push tool (or use any generator):
  npm install -g web-push
  web-push generate-vapid-keys --json > vapid.json

- Copy the `publicKey` and `privateKey` values.

2) Add GitHub Secrets (Repository settings -> Secrets):

- VAPID_PUBLIC_KEY = <publicKey from vapid.json>
- VAPID_PRIVATE_KEY = <privateKey from vapid.json>
- HF_TOKEN (already used)
- KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD (for Android signing if you build APK in CI)

3) Run the app locally

- Create virtualenv, install requirements:
  python -m venv .venv
  source .venv/bin/activate
  pip install -r requirements.txt

- Start app:
  python app.py

4) TWA / Android

- Generate a TWA Android project locally using bubblewrap and commit the generated `android/` directory into this repo (recommended). See the earlier conversation for commands.

5) Notes

- The service worker is served at `/sw.js` and the frontend registers it automatically after identity is confirmed. Make sure VAPID_PUBLIC_KEY secret is available to the app (the server will inject it).

