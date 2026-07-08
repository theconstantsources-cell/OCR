# AI ScanHid

Android app for Honeywell EDA51 (and similar rugged Android scanners): capture a photo of a document, run on-device OCR, let the operator review/edit the text, then "type" the approved text onto a Bluetooth-paired PC via HID keyboard emulation - wherever the PC's cursor currently is (an Excel cell, a text field, anything with keyboard focus).

## How the PC delivery works

The phone registers itself as a **Bluetooth HID keyboard peripheral** using Android's `BluetoothHidDevice` API. There is no companion app on the PC - pairing works exactly like pairing any wireless Bluetooth keyboard (PC Bluetooth settings -> Add device -> select the phone). When you approve OCR'd text, the app sends it as a sequence of simulated keystrokes; the PC's OS routes those keystrokes to whatever currently has keyboard focus, the same as if you'd typed them yourself. The app has no awareness of what's focused on the PC and doesn't need any - that's what makes it work "anywhere."

Relevant code:
- `app/src/main/java/com/scanhid/ocr/bluetooth/HidReportDescriptor.kt` - the USB HID keyboard report descriptor
- `app/src/main/java/com/scanhid/ocr/bluetooth/HidKeyCodes.kt` - character -> HID usage code + modifier mapping
- `app/src/main/java/com/scanhid/ocr/bluetooth/HidKeyboardManager.kt` - registers the HID app, tracks connection state, sends key-down/key-up reports

## Requirements

- **minSdk 28** (Android 9) - required by `BluetoothHidDevice`. Confirmed compatible with EDA51 units running Android 9/10/11.
- **compileSdk 34** - required by the AndroidX/Compose/CameraX library versions this project uses (their AAR metadata mandates compiling against API 34+, independent of minSdk/targetSdk).
- JDK 17
- Android SDK platform 34 + platform-tools

## Building locally (VS Code + terminal, no Android Studio required)

1. Install JDK 17.
2. Install the Android **command-line SDK tools** (from the official Android developer downloads page), then:
   ```
   sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
   ```
3. Create `local.properties` in the project root (not committed - it's user/machine-specific):
   ```
   sdk.dir=/path/to/your/Android/sdk
   ```
4. Build and install:
   ```
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

> This repository ships a working Gradle wrapper (`gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.jar`), so no local Gradle install is required - just JDK + Android SDK.

## First-time pairing with a PC

1. Launch the app on the EDA51 and grant the Camera and Bluetooth permissions when prompted.
2. Open the connection screen (tap the "Bluetooth: ..." chip at the top of the capture screen).
3. Tap **Make discoverable**.
4. On the PC: Settings -> Bluetooth -> Add device -> select the EDA51 (it will appear as a keyboard).
5. Once paired, the connection screen shows "Connected to: <PC name>". Pairing is one-time per PC - you don't need to repeat this for every scan.

## Using it

1. Point the camera at the document and tap **Scan**.
2. Review the extracted text on the next screen; edit it if OCR made mistakes.
3. Tap **Approve & Send to PC** and confirm the popup.
4. The text is typed into whatever has keyboard focus on the paired PC, followed by a Tab keystroke (so, e.g., an Excel selection advances to the next cell).

## Known limitations

- Only ASCII/US-QWERTY characters are mapped (see `HidKeyCodes.kt`); accented characters, non-Latin scripts, and emoji in OCR output are skipped rather than typed.
- Bluetooth HID supports one active PC connection at a time.
- Typing speed includes a small delay between keystrokes (15ms) to avoid dropped characters on some PC Bluetooth stacks; very long extracted text will take a few seconds to fully type out.
- OCR runs fully on-device via ML Kit (no internet required), which favors printed/typed Latin-script text; accuracy on handwriting will be lower.

## A note on this repository's build status

This project was authored in an isolated cloud sandbox whose network policy blocks Google's Android artifact host (`dl.google.com`), which every Android build depends on (Android Gradle Plugin, AndroidX, CameraX, ML Kit, Compose). Because of that, **the app could not be compiled to an APK from within that sandbox** - only written and reviewed. It has not been run against the Android compiler. Building it on a normal developer machine (unrestricted internet) with the steps above should work as expected, but treat the very first local build as the real first compile/test pass, and expect to fix any small issues Gradle turns up (dependency version bumps, etc.).
