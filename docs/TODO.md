# PikminBee TODO

## v0.1 — Foundation (first milestone)
- [x] PikminBee branding: original bee icon and a nectar-collecting exploration concept, without game assets.
- [x] Independent Android project under `personal/PikminBee`.
- [x] Minimal CI and branch policy documentation.
- [x] Initial Compose map-first UI with iOS-27-inspired frosted controls (compiled; visual/UAT verification pending).
- [x] OpenFreeMap map integration + light/dark/system styles (compiled; device rendering pending).
- [x] Official Google Maps SDK provider, gated on untracked user API key (no key configured yet).
- [x] Latitude/longitude input and map-point teleport via explicit Android mock-location service (device test pending).
- [x] Basic on-device favourites with SharedPreferences/JSON (migrate to Room before v1.0).
- [ ] On-device build, screenshot and permission flow UAT.

## v0.2 — Walking and flower-search
- [x] Multi-point route builder with map tap ordering and Valhalla pedestrian routing; fails visibly instead of walking straight (code + unit tests, device UAT pending).
- [x] Shared GPS service with start / pause / resume / stop / clear; stop keeps last virtual position, restoring real GPS remains separate (code tested, device UAT pending).
- [x] Manual flower-point selection and pedestrian road loop. [x] Offline user-maintained flower catalogue: 500 m / 2 km / 5 km search, near-stop selection, 25 m deduplication, 1,000-record storage, explicit personally-checked vs unverified labels, JSON/GeoJSON file import/export. [ ] Automatic official Big Flower discovery: no verified authorised public source found. [ ] POCO map/UI/SAF UAT. See [FLOWER-CATALOG.md](FLOWER-CATALOG.md).
- [x] Route/flower drafts saved locally, flower loop playback and speed presets; named route library (save/rename/load/delete) and GPX 1.1 route/track import/export are implemented with unit tests. [ ] POCO file picker/UI UAT.

## v0.3 — Overlay and persistence
- [x] Basic draggable bee overlay sharing the existing GPS foreground service, with Pause/Resume/Stop/Map/Hide/Restore actions; [ ] device-level interaction and permission UAT.
- [x] Foreground GPS service + notification actions (pause/resume/stop/restore), 5-second last-coordinate journal and safe START_STICKY null-Intent recovery as PARKED; [ ] on-device lifecycle/restart UAT.
- [x] In-app HyperOS/POCO background guidance, standard Android app settings link and user-triggered permission flow; force-stop/reboot limitations documented in [BACKGROUND-LIFECYCLE.md](BACKGROUND-LIFECYCLE.md). [ ] POCO/UAT verification.

## v0.4 — LAN browser controller
- [x] Opt-in private-Wi-Fi IPv4 HTTP endpoint (off by default, bound only to Wi-Fi IP); [ ] POCO network UAT.
- [x] Six-digit PIN + on-phone device approval, memory-only expiring sessions, IP binding, Origin/Host checks, rate limits and Wi-Fi-loss shutdown; [ ] security and cross-device UAT.
- [x] Responsive OpenFreeMap Liberty/Dark web page, map-pick teleport, route/flower stops, controls and 2-second state polling, with shared phone/web route state; [ ] browser/device UAT and optional WebSocket if needed.
- [ ] Optional Google Maps on browser with separate authorised web key/billing; OpenFreeMap is the only browser map initially. Browser loads pinned MapLibre JS from CDN (internet required).

## v0.4 follow-ups
- [ ] Verify private Wi-Fi phone-to-MSI pairing, Origin/Host rejection, disconnect and phone approval in real Android/Chrome.
- [ ] Evaluate local HTTPS or other encrypted transport before public or untrusted-network use; LAN HTTP is not encrypted.
- [ ] When targetSdk reaches 37, declare and request Android 17 `ACCESS_LOCAL_NETWORK` runtime permission.
- [x] Persist named routes and GPX using Android SAF (no storage permission); [ ] verify on real device and source licensing for game-specific flower discovery.

## Release gates
- [x] No automatic production release or installation.
- [x] One low-cost PR workflow: Draft skips Android jobs, superseded runs cancelled, lint/tests for ready PRs, APK packaged only by explicit workflow_dispatch option. Local tests added for walking geometry and polyline6; device UAT still pending.
- [x] Ignore local secret/keystore files in Git; production signing not configured.
- [ ] UAT on POCO before `staging → main` merge.
- [ ] Preserve dependency licences and attribution when reusing compatible upstream code.

Notes: a mock GPS location does not guarantee acceptance by third-party location checks and may breach their rules. Avoid claiming automatic background persistence after system force-stop.
