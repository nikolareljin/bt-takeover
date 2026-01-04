bt-takeover — Bluetooth takeover + WAV blast (Windows + Android)

Overview
- Minimal Windows app to help locate and take back your own Bluetooth headphones by scanning/pairing to a known device ID/MAC and playing a WAV file to them at maximum device volume.
- Android companion app (Android 15+) that routes audio to the current media output (Bluetooth if connected) and plays a chosen MP3/WAV file or white noise at max media volume.
- Windows: works as a portable app (single-file publish). Android: ships as an APK.
- Docs: see `docs/README.md` for feature details and external references.

Important notes
- Only use this on devices you own or are authorized to access. Headphones connected to another phone/PC may refuse connection until that device disconnects.
- Loud playback can damage hearing. Confirm volume before blasting and use responsibly.

Features
- Windows: scan by MAC or name snippet; attempts pairing if supported; plays a .wav via WASAPI to the device endpoint; forces endpoint volume to 100%; loop toggle.
- Android: choose MP3/WAV or fall back to white noise; loop toggle; stores device ID/MAC and selected audio; checks A2DP connection to your target device and shows routing hint. Note: standard Android apps cannot force A2DP pairing/connection; pair/connect from system UI first.

Usage
1) Build (Windows, .NET 8 SDK required):
   - Open a Developer PowerShell on Windows and run from the repo root:
     - `cd bt-takeover/src`
     - `dotnet restore`
     - `dotnet build -c Release`

2) Portable (USB) publish (EXE):
   - From `bt-takeover/src` on Windows:
     - `dotnet publish -c Release -r win-x64 /p:PublishSingleFile=true /p:SelfContained=true -o ..\publish`
   - Copy the contents of `bt-takeover\publish` to your USB stick and run `BtTakeover.exe` from there.

3) Run (Windows):
   - Launch `BtTakeover.exe`.
   - Paste your device identifier in “Device ID or MAC”. Examples:
     - MAC: `00:1A:7D:DA:71:13` or `001A7DDA7113`
     - Windows DeviceId fragment: something like `BTHENUM\\..._001A7DDA7113` (the app extracts the MAC).
     - Name snippet: e.g., `WH-1000XM4` (will match by device name if MAC not detected).
   - Click “Scan & Pair”. Once found/paired, choose your `.wav` via Browse.
   - Optionally enable “Loop”.
  - Click “Play (Max Volume)” to set the endpoint volume to 100% and play the WAV to that device. While playing, the button becomes “Stop”.

Build scripts (cross‑platform)
- Linux/macOS: `./build.sh` (options: `-c Debug|Release`, `--publish true|false`, `-r win-x64`)
- Windows PowerShell: `./build.ps1` (params: `-Configuration Debug|Release`, `-Publish:$true|$false`, `-Runtime win-x64`)
- Both scripts auto‑use `script-helpers` if present (git submodule `git@github.com:nikolareljin/script-helpers.git`).

Android build quickstart
- Ensure Android SDK installed with API 35 (Android 15), and Java 17+ is on PATH.
- Use the Gradle wrapper from `android/gradlew`.
- Commands:
  - `cd android`
  - `./gradlew :app:assembleDebug` → `android/app/build/outputs/apk/debug/app-debug.apk`
  - `./gradlew installDebug` (installs to a connected device via adb)
  - For release: `./gradlew :app:assembleRelease` then sign/align with your keystore.

Android Play Store automation
- Tag-driven release: pushing a tag `X.Y.Z` triggers `.github/workflows/android-play-release.yml` to:
  - Compute `versionName=X.Y.Z` and `versionCode=(X*10000 + Y*100 + Z)`.
  - Build a release App Bundle (`.aab`).
  - Sign the bundle with your upload keystore.
  - Create a GitHub Release and attach the AAB.
  - Upload the AAB to Google Play (track: internal).
- Required GitHub Secrets:
  - `ANDROID_KEYSTORE_BASE64`: Base64-encoded JKS keystore for signing uploads.
  - `ANDROID_KEYSTORE_PASSWORD`: Keystore password.
  - `ANDROID_KEY_ALIAS`: Key alias.
  - `ANDROID_KEY_PASSWORD`: Key password.
  - `PLAY_SERVICE_ACCOUNT_JSON`: Contents of the Google Play Developer API service account JSON.
- Package name: `com.bttakeover.app` (change in `android/app/build.gradle` and workflow env if needed).
- Default tag-based deploy goes to `internal`. You can also run the workflow manually (Actions → Android Play Store Release → Run) and provide:
  - `version` (X.Y.Z)
  - `track` (internal, beta, production)

Android usage
- Pair/connect your headphones from Android Settings first.
- Enter your Device ID/MAC (or a name snippet like WH-1000XM4) and tap “Scan & Check” to verify connection.
- Optionally, tap “Pick from paired” to select from your bonded devices, or “Open Bluetooth settings” to pair/connect.
- Tap “Browse” to pick an MP3/WAV (persisted). If none selected, the app plays white noise.
- Choose a source: White noise (default) or Audio file. If you pick Audio file, select one using Browse.
- Tap “Play (Max Volume)” to start; use “Loop” to repeat; tap Stop to end.

