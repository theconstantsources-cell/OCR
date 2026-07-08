# AI ScanHid

Android app for Honeywell EDA51 (and similar rugged Android scanners): capture a photo of a document, run on-device OCR, let the operator review/edit the text and see the OCR confidence level, then send the approved text to a paired Windows PC - where it's typed out wherever the cursor currently is (an Excel cell, a text field, anything with keyboard focus).

## How the PC delivery works

Two pieces work together:

1. **The Android app** (this repo, `app/`) connects to the PC over plain Bluetooth **RFCOMM (Serial Port Profile)** - the same well-supported connection type used by things like Bluetooth barcode scanners and OBD-II readers - and streams the approved text over that socket.
2. **A small Windows companion program** (`windows-companion/`) runs on the PC, listens for that Bluetooth connection, and types the received text out using Windows' own keystroke-injection API (`SendInput`) - wherever the cursor/focus currently is. No cloud, no internet - everything happens over the direct Bluetooth link between the two devices.

This replaced an earlier design where the phone pretended to be a Bluetooth HID keyboard directly (no PC-side software at all). That approach requires a fussier OS-level secure-pairing handshake for input devices specifically, which some Android Bluetooth chipsets don't reliably complete with Windows. Plain RFCOMM pairing is a much more universally supported flow - the tradeoff is the one small PC-side program above.

**Bonus of this design**: because keystrokes are injected locally by the PC's own OS rather than simulated over Bluetooth HID, the Windows companion can type *any* Unicode character directly (accented letters, non-Latin scripts, symbols) - not just US-QWERTY ASCII like the old approach was limited to.

Relevant code:
- `app/src/main/java/com/scanhid/ocr/bluetooth/BluetoothSppManager.kt` - lists paired devices, opens the RFCOMM socket, streams text
- `windows-companion/Program.cs` - hosts the Bluetooth RFCOMM service and injects keystrokes via `SendInput`

## Requirements

- **minSdk 28** (Android 9), confirmed compatible with EDA51 units running Android 9/10/11.
- **compileSdk 34** - required by the AndroidX/Compose/CameraX library versions this project uses (their AAR metadata mandates compiling against API 34+, independent of minSdk/targetSdk).
- JDK 17
- Android SDK platform 34 + platform-tools
- On the PC: .NET 8 SDK (to build the companion program) and Windows 10/11 with a Bluetooth adapter

## Building the Android app locally (VS Code + terminal, no Android Studio required)

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

## Building and running the Windows companion program

See `windows-companion/README.md` for full setup steps. Short version:
```
cd windows-companion
dotnet run
```
Leave it running in a terminal window while you use the app - it prints what it's doing (connections, received text) so you can see it working.

## First-time pairing with a PC

1. On the EDA51: launch the app, grant the Camera and Bluetooth permissions when prompted.
2. Start the Windows companion program on the PC (see above) and leave it running.
3. In the app, open the connection screen (tap the status chip at the top of the capture screen) and tap **Make discoverable to pair**.
4. On the PC: Settings -> Bluetooth & devices -> Add device -> Bluetooth -> select the phone. This is now a plain Bluetooth pairing (no PIN typing needed for most setups) - not the fussier "pair with a keyboard" flow.
5. Back in the app's connection screen, tap **Refresh** under "Paired devices" - the PC should now appear in the list. Tap **Connect** next to it.
6. The screen should show "Connected to: <PC name>". This connection step is a one-time thing per session - if the companion program or the app restarts, just tap Connect again (no need to re-pair from scratch).

## Using it

1. Point the camera at the document and tap **Scan**.
2. Review the extracted text and its confidence level bar on the next screen; edit the text if OCR made mistakes.
3. Tap **Approve & Send to PC** and confirm the popup.
4. The text is typed into whatever has keyboard focus on the connected PC, followed by a Tab keystroke (so, e.g., an Excel selection advances to the next cell).

## Known limitations

- Only one PC can be connected at a time.
- The Windows companion program must be running on the target PC for text delivery to work - it's a small, always-listening background program (see its README for running it automatically at Windows startup, if wanted).
- Typing speed includes a small delay between keystrokes (8ms) to avoid dropped characters; very long extracted text will take a few seconds to fully type out.
- OCR runs fully on-device via ML Kit (no internet required), which favors printed/typed Latin-script text; accuracy on handwriting will be lower. ML Kit's confidence score is also known to be unpopulated on some Play Services versions - the app shows "Unavailable" rather than a fabricated percentage in that case.

## A note on this repository's build/verification status

This project was authored in an isolated cloud sandbox whose network policy blocks Google's Android artifact host (`dl.google.com`), so the **Android app** could not be compiled to an APK from within that sandbox - it was written and carefully reviewed, then fixed against real compile errors once built on an actual developer machine (see git history for the specific fixes that came out of that first real build). The **Windows companion program**, by contrast, *was* fully compiled and verified in that same sandbox (`dotnet build` succeeded with 0 errors) since .NET tooling and NuGet were reachable there - but it hasn't yet been run against real Bluetooth hardware, so treat the first real end-to-end test as the true first run of that half.
