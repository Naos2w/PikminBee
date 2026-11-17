# NaosGPS TODO

## v0.1 — Foundation (first milestone)
- [x] Independent Android project under `personal/NaosGPS`.
- [x] Minimal CI and branch policy documentation.
- [x] Initial Compose map-first UI with iOS-27-inspired frosted controls (compiled; visual/UAT verification pending).
- [x] OpenFreeMap map integration + light/dark/system styles (compiled; device rendering pending).
- [x] Official Google Maps SDK provider, gated on untracked user API key (no key configured yet).
- [x] Latitude/longitude input and map-point teleport via explicit Android mock-location service (device test pending).
- [x] Basic on-device favourites with SharedPreferences/JSON (migrate to Room before v1.0).
- [ ] On-device build, screenshot and permission flow UAT.

## v0.2 — Walking and flower-search
- [ ] Multi-point route builder; pedestrian roads through Valhalla (do not silently fall back to straight line).
- [ ] Start / pause / resume / stop / clear; stop stays at last virtual position, restore real GPS is separate.
- [ ] Flower Search integration after reviewing upstream source licensing.
- [ ] Saved walking routes, loop mode and GPX import/export.

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
- [x] One local Gradle build + lint + 3 unit tests passed; one PR workflow authored (GitHub validation pending).
- [x] Ignore local secret/keystore files in Git; production signing not configured.
- [ ] UAT on POCO before `staging → main` merge.
- [ ] Preserve dependency licences and attribution when reusing compatible upstream code.

Notes: a mock GPS location does not guarantee acceptance by third-party location checks and may breach their rules. Avoid claiming automatic background persistence after system force-stop.