Submodules
- Initialize locally: `git submodule update --init --recursive`
- In CI, workflows fetch submodules automatically and fall back to cloning if the submodule is not yet committed.

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
- Place `BtTakeover.config.json` next to `BtTakeover.exe`. If present, the app loads defaults for the device ID and WAV path at startup and can auto-start playback.
- `AutoStart` (default true when omitted) triggers scan/pair and playback on launch if both `BluetoothId` and a valid `AudioFile` are set.
- `Loop` (default true when omitted) controls whether auto-start playback loops continuously.
- `Persistent` (default true when omitted) makes the app rescan periodically until the device is found; useful for USB “drop-in” usage.
- `RescanSeconds` (default 30) interval between scans in persistent mode.
- `VolumePercent` (default 100) sets a linear gain multiplier applied in software on top of the endpoint volume. Values >100 enable overdrive (e.g., 180 = 1.8x). High values may clip and be extremely loud. Capped at 250%.
- Relative `AudioFile` paths are resolved relative to the EXE folder.
- Example `BtTakeover.config.json`:
  {
    "AutoStart": true,
    "Loop": true,
    "Persistent": true,
    "RescanSeconds": 30,
    "VolumePercent": 100,
    "BluetoothId": "00:1A:7D:DA:71:13",
    "AudioFile": "alert.wav"
  }

CI/CD (releases)
- Pushing a tag like `1.2.3` builds:
  - A Windows 11 (win-x64) self-contained single-file EXE ZIP.
  - An MSIX package (signed with a CI self-signed certificate) for side-loading.
- The ZIP includes `BtTakeover.exe`, `BtTakeover.config.sample.json`, and `README.md`.
- The release also contains the MSIX and a `BtTakeover.cer` you can install to trust the package.
 - A helper script `install-sideload.ps1` is included to install the certificate and MSIX in one step.

MSIX install notes
- Side-loading: install `BtTakeover.cer` to Local Machine > Trusted People, then double-click the `.msix` or use `Add-AppxPackage`.
- Windows 11 S Mode: does not allow side-loading or running desktop EXEs. To run on S Mode, the app must be distributed via the Microsoft Store and signed with a Store certificate.
 - One-step (admin PowerShell):
   Set-ExecutionPolicy Bypass -Scope Process -Force; ./install-sideload.ps1

Microsoft Store packaging & submission
- Workflow: run `.github/workflows/store.yml` via “Run workflow” and provide a `version` (e.g., 1.2.3). It builds a Store-identity MSIX.
- Required repository secrets:
  - `STORE_IDENTITY_NAME` (reserved package identity, e.g., `12345YourOrg.BtTakeover`).
  - `STORE_PUBLISHER` (exact Publisher string from Partner Center, e.g., `CN=Your Org, O=Your Org, L=City, S=State, C=US`).
  - `STORE_PUBLISHER_DISPLAY_NAME` (friendly name shown in Store).
  - Optional for auto-submit: `SUBMIT_TO_STORE` = `true`, `STORE_APP_ID` (GUID), `PARTNER_TENANT_ID`, `PARTNER_CLIENT_ID`, `PARTNER_CLIENT_SECRET` (Azure AD app for Partner Center).
- Output: uploads a Store-ready `.msix` as a build artifact.
- Optional auto-submit: if `SUBMIT_TO_STORE` is `true` and Partner Center secrets are set, the workflow attempts to create, upload, and commit a submission via the Partner Center API.
- Note: API contracts can change; if submission fails, download the MSIX artifact and upload it manually in Partner Center.
4) Build APK (Android 15+):
   - Prereqs: Android SDK/NDK and Java 17+; Gradle wrapper available at `android/gradlew`.
   - From repo root:
     - `cd android`
     - Ensure compileSdk/targetSdk 35 are available in your SDK Manager.
     - Build debug: `./gradlew :app:assembleDebug`
       - Output: `android/app/build/outputs/apk/debug/app-debug.apk`
     - Build release: `./gradlew :app:assembleRelease`
       - Output: `android/app/build/outputs/apk/release/app-release-unsigned.apk`
     - Sign & align release (example):
       - `apksigner sign --ks your.keystore --out app-release.apk android/app/build/outputs/apk/release/app-release-unsigned.apk`
   - Install: `./gradlew installDebug` or `adb install -r path/to/app-debug.apk`

Android usage
- Pair/connect your headphones from Android Settings first.
- Open BT Takeover and tap “Play (Max Volume)”. Audio routes to the current media output; if a Bluetooth A2DP device is connected, it will receive the noise. Tap Stop to end.

App icon
- A simple “headphones with noise” logo is included as an SVG at `assets/logo/headphones-noise.svg` and used by Android as a vector.
- Windows EXE uses the same icon via `src/Assets/AppIcon.ico` (committed in the repo and referenced in the project).
- If you ever need to re-generate the ICO from the SVG, build scripts can help (optional):
  - Windows: install ImageMagick, then run `./build.ps1` (will generate if missing).
  - Linux/macOS: install ImageMagick, then run `./build.sh` (will generate if missing).
  - Manual command example (ImageMagick):
    `convert -background none -density 384 assets/logo/headphones-noise.svg -define icon:auto-resize=256,128,64,48,32,16 src/Assets/AppIcon.ico`
  - Visual Studio users can also set the icon in Project Properties; the `.csproj` references the ICO (and will pick it up if replaced).

Android compatibility
- minSdk 23 (Android 6.0), targetSdk 35. Some features (permissions, device routing APIs) adjust based on OS version.
- The app cannot pair/connect A2DP programmatically (restricted). It checks whether your configured device is currently connected; use system UI to pair/connect.
