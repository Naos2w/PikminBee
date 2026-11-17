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
- [x] Manual flower-point selection with pedestrian road loop; [ ] verified automatic Big Flower discovery, upstream licensing and service limits.
- [x] Route/flower drafts saved locally, flower loop playback and speed presets; [ ] named route library and GPX import/export.

## v0.3 — Overlay and persistence
- [ ] Floating overlay controls sharing exactly one GPS state machine.
- [x] Basic location foreground service and explicit stop notification action; [ ] lifecycle/restart and HyperOS UAT.
- [ ] HyperOS lock-in-recents guidance and realistic force-stop limitations.

## v0.4 — LAN browser controller
- [ ] Opt-in Wi-Fi/LAN endpoint; off by default.
- [ ] PIN pairing, rotating session tokens, device approval, request authentication, rate limits.
- [ ] Live position/status and route editing through a mobile-friendly local web UI.
- [ ] Google Maps on the browser only with separate authorised web key / billing; OpenFreeMap by default.

## Release gates
- [x] No automatic production release or installation.
- [x] One low-cost PR workflow: Draft skips Android jobs, superseded runs cancelled, lint/tests for ready PRs, APK packaged only by explicit workflow_dispatch option. Local tests added for walking geometry and polyline6; device UAT still pending.
- [x] Ignore local secret/keystore files in Git; production signing not configured.
- [ ] UAT on POCO before `staging → main` merge.
- [ ] Preserve dependency licences and attribution when reusing compatible upstream code.

Notes: a mock GPS location does not guarantee acceptance by third-party location checks and may breach their rules. Avoid claiming automatic background persistence after system force-stop.
