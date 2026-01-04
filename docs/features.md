Features

Windows app
- Scan by MAC, device ID fragment, or name snippet to find a target device.
- Attempts pairing when supported by Windows and the device.
- Select a WAV file and play it to the device audio endpoint.
- Forces the endpoint volume to 100% before playback.
- Optional loop playback toggle.
- Optional config file for auto-start, persistent rescan, and volume multiplier.

Android app
- Routes audio to the current system media output (Bluetooth A2DP when connected).
- Two audio sources: White Noise or Audio File (MP3/WAV).
- Loop toggle and persisted preferences.
- Device ID/MAC saved for quick checks and scans.
- Scan and check connected status; open Bluetooth settings when needed.
- Foreground background-scan service for long-running device checks.

Behavior notes
- Android does not allow third-party apps to force A2DP pairing/connection; the user must connect via system UI.
- Windows takeover depends on the target device not being actively connected to another device.
