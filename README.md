# CHRONO PHANTOM

**Android Device Intelligence Engine** — local device telemetry, explainable diagnostics, and optional one-shot AI summaries.

ChronoPhantom helps answer questions such as **“Why is my mobile data draining?”** by collecting Android-reported usage and device-state signals, then presenting a concise diagnostic briefing. It is not a chatbot.

## Current MVP

- Mobile and Wi-Fi data totals for the last 24 hours, plus a previous-24-hour mobile comparison.
- Battery percentage and current network transport.
- Recent app foreground-time ranking (requires Usage Access).
- One-shot OpenRouter summary page: ask a question, press Generate, and receive findings, evidence, next checks, and confidence.
- OpenRouter API key encrypted with an AES-GCM key held by Android Keystore.
- Dark, high-contrast PHANTOM interface.

## Build

- Android Studio / Android Gradle Plugin 8.9.1
- JDK 17
- Gradle 8.11.1
- compileSdk 36, targetSdk 35, minSdk 26

Open this repository in Android Studio and sync Gradle, or run gradle assembleDebug with Gradle 8.11.1 installed. GitHub Actions builds a debug APK.

## First launch

1. Open **Settings → Usage Access** and enable ChronoPhantom. This is needed for Android's usage-statistics APIs.
2. Open **Settings** in PHANTOM and enter your own OpenRouter API key.
3. Save the key. It is encrypted locally using Android Keystore.
4. Open **AI Summary**, describe the issue, and press **Generate Intelligence Summary**.

The API key is never included in source code or the APK. AI analysis happens only after the user explicitly presses the button. The question and displayed telemetry are sent to OpenRouter's API.

## Data and privacy boundaries

- Uses Android's NetworkStatsManager, UsageStatsManager, BatteryManager, and ConnectivityManager.
- Network totals are Android system-reported aggregates; they can be delayed and do not represent live packet capture.
- App foreground time is not the same as per-app network usage. PHANTOM does not infer app-level data consumption from screen time.
- No message reading, packet payload capture, traffic decryption, root access, or covert monitoring.
- Device telemetry stays on-device unless the user explicitly requests an AI summary. Then the question and summary context are sent to OpenRouter.
- The free model router can change the underlying model and may be unavailable or rate-limited.

## Current limitations

This is the first working foundation, not the final intelligence platform. Historical baselines are limited to Android's available accounting windows. Future iterations can add persistent local history, per-app network attribution where Android permits it, battery-drain correlation, anomaly rules, exportable reports, and richer event timelines.

## Project layout

- MainActivity.kt — Compose interface and navigation
- DeviceCollector.kt — Android telemetry collection
- OpenRouterClient.kt — one-shot AI request
- SecretStore.kt — encrypted API-key storage

**Package:** com.chrono.phantom  
**Version:** 0.1.0
