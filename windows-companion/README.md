# AI Scan Receiver (Windows companion program)

A small background program that receives scanned/OCR'd text from the AI Scan Android app over Bluetooth and types it out locally - wherever the cursor/focus currently is (an Excel cell, a text field, anything).

It does not use the internet or any cloud service. Everything happens over a direct Bluetooth connection to the paired phone.

Runs from the **system tray** - no terminal window to keep open, and it registers itself to **start automatically at Windows login** the first time it runs, so nobody needs to remember to launch it by hand afterward.

## Requirements

- Windows 10 or 11
- A Bluetooth adapter (built-in or USB)
- [.NET 8 SDK](https://dotnet.microsoft.com/download) installed (only needed to build it once - see below)

## Running it (first time)

```powershell
cd windows-companion
dotnet run
```

There's no console output to watch - instead, a small icon appears in the system tray (near the clock, may be under the "^" overflow arrow). Hover over it to see status, right-click for an Exit option. Status changes (connected, typing, errors) also show briefly as Windows notification popups.

After this first run, the program has added itself to Windows' startup list - it'll already be running the next time you log in, without needing `dotnet run` again. (See "Building a standalone .exe" below for the more typical way to run it day-to-day, without needing the .NET SDK installed at all.)

## First-time pairing

1. Start this program (as above) and confirm the tray icon appears.
2. On the phone, in the AI Scan app's connection screen, tap **Make discoverable to pair**.
3. On this PC: Settings -> Bluetooth & devices -> Add device -> Bluetooth -> select the phone. Accept any confirmation prompt (no PIN typing needed for most setups).
4. Back on the phone's connection screen, tap **Refresh**, then tap **Connect** next to this PC.
5. The phone should show "Connected to: <this PC's name>", and the tray icon's notification popup should say `Connected: <phone's name>`.

After that first pairing, you don't need to repeat the "Add device" step again on this PC - just make sure this program is running (check the tray), and tap Connect on the phone's connection screen (a fresh connection is opened each time this program or the app restarts).

## Building a standalone .exe (recommended for day-to-day use)

So the .NET SDK doesn't need to be installed on every PC that uses this:
```powershell
dotnet publish -c Release -r win-x64 --self-contained -p:PublishSingleFile=true
```
The resulting `ScanHidReceiver.exe` (under `bin/Release/net8.0-windows/win-x64/publish/`) can be copied anywhere and double-clicked directly - no .NET SDK required on that machine (the runtime is bundled in), no console window appears, and running it once still registers it for auto-start at login exactly as above.

## Turning off auto-start

Right-click the tray icon and choose Exit, then remove the startup entry:
```powershell
Remove-ItemProperty -Path "HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Run" -Name "AIScanReceiver"
```

## How it works, technically

- Runs as a WinForms `ApplicationContext` with a `NotifyIcon` and no main window (`OutputType=WinExe`, so no console is allocated at all).
- On first launch, writes itself into `HKEY_CURRENT_USER\SOFTWARE\Microsoft\Windows\CurrentVersion\Run` so Windows starts it automatically at every subsequent login - a one-time, silent, per-user action (doesn't require admin rights).
- Hosts a Bluetooth RFCOMM (Serial Port Profile) service using a fixed UUID that the Android app connects to as a client.
- Reads incoming messages as a 4-byte big-endian length header followed by that many UTF-8 bytes (one message per approved scan).
- Types the received text using the Win32 `SendInput` API with `KEYEVENTF_UNICODE`, which injects the literal Unicode character regardless of keyboard layout - so accented letters, non-Latin scripts, etc. all work, not just ASCII. `\n` and `\t` are sent as real Enter/Tab key presses instead of literal control characters, since some apps only respond to the actual key press.

## A note on this update's verification status

Unlike the original console version (which was fully compiled and verified), this tray-app rewrite could not be compiled in the sandbox it was written in - building a WinForms app requires the Windows Desktop SDK component, which isn't available on Linux (not even as an installable workload). It was written carefully and reviewed by hand instead. Treat your first `dotnet run` after pulling this update as the real first compile/test - if it doesn't build cleanly, send the exact error and it can be fixed directly, the same way a couple of real issues in the Android app were caught and fixed on the first real build there.
