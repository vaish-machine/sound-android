# SOUND — Android Music Player

SOUND is a lightweight Android music player designed for listening to music stored locally on your device.

## Features

- Clean, dark-themed interface.
- Local music folder selection.
- Persistent folder access using Android Storage Access Framework.
- Automatic MP3 scanning.
- Music library listing.
- Audio playback using AndroidX Media3 ExoPlayer.
- Play and pause controls.
- Previous and next track controls.
- Playback progress and seek bar.
- Current track and playback duration display.

## Technology

| Component | Details |
|---|---|
| Platform | Android |
| Language | Java |
| Package | `com.gathin.sound` |
| Minimum Android | Android 6.0 (API 23) |
| Target SDK | 36 |
| Compile SDK | 37 |
| Gradle | 9.6.0 |
| Android Gradle Plugin | 9.5.0 |
| Audio Engine | AndroidX Media3 ExoPlayer |

## Requirements

- Android Studio
- JDK 17 or compatible Gradle runtime
- Android SDK
- Internet connection for initial Gradle dependency downloads

## Build Instructions

Clone the repository:

    git clone https://github.com/vaish-machine/sound-android.git
    Set-Location .\sound-android

Build the debug APK:

    .\gradlew.bat assembleDebug

Generated APK location:

    app\build\outputs\apk\debug\app-debug.apk

## How to Use

1. Install and open SOUND.
2. Select Choose Music Folder.
3. Grant access to a folder containing MP3 files.
4. Wait for the music library scan.
5. Select a track to begin playback.
6. Use the playback controls and seek bar.

## Privacy and Storage

SOUND is designed around local music playback.

Folder access is granted by the user through Android's Storage Access Framework. The selected folder permission is persisted so that the application can restore access when reopened.

The inspected source does not implement cloud music storage or online music streaming.

## Project Status

Version: 1.0

This repository contains the Android application source code.

Generated APKs, build directories, local machine configuration and signing credentials are excluded from source publishing.

## License

No open-source license has been assigned yet.
All rights are reserved by the copyright holder unless a license is added to this repository.