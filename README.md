# UPI Manager (Offline Ledger)

A 100% offline-first, secure financial ledger and UPI management application built with **Jetpack Compose**, **Room**, **Android Keystore**, and **Coroutines**. Designed for secure local bookkeeping, multi-device peer sync over Wi-Fi, receipt OCR scanning, and UPI account management without relying on external cloud servers.

---

## ✨ Key Features

- **🔒 100% Offline & Secure**: AES-256-GCM hardware-backed encryption using the Android Keystore system.
- **🔄 Local Wi-Fi Multi-Device Sync**: Synchronize transactions and ledgers securely across devices on the same Wi-Fi network or mobile hotspot.
- **📷 Receipt OCR**: Scan physical receipt bills and extract transaction details automatically using ML Kit.
- **💳 Multi-Account UPI QR**: Generate and manage multiple UPI payment QR codes and payment links.
- **📊 Financial Analytics & Reports**: Track net cash flow, income, expenses, and export detailed reports.
- **✅ Daily Tasks & Reminders**: Manage daily accounting tasks, audit balances, and track streaks.

---

## 🛠️ Environment Setup (`.env`)

This project uses the **Secrets Gradle Plugin** to manage configuration and secrets securely.

1. Copy the example environment file to create your local `.env` file:
   ```bash
   cp .env.example .env
   ```
2. Open `.env` and fill in any required development tokens (e.g., Firebase App Check debug tokens or API endpoints if applicable).
   *Note: `.env` is ignored by Git and will never be checked into version control.*

---

## 🏗️ Build & Run

### Prerequisites
- Android Studio Ladybug or later.
- Android SDK (compileSdk 36, minSdk 24).
- JDK 11+.

### Build Commands (CLI)
- **Debug Build**:
  ```bash
  ./gradlew assembleDebug
  ```
- **Run Unit & Roborazzi Tests**:
  ```bash
  ./gradlew test
  ```

---

## 🚀 Release Signing Configuration

For publishing release builds to Google Play or distributing signed APKs/Bundles:
1. Copy `keystore.properties.example` to `keystore.properties`:
   ```bash
   cp keystore.properties.example keystore.properties
   ```
2. Update `keystore.properties` with your upload keystore path, store password, key alias, and key password.
3. Build the release bundle:
   ```bash
    ./gradlew bundleRelease
    ```

### Repo push → in-app update (Firebase Hosting)

`.github/workflows/deploy.yml` builds a **signed release APK** on pushes to `main` or `master`, or a manual Actions run. It publishes `app-release.apk` and `version.json` together to the Hosting site `upi-manager-b2087.web.app`. The app checks `https://upi-manager-b2087.web.app/version.json`; the manifest's `latestVersionCode`, `latestVersionName` and `apkDownloadUrl` are generated from that same build. Hosting deployment replaces the site's deployed files with the contents of `public/` plus the staged APK/manifest; keep any other required Hosting assets in `public/`.

Set these **GitHub Actions repository secrets** under your repository's **Settings > Secrets and variables > Actions** before running the deploy workflow. The workflow reports all missing required secret names and stops before building or publishing; it never prints their values.

| Secret | Purpose |
| --- | --- |
| `CREDENTIAL_FILE_CONTENT` | Firebase service-account JSON (plain JSON or base64-encoded JSON), with permission to deploy Firebase Hosting and, if used, App Distribution. |
| `RELEASE_KEYSTORE_BASE64` | Base64 of the **persistent release signing keystore** (for example `base64 -w 0 my-upload-key.jks` on Linux); retain the original keystore securely for future releases. |
| `STORE_PASSWORD` | Keystore password. |
| `KEY_ALIAS` | Alias of the release key inside that keystore. |
| `KEY_PASSWORD` | Release key password. |

Use the **original release keystore** that signed the APK already installed by users. From a trusted local shell with GitHub CLI authenticated for this repository, replace `OWNER/REPO` and the local filename below; the base64 data goes straight to the secret via stdin, without printing it:

```bash
base64 -w0 original-key.jks | gh secret set RELEASE_KEYSTORE_BASE64 --repo OWNER/REPO
gh secret set STORE_PASSWORD --repo OWNER/REPO
gh secret set KEY_ALIAS --repo OWNER/REPO
gh secret set KEY_PASSWORD --repo OWNER/REPO
```

The last three commands prompt for their values locally; enter the existing keystore's credentials there. Set `CREDENTIAL_FILE_CONTENT` from your Firebase service-account credential in the same repository's Actions secrets settings. Do not paste keystore data or passwords into chat or commit them to the repository. A newly generated or different key cannot replace the original signature for an in-place update.

For optional tester distribution of this **same signed release APK**, also set `FIREBASE_APP_ID` and `FIREBASE_TESTERS` (comma-separated tester email addresses). No debug-key APK is published as an update. The Firebase project used by the workflow must be `upi-manager-b2087`, matching the app's update URL and Firebase configuration. The service account needs the necessary Hosting permissions (and App Distribution permissions if enabled); the workflow authenticates using `GOOGLE_APPLICATION_CREDENTIALS`.

### Seven-day checks on the phone

Opening the app registers one persistent Android WorkManager job with a seven-day interval and a network constraint. It survives ordinary process shutdown/reboots; Android can defer it for battery/network restrictions, and force-stopping the app prevents background work until it is reopened. No desktop cron is needed.

The app checks on first use and catches up on opening when the last successful check is at least seven days old. Background discoveries show an update notification if notification permission is granted; otherwise the cached update is offered when the app is opened. Choosing Later suppresses repeat dialogs for that same version. The existing Check for Updates button always requests fresh metadata, regardless of the seven-day interval or a prior dismissal. Downloads and installation still require the user's action; no silent install is performed.

The workflow derives a versionCode from UTC seconds since 2020-01-01 (greater than the existing static Hosting code `2`, within Android's supported range through 2086). It rejects a build whose code is not newer than the currently served manifest, including a duplicate run in the same second. The versionName is `1.0.<versionCode>`. Both values can also be supplied locally via `-PreleaseVersionCode=... -PreleaseVersionName=...`; the code must be 3–2100000000. The workflow serializes deploys across branches and cancels superseded runs, but do not manually deploy an older `version.json` afterward. A failed or unreachable remote version check blocks deployment rather than risking a downgrade.

Tag-triggered GitHub Releases (`.github/workflows/release.yml`) also use the same release signing secrets and publish a signed release APK. They do not update Hosting; run the deploy workflow to change the in-app update manifest.

**Signing compatibility:** an installed copy can update in place only when the new APK has the same application ID and signing certificate. An app previously installed with a debug key or another release key cannot install this release over it; use the same original signing key for existing users, or uninstall the old app first (which may remove local data). To check a run, inspect the Actions deploy job, then fetch the live `version.json` and `app-release.apk` URLs above and compare the manifest versionCode/versionName with the built APK using `apkanalyzer manifest version-code app-release.apk` and `apkanalyzer manifest version-name app-release.apk` (or `aapt dump badging`).

---

## 🤖 CI/CD Workflow

The repository includes a GitHub Actions workflow (`.github/workflows/android-ci.yml`) that automatically builds debug APKs and runs unit tests on every push and pull request.
