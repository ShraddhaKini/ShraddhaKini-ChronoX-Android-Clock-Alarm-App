# ChronoX

ChronoX is an Android clock app written in Java. It brings an alarm clock, world clock, countdown timer, stopwatch and a two-player chess clock together in one app with a bottom-navigation layout and selectable themes.

## Features

- **Clock** – large home clock display.
- **Alarm** – create, edit and delete alarms; scheduled with `AlarmManager` and rescheduled after device restart.
- **World Clock** – save and remove time zones, stored locally.
- **Timer** – countdown timer (persists across navigation and restarts, rings until stopped) and a stopwatch with laps.
- **Chess Clock** – two-player clock with editable player names, end-game options and saved game history.
- **Themes** – Black/Red, Dark Blue and White/Blue.

## Tech stack

- Java 11, Android SDK (minSdk 29, targetSdk/compileSdk 36)
- AndroidX AppCompat, ConstraintLayout, RecyclerView, ViewPager2
- Material Components, Navigation Component, ViewBinding
- Room database (alarms, world clocks, chess history)

## Getting started

1. Clone the repository.
2. Open the project folder in Android Studio (it will create `local.properties` automatically).
3. Let Gradle sync, then run the `app` configuration on a device or emulator running Android 10 (API 29) or higher.

Or build from the command line:

```bash
./gradlew assembleDebug
```

## Permissions

The app uses exact-alarm, notification, boot-completed, vibration and foreground-service permissions so that alarms and timers can ring on time and survive a restart. Exact delivery depends on the device's alarm permissions and battery restrictions.

## Project structure

```
app/src/main/java/com/example/chronox/
  alarm/      alarms, scheduling, boot receiver
  chess/      chess clock and history
  common/     shared utilities and helpers
  data/       Room database
  home/       clock home screen
  settings/   themes and preferences
  stopwatch/  stopwatch and laps
  timer/      countdown timer and alert service
  worldclock/ world clock list
```

See [CHANGES.md](CHANGES.md) for the development change log.

## Author

Shraddha Kini
