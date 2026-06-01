# Mochi Kanban

A minimal, opensource Trello + TickTick + Google Calendar app for Android, themed after
[`mochi-money`](../mochi-money). Three columns (To do, Doing, Done), drag-and-drop cards,
local reminders, a home-screen widget, and two-way Google Calendar sync over CalDAV.

Future phases: GNOME desktop client and a self-hosted sync server. The local schema is
already sync-ready so neither phase needs a migration.

## Stack

- Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Material 3
- Hilt 2.52 (DI), Room 2.6.1 (offline-first), WorkManager 2.9.1, DataStore
- Glance 1.1.1 for the home-screen widget
- OkHttp 4.12 — direct calls to Google's CalDAV endpoint
- min SDK 26, target SDK 35, JVM 17

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
```

The `gradle.properties` heap is tuned for memory-constrained machines
(1.5 GiB Gradle, 1 GiB Kotlin daemon, no parallel project builds).

## Google Calendar setup (build-time, one-time)

Sync uses native "Sign in with Google" (Credential Manager + AuthorizationClient).
The OAuth client ID lives in `local.properties` — it's never exposed in the
in-app UI. If you don't configure it, the Calendar Sync section simply doesn't
appear and the app runs as a local-only kanban.

One-time setup:

1. https://console.cloud.google.com → New project.
2. APIs & Services → Library → enable **Google Calendar API**.
3. OAuth consent screen → External → app name + your email + scope
   `https://www.googleapis.com/auth/calendar.events` + add yourself as a test
   user.
4. Credentials → Create OAuth client ID **twice**:
   - **Web application** (no redirect URI needed) — copy the client ID.
   - **Android** — package `com.mochikanban.app.debug` (debug) /
     `com.mochikanban.app` (release), SHA-1 from
     `keytool -list -v -keystore ~/.android/debug.keystore -storepass android -keypass android | grep SHA1`.
5. Add the **Web client ID** to `local.properties`:
   ```
   sdk.dir=/path/to/android-sdk
   GOOGLE_OAUTH_CLIENT_ID=1234567890-xxxx.apps.googleusercontent.com
   ```
   Rebuild. Settings now shows a clean "Sign in with Google" button.

## Permissions

| Permission | When prompted | Why |
|---|---|---|
| `POST_NOTIFICATIONS` (Android 13+) | First launch | Card reminders |
| `SCHEDULE_EXACT_ALARM` (Android 12+) | Settings → "Allow exact alarms" | Reminders < 15 min before event |
| Internet | implicit | Calendar sync |

If exact-alarm permission isn't granted, reminders fall back to WorkManager,
which Doze may delay by a few minutes.

## Architecture

```
com.mochikanban.app
├── domain/           Card, Column, ColorTag, SyncState, OpType
├── data/
│   ├── db/           Room: entities, DAOs, Converters, KanbanDatabase
│   └── repo/         CardRepository, CalendarRepository
├── ui/
│   ├── theme/        Color tokens, Mochi dark scheme, Fredoka, MochiCardShape
│   ├── components/   KanbanCard, ColumnHeader
│   ├── board/        BoardScreen, BoardViewModel, drag-drop overlay
│   ├── edit/         EditCardSheet, EditCardViewModel
│   └── settings/     SettingsScreen, SettingsViewModel
├── widget/           Glance widget, repository, updater
├── reminders/        Channels, ReminderScheduler, ReminderWorker
├── sync/
│   ├── auth/         CaldavCredentials (EncryptedSharedPreferences)
│   ├── net/          CaldavClient, CaldavXml, ICal, VEvent, EventMapper
│   ├── engine/       SyncEngine, OutboxProcessor, ConflictResolver, ColumnMapper
│   ├── worker/       CalendarSyncWorker, OutboxFlushWorker
│   └── WorkManagerSyncTrigger
├── di/               Hilt modules
├── util/             Time helpers
├── KanbanApplication, MainActivity
```

The data flow is offline-first:

```
local edit → CardDao.upsert(dirty=1) → OutboxDao.enqueue
                                           ↓
                              expedited OutboxFlushWorker
periodic 15m → CalendarSyncWorker → OutboxProcessor.drain → PUT/DELETE
                                  → CaldavClient.syncCollection(syncToken)
                                  → multiGet for changed hrefs
                                  → EventMapper → CardDao upsert (skip if local newer)
                                  → store new sync-token; reschedule reminders
```

Column membership rides on each VEVENT's `X-MOCHIKANBAN-COLUMN` property,
preserved by Google CalDAV alongside the standard fields. Missing →
defaults to TODO when ingested.

Conflict resolution: last-write-wins by VEVENT `LAST-MODIFIED` (server) vs
`updatedAtLocal` (device). On PUT we send `If-Match: <etag>`; on 412 we
treat it as a conflict, requeue, and re-fetch.

## License

TBD.
