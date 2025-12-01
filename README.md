bt-takeover — Bluetooth takeover + WAV blast (Windows)

Overview
- Minimal Windows app to help locate and take back your own Bluetooth headphones by scanning/pairing to a known device ID/MAC and playing a WAV file to them at maximum device volume.
- Works as a portable app (publish single-file, copy to USB). Designed for Windows 11.

Important notes
- Only use this on devices you own or are authorized to access. Headphones connected to another phone/PC may refuse connection until that device disconnects.
- Loud playback can damage hearing. Confirm volume before blasting and use responsibly.

Features
- Scan for a Bluetooth device by MAC address or by name snippet; also accepts common Windows DeviceId strings (extracts MAC if present).
- Attempts pairing if not already paired (SSP / no PIN if supported).
- Plays a given .wav file directly to the device’s audio endpoint via WASAPI (no need to change the system default).
- Sets the device endpoint volume to 100% before playback.
- Loop playback toggle with in-app Stop control.

Usage
1) Build (Windows, .NET 8 SDK required):
   - Open a Developer PowerShell on Windows and run from the repo root:
     - `cd bt-takeover/src`
     - `dotnet restore`
     - `dotnet build -c Release`

2) Portable (USB) publish:
   - From `bt-takeover/src` on Windows:
     - `dotnet publish -c Release -r win-x64 /p:PublishSingleFile=true /p:SelfContained=true -o ..\publish`
   - Copy the contents of `bt-takeover\publish` to your USB stick and run `BtTakeover.exe` from there.

3) Run:
   - Launch `BtTakeover.exe`.
   - Paste your device identifier in “Device ID or MAC”. Examples:
     - MAC: `00:1A:7D:DA:71:13` or `001A7DDA7113`
     - Windows DeviceId fragment: something like `BTHENUM\\..._001A7DDA7113` (the app extracts the MAC).
     - Name snippet: e.g., `WH-1000XM4` (will match by device name if MAC not detected).
   - Click “Scan & Pair”. Once found/paired, choose your `.wav` via Browse.
   - Optionally enable “Loop”.
   - Click “Play (Max Volume)” to set the endpoint volume to 100% and play the WAV to that device. While playing, the button becomes “Stop”.

Limitations & tips
- If the headphones are actively connected to another device, Windows usually cannot take over until that device releases the connection. Try toggling the headphones off/on nearby.
- Bluetooth discovery can take ~10–30 seconds. Try multiple scans if the device is intermittent.
- Some devices expose multiple audio endpoints (Hands-Free vs A2DP). The app picks the render endpoint whose name matches the device name; you can rescan if Windows renames endpoints.

Security/Privacy
- The app does not store credentials or collect data. Pairing is delegated to the Windows Bluetooth stack.

Tech stack
- .NET 8 Windows Forms
- 32feet.NET for Bluetooth discovery/pairing
- NAudio CoreAudio/WASAPI for endpoint selection, volume, playback

Configuration file (optional)
- Place `BtTakeover.config.json` next to `BtTakeover.exe`. If present, the app loads defaults for the device ID and WAV path at startup.
- Relative `AudioFile` paths are resolved relative to the EXE folder.
- Example `BtTakeover.config.json`:
  {
    "BluetoothId": "00:1A:7D:DA:71:13",
    "AudioFile": "alert.wav"
  }
