Windows app

What it does
- Finds a Bluetooth device by MAC or a name/ID fragment.
- Attempts pairing if the device is pairable.
- Locates the matching audio endpoint and plays a WAV file to it.
- Sets the endpoint volume to 100% before playback.
- Supports loop playback.

Why it is implemented this way
- The Windows CoreAudio/WASAPI stack is the supported path for selecting output endpoints and controlling endpoint volume.
- Bluetooth pairing and discovery are delegated to the OS Bluetooth stack for compatibility with different adapters and devices.

Implementation highlights
- Bluetooth discovery and pairing use 32feet.NET (InTheHand.Net).
- Audio playback and endpoint control use NAudio (CoreAudio/WASAPI).
- Config file can drive auto-start, persistent rescan, and a software volume multiplier.
