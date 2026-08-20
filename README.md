# SyncParty

Offline, local-network synchronized playback for Android. One phone hosts,
friends' phones join over local Wi-Fi/hotspot, and everyone's audio/video
plays in sync — **no internet required, ever.**

## Requirements met from the spec

- Fully offline: no Firebase, no cloud servers, no internet dependency (see `TransportChoice.md`)
- Star topology: clients never talk to each other, only through the host
- Each phone routes its own audio output (host does not fan out to every client's Bluetooth device)
- Clock-synchronized scheduled playback (`PLAY_AT`/`PAUSE_AT`/`SEEK_AT`) rather than naive `play()` broadcasts
- Progressive drift correction (rate nudge → hard resync) with a diagnostics screen for on-device testing
- Binary file transfer for local audio/video distribution, separate from the JSON control channel
- Party code doubles as the local auth token (Section 39); message validation, file-size/hash checks

## Module map

```
app/                    Compose UI shell, navigation, ViewModels wiring everything together
core/common/            Data models, wire protocol (SyncMessage), transport interfaces
core/networking/        NSD discovery, TCP host/client transports, wire framing
core/synchronization/   Clock sync (Cristian's algorithm), drift correction, sequence gating
core/playback/          SynchronizedPlayer (Media3/ExoPlayer), scheduled play/pause/seek
core/bluetooth/         Audio output route detection
core/audiotransfer/     SHA-256 hashing, binary chunked file transfer
core/partyengine/       HostPartyEngine / ClientPartyEngine — orchestration layer
service/playback/       Foreground MediaSessionService for background playback
feature/home/           Landing screen (Create Party / Join Party)
feature/createparty/    Party code + QR code card
feature/joinparty/      Discovery list + manual code entry
feature/party/          Host and Client in-party screens
feature/medialibrary/   Local file picker
feature/diagnostics/    Sync/network/clock diagnostics screen
```

## Build

Requires JDK 17 and Android SDK (compileSdk 35, minSdk 26).

```bash
./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`. CI (`.github/workflows/build.yml`)
builds this automatically on every push to `main` and uploads it as an artifact.

To install directly on a connected device:

```bash
./gradlew installDebug
```

## What's implemented vs. what's a documented next step

This is a working MVP through **Phase 6** of the spec's development-phase plan
(local player → local network → room → audio transfer → clock sync →
synchronized playback). Explicitly **not** yet implemented, each left as a
clearly marked extension point rather than silently missing:

- **Host migration** (Section 22) — spec explicitly says to defer this until the
  base system is stable; "party ended" on host disconnect is the current MVP behavior.
- **Audio latency calibration** (Section 32) — spec explicitly marks this as post-MVP.
- **QR code scanning** — the QR code is generated and displayed correctly
  (`CreatePartyCodeCard`); the scan-to-join side has a wired button
  (`onScanQr`) but launching the camera scanner activity itself is a TODO —
  zxing-android-embedded is already a dependency, ready for `IntentIntegrator`.
- **Playback-rate resume-from-pause command** — pause scheduling is implemented;
  a full resume needs a `RESUME_AT` path analogous to `PLAY_AT`, which the
  protocol already has room for.
- **Next/Previous queue** — `TrackInfo`/`NextTrackPrepare` messages exist in the
  protocol (Section 21); the host UI currently treats "Next" as "back to library."

## Testing

Per spec Section 36-37: this must be tested on real physical Android devices
(2, 3, then 4 phones), not emulators, since Bluetooth audio latency and
Wi-Fi behavior vary meaningfully by hardware. The diagnostics screen
(`feature:diagnostics`) is built specifically to support that testing loop.

## License

Unlicensed / private project scaffold — add a LICENSE file appropriate to
your intended distribution.
