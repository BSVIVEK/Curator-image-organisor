# Curator

Curator is an Android app for fast, hands-free photo curation. It helps users quickly sort images into Yes/No/Not Sure categories using gestures or voice, view selected images, and export organized folders and text logs.

Screenshot: app/src/main/Screenshot_20240824_182853.png

## Features

- Gesture-based sorting: swipe left/right to navigate, swipe up/down to categorize, double-tap for "Not Sure", triple-tap to exit
- Voice commands via SpeechRecognizer: say "yes", "no", "not sure", or "exit"
- Export categorized images into `Yes`, `No`, and `NotSure` folders
- Persisted state and simple text logs (`selected.txt`, `not_selected.txt`, `not_sure.txt`)
- Error logging to `ErrorLog.txt` for diagnostic information
- Uses Glide for image loading and Android DocumentFile API for storage access

## Prerequisites

- Android Studio
- JDK 11+ and Android SDK
- Device or emulator with API level 26+ (Android 8.0) recommended

## Build & Run

1. Open the project in Android Studio (File → Open → `C:\Users\Work\AndroidStudioProjects\Curator`).
2. Connect a device or start an emulator (API 26+).
3. Grant microphone and storage permissions when prompted.
4. Run the app from Android Studio or build via command line:
   - Open a command prompt in the project root and run:
     - `gradlew.bat assembleDebug`
   - Install on a device (if needed):
     - `adb install -r app\build\outputs\apk\debug\app-debug.apk`

## Usage

- On first launch choose a directory to curate.
- Use gestures or speak commands to categorize images.
- Tap the gear icon to toggle voice recognition or download the `Yes` selected list.
- Export happens automatically when exiting or on pause; exported folders and logs are created under the app's external files directory.

## Troubleshooting

- Microphone permission denied: enable microphone permission in system settings.
- No images found: ensure the selected directory contains image files and the app has permission to read it.
- Check `ErrorLog.txt` in the app external files directory for runtime exceptions.

## Code

Main logic: `app/src/main/java/com/blue/curator/MainActivity.java`.

Libraries used:
- Glide for image loading
- Android SpeechRecognizer
- AndroidX DocumentFile
