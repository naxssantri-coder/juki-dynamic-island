# Island — Dynamic Island-style overlay for Android

Native Kotlin + Jetpack Compose. All phases from the brief are implemented in source, but
**nothing has been compiled or run yet** (the authoring sandbox cannot reach Google Maven).

## Build
- Android Studio: open this folder, sync, Run.
- No PC: push to GitHub -> Actions -> "Build debug APK" -> download artifact `island-debug-apk`.

## First-run checklist
1. Open Island, grant **Display over other apps**.
2. For Music + Notifications: enable **Notification access** (on Android 13+ sideloaded APKs:
   App info > ⋮ > Allow restricted settings first).
3. For Bluetooth: grant the Bluetooth permission prompt (Android 12+).
4. Pick apps under Notification access > Choose apps.

## Architecture
- `domain/`   IslandController (state, priority queue, timeouts), IslandEvent, EventPriorityPolicy
- `sources/`  ChargingSource, MediaSource, BluetoothSource, SourceManager (start/stop by toggle + permission)
- `notification/` NotificationListenerService + NotificationEvents (allow-list, call detection)
- `timer/`    TimerManager (elapsedRealtime deadline + AlarmManager), alarm receiver
- `overlay/`  OverlayWindow, IslandOverlayService, placement from insets/cutout
- `ui/`       DynamicIsland composables, springs, main/settings screens
- `data/`     DataStore settings
- `boot/`     BootReceiver (opt-in)
