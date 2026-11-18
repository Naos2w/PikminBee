# PikminBee TODO

## v0.1 — Foundation (first milestone)
- [x] PikminBee branding: original bee icon and a nectar-collecting exploration concept, without game assets.
- [x] Independent Android project under `personal/PikminBee`.
- [x] Minimal CI and branch policy documentation.
- [x] Initial Compose map-first UI with iOS-27-inspired frosted controls (compiled; visual/UAT verification pending).
- [x] OpenFreeMap map integration + light/dark/system styles (compiled; device rendering pending).
- [x] Keyless OpenFreeMap as the default and required map provider. Bright / Positron / Liberty / Dark styles and safe fallback from previously saved Google preference are implemented; Google Maps SDK appears only with a configured restricted key. No undocumented Google tiles. [ ] Phone map-style and tile attribution UAT. See [MAP-BASEMAPS.md](MAP-BASEMAPS.md).
- [x] Latitude/longitude input and map-point teleport via explicit Android mock-location service (device test pending).
- [x] Basic on-device favourites with SharedPreferences/JSON (migrate to Room before v1.0).
- [ ] On-device build, screenshot and permission flow UAT.

## v0.2 — Walking and flower-search
- [x] Multi-point route builder with map tap ordering and Valhalla pedestrian routing; fails visibly instead of walking straight (code + unit tests, device UAT pending).
- [x] Shared GPS service with start / pause / resume / stop / clear; stop keeps last virtual position, restoring real GPS remains separate (code tested, device UAT pending).
- [x] Manual flower-point selection and pedestrian road loop. [x] Map-first flower route editor now distinguishes selecting a point from confirming it, uses an explicit start (or active mock location), supports ordered stop removal/reordering and read-only Valhalla road preview before starting; phone UI/network UAT pending. [x] Offline user-maintained flower catalogue (local records, nearby user-defined spots, JSON/GeoJSON); **no Overpass/OSM candidate service will be integrated** per current requirement. [x] Original read-only screen flower-shape recognition prototype (user-selected image or per-session MediaProjection consent, no automatic GPS conversion). [ ] Real screenshot precision/recall, reliable pixel-to-coordinate calibration and POCO projection/permissions UAT. See [FLOWER-VISION.md](FLOWER-VISION.md) and [FLOWER-CATALOG.md](FLOWER-CATALOG.md).
- [x] Direct-on-map flower route stop editing in a separate Draft PR: tap an existing route pin or list entry, select a replacement location and explicitly confirm, or remove/cancel; preserve other stops and reject duplicate/near-start replacements. Local code/unit tests; actual marker tap interaction remains for grouped POCO UAT.
- [x] Map camera and immersive map controls: temporarily hide large overlays, fit active pedestrian geometry or draft, focus origin or selected point, auto-fit successful flower road preview, avoid auto-following changing GPS, and prefer existing route stops when flower markers overlap. Pure Kotlin bounds tests and Android source verification passed; native provider rendering / compact overlay accessibility remain POCO UAT work.
- [x] Route/flower drafts saved locally, flower loop playback and speed presets; named route library (save/rename/load/delete) and GPX 1.1 route/track import/export are implemented with unit tests. [ ] POCO file picker/UI UAT.

## v0.3 — Overlay and persistence
- [x] Basic draggable bee overlay sharing the existing GPS foreground service, with Pause/Resume/Stop/Map/Hide/Restore actions; [x] one-tap "開始尋花" shortcut uses the saved flower draft, current explicit simulated position and Valhalla pedestrian loop (1.4 m/s), with cancel/stop and stale-result rejection; no second GPS engine. [ ] Device-level interaction, permission, background and route-planning UAT.
- [x] Foreground GPS service + notification actions (pause/resume/stop/restore), 5-second last-coordinate journal and safe START_STICKY null-Intent recovery as PARKED; [ ] on-device lifecycle/restart UAT.
- [x] In-app HyperOS/POCO background guidance, standard Android app settings link and user-triggered permission flow; force-stop/reboot limitations documented in [BACKGROUND-LIFECYCLE.md](BACKGROUND-LIFECYCLE.md). [ ] POCO/UAT verification.

