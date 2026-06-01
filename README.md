# 🍡 Mochi Kanban

A kawaii, offline-first, open-source Android kanban board with two-way Google
Calendar sync. Cards flow To do → Doing → Done by the clock, dated events stay in
step with your calendar, and a home-screen widget keeps today's list a tap away.
Reminders fire with Complete/Snooze, and you can type dates right into a title
("lunch tomorrow 15:00") and they're parsed automatically.

## Install

Download the latest signed APK from the
[Releases page](https://github.com/gnshb/mochi-kanban/releases).

## Build from source

Requirements: JDK 17, Android SDK with platform 35 and build-tools.

```sh
git clone https://github.com/gnshb/mochi-kanban.git
cd mochi-kanban
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:assembleRelease        # minified release APK
```

Google Calendar sync is optional. Add a **Web** OAuth client ID to
`local.properties` as `GOOGLE_OAUTH_CLIENT_ID=...`; without it the app runs as a
local-only board. Release signing reads `keystore.properties` (see
`keystore.properties.example`) or the `MOCHI_RELEASE_*` environment variables.

## Changelog

### v0.1.2

- Fixed duplicate calendar events from rapid edits before the first sync.
- All-day calendar events now resolve to local 00:00.

### v0.1.1

- Reworked the widget for reliability: instant complete, awaited refresh after every change, and a working background-opacity control.
- Strikethrough now follows the clock-driven column, so finished dated events read as done.
- New events default to a 1-hour window and no longer add Google's default reminder.
- Reminders show a Complete/Snooze heads-up; Snooze reschedules the event back to To do.
- Natural-language date parsing in titles (today/tomorrow/weekdays, "in N days", month-name and numeric dates, 24h times); Done clears nightly.

### v0.1.0

- Initial release.

## License

GPL v3. See [LICENSE](LICENSE).
