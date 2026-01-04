Android app

What it does
- Plays audio to the current media output, which is Bluetooth A2DP when connected.
- Two source modes: White Noise or Audio File (MP3/WAV).
- Loop toggle and persistence of selected audio and preferences.
- Scans for a target device and checks connection status.
- Quick access to Bluetooth settings and device pickers.
- Optional background scan service with a foreground notification.

Why it is implemented this way
- Android apps cannot force A2DP pairing or connection; users must connect in system settings.
- Playback uses standard Android media APIs to ensure compatibility across devices.

Implementation highlights
- Audio uses AudioTrack for generated noise and MediaPlayer for file playback.
- Bluetooth scans rely on OS permissions and the system Bluetooth stack.
- Companion Device Manager is used for better device selection UX on supported versions.
