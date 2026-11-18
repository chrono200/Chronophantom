# CHRONO PHANTOM

**Android Event Intelligence Engine**

ChronoPhantom builds a local, searchable picture of what happens on an Android device. It records permitted system events, correlates activity and device-state changes, detects unusual patterns, and turns the timeline into useful diagnostic reports.

It is not a cleaner, a spy tool, a packet sniffer, or a chat app.

## Core capabilities

- **Event timeline:** app foreground transitions, notification arrival/removal metadata, connectivity changes, and battery-level changes.
- **Device-state correlation:** see what was happening around a battery drop, connection change, or burst of activity.
- **Application activity:** inspect when apps entered the foreground, using Android Usage Access.
- **Permission intelligence:** understand which optional access permissions are enabled and what each one unlocks.
- **Anomaly detection:** local rules flag unusual notification bursts, repeated connectivity changes, rapid app switching, and battery drops.
- **Diagnostic reports:** share a readable report containing the selected time window, event counts, notable patterns, and timeline entries.
- **Privacy-first storage:** event history stays in a local SQLite database. Notification contents are not collected.
- **Branded launcher identity:** a custom ghost-clock icon, dark system theme, and consistent ChronoPhantom app label.
- **Failure isolation:** device collection and notification journal errors are contained and shown as recoverable issues instead of terminating the main screen.

## Platform boundaries

ChronoPhantom uses public Android APIs on ordinary, unrooted devices. It cannot observe every system event, guarantee background execution, inspect private app data, read notification message bodies, capture packet payloads, or attribute all network traffic to individual apps. Android version, vendor restrictions, battery optimization, and user-granted access affect coverage.

The app collects only after the user enables the relevant Android access. Usage Access and Notification Access are optional and can be revoked in Android Settings.

## Build

- Android Gradle Plugin 8.9.1
- Kotlin 2.0.21
- JDK 17
- Gradle 8.11.1
- compileSdk 36 · targetSdk 35 · minSdk 26

Open in Android Studio and sync Gradle, or run `gradle assembleDebug`. GitHub Actions builds a debug APK on pushes to `main`. The debug APK is suitable for testing; Android may require uninstalling an older debug-signed build before installing a newly generated APK if its signing key differs.

## First launch

1. Open **Access** and enable Usage Access for ChronoPhantom.
2. Optionally enable Notification Access to record notification arrival/removal events (metadata only).
3. Return to the app and press **Sync events**.
4. Explore Timeline and Insights. Use **Export report** to share a diagnostic snapshot.

## Storage and privacy

- Events are stored in the app's private SQLite database.
- No account, cloud backend, analytics SDK, or background upload is included.
- No notification title/body, message text, contact data, or packet contents are stored.
- Export is user-initiated through Android's share sheet.
- Clearing app data or uninstalling removes the local event history.

## Project structure

- `MainActivity.kt` — app interface and navigation
- `PhantomEvent.kt` — event model
- `EventStore.kt` — local SQLite event store
- `DeviceObserver.kt` — Android event collection and state sampling
- `PhantomNotificationListener.kt` — optional notification metadata listener
- `InsightEngine.kt` — local correlation and anomaly rules
- `DiagnosticReport.kt` — human-readable report generation

**Application ID:** `com.chrono.phantom`  
**Version:** `0.3.0`