## v0.4 — LAN browser controller
- [x] Opt-in private-Wi-Fi IPv4 HTTP endpoint (off by default, bound only to Wi-Fi IP); [ ] POCO network UAT.
- [x] Six-digit PIN + on-phone device approval, memory-only expiring sessions, IP binding, Origin/Host checks, rate limits and Wi-Fi-loss shutdown; [ ] security and cross-device UAT.
- [x] Responsive keyless OpenFreeMap web page (Auto/Liberty/Bright/Positron/Dark), map-pick teleport, route/flower stops, controls and 2-second state polling, with shared phone/web route state. [x] Remote API now reuses native flower draft safety limits (14 flower points, start/duplicate rejection), and validates road loop capacity/speed before routing. [ ] Browser/device UAT and optional WebSocket if needed.
- [ ] Optional Google Maps on browser with separate authorised web key/billing; OpenFreeMap is the only browser map initially. [x] MapLibre GL JS 6.18.0 is now vendored locally with hash tests and its BSD-3-Clause licence; map tiles still need internet.

## v0.4 follow-ups
- [ ] Verify private Wi-Fi phone-to-MSI pairing, Origin/Host rejection, disconnect and phone approval in real Android/Chrome.
- [ ] Evaluate local HTTPS or other encrypted transport before public or untrusted-network use; LAN HTTP is not encrypted.
- [ ] When targetSdk reaches 37, declare and request Android 17 `ACCESS_LOCAL_NETWORK` runtime permission.
- [x] Persist named routes and GPX using Android SAF (no storage permission); [ ] verify on real device. [x] Evaluated flower data sources in [FLOWER-SOURCE-EVALUATION.md](FLOWER-SOURCE-EVALUATION.md); official real-time Big Flower API remains unverified and is not integrated.

## UAT preflight (source quality checks)
- [x] Bundled fixed-version MapLibre GL JS/CSS locally and preserved its third-party licence; hardened the local web Content-Security-Policy (no remote executable scripts).
- [x] MSI local `:app:testDebugUnitTest :app:lintDebug --offline` passed (48/48 tests, 0 failed); node syntax and vendored asset SHA-256 verified. [ ] Real POCO UAT still pending. See [PRE-UAT-QUALITY.md](PRE-UAT-QUALITY.md).
- [ ] Perform one grouped POCO UAT: maps, permissions, GPS, routes, GPX, overlay, LAN pairing and UI.
- [ ] Check Google Maps API key restriction, map attribution, source licence notices and third-party dependencies before PROD.

## Flower vision backlog (separate PRs, not part of the initial recognition prototype)
- [ ] Test with user-authorised real-world frames; tune colour, lighting and pale-flower false positives.
- [x] Added a memory-only, read-only still-image pixel-to-GPS calibration preview (3–16 user-entered landmarks; similarity transform; leave-one-out RMSE and out-of-hull indication). Unit-tested only; not measured real-game accuracy. No automatic route insertion.
- [x] Added an independent Draft PR for user-picked still-image tap-to-select calibration landmarks: displays a transient downsampled image, maps taps correctly across Fit letterboxing, and retains manual X/Y entry. Code and unit tests only; actual phone touch/UI behaviour remains pending integrated UAT.
- [ ] Validate actual pixel-to-coordinate accuracy on independent user-authorised reference data before suggesting GPS route waypoints. See [FLOWER-VISION.md](FLOWER-VISION.md).
- [ ] Verify Android 14+/HyperOS MediaProjection consent, app-window capture, orientation and cancellation on POCO during UAT.
- [x] Do not integrate Overpass/OSM POI candidate search; favour image-based recognition as requested.

## Release gates
- [x] No automatic production release or installation.
- [x] One low-cost PR workflow: Draft skips Android jobs, superseded runs cancelled, lint/tests for ready PRs, APK packaged only by explicit workflow_dispatch option. Local tests added for walking geometry and polyline6; device UAT still pending.
- [x] Ignore local secret/keystore files in Git; production signing not configured.
- [ ] UAT on POCO before `staging → main` merge.
- [ ] Preserve dependency licences and attribution when reusing compatible upstream code.

Notes: a mock GPS location does not guarantee acceptance by third-party location checks and may breach their rules. Avoid claiming automatic background persistence after system force-stop.
