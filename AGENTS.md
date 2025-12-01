# Repository Guidelines

## Project Structure & Module Organization
- `src/` — .NET 8 Windows Forms app (`BtTakeover.csproj`, `Program.cs`).
- `src/UI/` — UI layer: `MainForm.cs` (logic) and `MainForm.Designer.cs` (auto‑generated).
- `src/core/` — Core helpers: `BluetoothHelper.cs`, `AudioHelper.cs`.
- `publish/` — Created by publish commands (not tracked).

## Build, Test, and Development Commands
- Restore/build (Windows): `cd src && dotnet restore && dotnet build -c Debug`
- Run locally (Windows): `cd src && dotnet run -c Debug`
- Release build: `cd src && dotnet build -c Release`
- Portable publish (single file):
  `cd src && dotnet publish -c Release -r win-x64 /p:PublishSingleFile=true /p:SelfContained=true -o ../publish`

## Coding Style & Naming Conventions
- C#/.NET: target `net8.0-windows`, nullable enabled, implicit usings on.
- Indentation 4 spaces; braces on new lines; prefer `var` for locals.
- Naming: PascalCase for types/methods/properties; camelCase for parameters; prefix private fields with `_` (e.g., `_audio`).
- UI code: do not hand‑edit `*.Designer.cs`; place handlers and UI logic in `MainForm.cs` (partial class). Keep business logic in `src/core/`.
- Dependencies: NAudio (audio), InTheHand.Net.Bluetooth (Bluetooth). Avoid adding new packages without discussion.

## Testing Guidelines
- No automated tests currently. If adding tests, use xUnit in `tests/` or `src/BtTakeover.Tests/` with names like `BluetoothHelperTests.cs`.
- Run tests: `dotnet test` at repo root once a test project exists.
- Manual checks for PRs touching device logic: pair to a known headset, verify playback (non‑loop and loop), and confirm endpoint volume behavior.

## Commit & Pull Request Guidelines
- Commits: small, focused, imperative mood with scope, e.g., `core: improve Bluetooth discovery` or `UI: fix loop toggle state`.
- PRs must include: description, before/after notes or screenshots for UI changes, Windows version tested, repro/verify steps, and linked issues. Update `README.md` if build/publish behavior changes.

## Security & Configuration Tips
- Use only with devices you own/are authorized to access. Loud playback can harm hearing—verify volume first.
- Windows 11 + .NET 8 SDK required for development; target `win-x64` for publishing.
- Do not commit `*.csproj.user`, publish artifacts, or secrets.

## Agent‑Specific Instructions
- Keep changes minimal and scoped; match existing structure and style.
- Never edit `*.Designer.cs` by hand; use the partial pattern.
- Avoid blocking the UI thread; prefer async patterns for I/O/device work.
- Do not upgrade package versions or add dependencies without justification.
