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

---

## 🤖 CI/CD Workflow

The repository includes a GitHub Actions workflow (`.github/workflows/android-ci.yml`) that automatically builds debug APKs and runs unit tests on every push and pull request.
