# AI ScanHid Receiver (Windows companion program)

A small background program that receives scanned/OCR'd text from the AI ScanHid Android app over Bluetooth and types it out locally - wherever the cursor/focus currently is (an Excel cell, a text field, anything).

It does not use the internet or any cloud service. Everything happens over a direct Bluetooth connection to the paired phone.

## Requirements

- Windows 10 or 11
- A Bluetooth adapter (built-in or USB)
- [.NET 8 SDK](https://dotnet.microsoft.com/download) installed

## Running it

```powershell
cd windows-companion
dotnet run
```

Leave the terminal window open - it prints status as things happen:
```
AI ScanHid receiver
====================
Waiting for the phone to connect over Bluetooth...
(Pair the phone with this PC first via Windows Bluetooth settings, if you haven't already.)

[10:32:14] Connected: EDA51-1234
[10:32:41] Received 87 characters - typing now...
[10:32:42] Done.
```

## First-time pairing

1. Start this program (`dotnet run`) and leave it running.
2. On the phone, in the AI ScanHid app's connection screen, tap **Make discoverable to pair**.
3. On this PC: Settings -> Bluetooth & devices -> Add device -> Bluetooth -> select the phone. Accept any confirmation prompt (no PIN typing needed for most setups).
4. Back on the phone's connection screen, tap **Refresh**, then tap **Connect** next to this PC.
5. The phone should show "Connected to: <this PC's name>", and this program's window should print `Connected: <phone's name>`.

After that first pairing, you don't need to repeat the "Add device" step again on this PC - just make sure this program is running, and tap Connect on the phone's connection screen (a fresh connection is opened each time this program or the app restarts).

## Building a standalone .exe (optional)

If you'd rather not need the .NET SDK installed to just run it day-to-day:
```powershell
dotnet publish -c Release -r win-x64 --self-contained -p:PublishSingleFile=true
```
The resulting `ScanHidReceiver.exe` (under `bin/Release/net8.0-windows/win-x64/publish/`) can be copied anywhere and double-clicked directly, no .NET SDK required on that machine (the runtime is bundled in).

## Running it automatically at Windows startup (optional)

Once you're happy it works, you can add a shortcut to the published `.exe` (or a shortcut running `dotnet run` from this folder) into:
```
shell:startup
```
(paste that into the Windows Run dialog, Win+R, to open the Startup folder) so it's always running in the background without you needing to remember to start it.

## How it works, technically

- Hosts a Bluetooth RFCOMM (Serial Port Profile) service using a fixed UUID that the Android app connects to as a client.
- Reads incoming messages as a 4-byte big-endian length header followed by that many UTF-8 bytes (one message per approved scan).
- Types the received text using the Win32 `SendInput` API with `KEYEVENTF_UNICODE`, which injects the literal Unicode character regardless of keyboard layout - so accented letters, non-Latin scripts, etc. all work, not just ASCII. `\n` and `\t` are sent as real Enter/Tab key presses instead of literal control characters, since some apps only respond to the actual key press.
