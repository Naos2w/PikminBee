# PikminBee TODO

## Naos Liquid Glass V3 · User-approved A/map-first Android UI (20 Nov 2026)
- [x] DEV 0.3.3: canonical Naos six theme families with Light/Dark and Regular/Clear/Blur, persistent settings, vector icons and low-saturation controls.
- [x] DEV 0.3.3: AGSL source-sampled rounded-edge lens effect on Android 13+ with idle MapLibre/Google snapshot, frosted fallback where unsupported; cross-app overlay uses privacy-safe frosted material.
- [x] DEV 0.3.3: top direct-teleport entry, floating four-mode dock, route undo/start/pause/stop, and compact bee quick actions with full controls behind deliberate expansion.
- [x] Initial Kotlin compile, 152 JVM tests, Android Lint and DEV assemble passed on MSI.
- [ ] PR and CI-gated signed UAT 0.4.1 publication; production `main` unchanged.
- [ ] POCO real-device acceptance for native camera gestures, shader optics, performance, overlay clipping, permission/HC regression, signed UAT install/upgrade; no connected device yet.
- [ ] Compare POCO runtime screenshots against the approved fig/HTML preview before claiming design pixel parity. See docs/NAOS-LIQUID-GLASS-V3-ANDROID.md.


## UAT 0.2.1 — user-reported UI regressions (19 Nov 2026)
- [x] DEV 0.2.3 source: remove edge-swipe `ModalNavigationDrawer` and always-mounted `BottomSheetScaffold`; keep native map visible with a compact bottom-only four-mode selector. Only the dedicated bottom handle may open a modal editor.
- [x] DEV source: bottom mode rail switches **瞬移／路徑／尋花／收藏**, without a left-hand drawer; settings and permissions are inside the expanded sheet.
- [x] DEV source: overlay actions no longer have a visible stroke on every row; replace with soft rounded action surfaces, spacing and unbordered group headings.
- [x] DEV source: real position marker reduced from 58dp bitmap to 24dp with small blue centre; mock marker is a matching orange position dot (not a bee).
- [x] DEV source: map tap in ROUTE immediately appends an ordered numbered destination; undo removes the last point for re-selection; tapping an existing numbered marker opens Delete/Re-pick; duplicate/near-origin/max-14 rules are tested.
- [x] DEV source: top coordinate arrow and keyboard Go explicitly teleport to entered/map-selected position; remove copy and hamburger buttons; teleports remain permission-gated.
- [x] DEV source: appearance changes immediately refresh existing overlay content and preserve tab/expanded state; Android configuration changes also refresh it.
- [ ] **POCO real-device acceptance pending**: single finger pan, pinch zoom, edge swipe gesture conflicts, 1→2→3 map tap/undo/delete, coordinate teleport, 24dp blue/orange markers, light/dark overlay contrast and screenshot parity.
- [ ] User review of updated OpenPencil design remains open; the old 12-screen V3 is a historical reference, not acceptance for these revised layouts.
- [ ] Promotion to next UAT must use develop → staging CI + fixed-signer workflow; never infer that compilation means phone-side behaviour is verified.


## V3 approved OpenPencil design / current Android implementation
- [x] Approved source-of-truth: `C:\Naos\02.Git\personal\design-previews\PikminBee-UI-V3-20261119.fig` (12 editable screens).
- [x] DEV v0.1.4 source: floating bee provides all four independent tabs (teleport, route, flowers, saved), waypoint edit/reorder, route launch from current device/virtual location, walking speed, pause/resume/stop/restore, and a link to main settings; tab changes never directly change GPS.
- [x] DEV v0.1.4 source: walking-time virtual step accumulator, persistent session ledger, Health Connect WRITE_STEPS permission flow, manual-entry source classification, stable client IDs, explicit opt-in, opt-out and local pending-record deletion; WorkManager backs up permission-gated synchronization.
- [ ] POCO acceptance: real overlay, background behaviour, keyboard on overlay, coordinate input, mode switching, route start, HC permission dialog/write/duplicate handling and screenshots; source tests do not prove physical-device function.
- [x] DEV 0.1.5 source: real physical steps can be read from Health Connect's supported on-device recording, or from Android TYPE_STEP_COUNTER via an opt-in foreground service on legacy devices. The two sources are tracked separately from virtual route estimates; duplicate legacy exports are disabled when native on-device HC recording is available. **POCO sensor and Health Connect acceptance still pending.**
- [ ] Design parity: refine translucent glass backgrounds, spacing and icon assets against the OpenPencil screens, not screenshots alone; the first Android slice is not a pixel-perfect port.
- [ ] Do not cut a new UAT APK or touch `main` without the standard develop -> staging checks.


## Version numbering policy (owner decision, November 18, 2026)
- [x] Configure UAT display versions starting at `0.1.1`; on a **new** UAT cycle increment minor and reset patch to 1 (`0.2.1`, `0.3.1`). DEV increments patch within the current minor (`0.1.2-dev`, `0.1.3-dev`, then `0.2.2-dev`).
- [x] Preserve the fixed UAT signer and monotonic Android `versionCode` independently of the visible version name. First signed build with the new policy still requires promotion to `staging` and an explicit manual package request; do not publish automatically.

## Approved POCO UI preview — November 18, 2026
- [x] Source: retained light / dark / system appearance and updated the glass-like translucent controls, gradients, drawer text contrast and theme-aware overlay. Exact preview parity and visual readability remain **POCO UAT pending** (no native backdrop blur on live map tiles).
- [x] Source: 「開啟懸浮控制」 now checks system overlay permission, launches system settings when missing, attempts to show on return and no longer requires a mock GPS session. Confirm real HyperOS popup and background longevity on POCO.
- [x] Source: added debug/UAT-only `ACCESS_MOCK_LOCATION` manifest marker; verified `aapt dump permissions` lists it in the built DEV APK. Actual HyperOS UAT picker visibility still requires device verification.
- [x] Source: ordered green / blue / red waypoint number icons for MapLibre and Google Maps, map sequence labels and independent phone blue-dot / virtual bee marker. POCO visual and map-marker touch interaction tests pending.
- [x] Source: permission guide now shows a top-row 「先略過」 action that enters map-only mode without claiming mock GPS eligibility; the explicit permission checks still gate actual mock location.
- [ ] POCO device acceptance for themes, overlays, GPS, route previews and accessibility; screenshots and source tests alone are not acceptance.
- [x] Source: road route preview shows the planned pedestrian distance and estimated duration, recalculated when walking speed changes; no straight-line estimate or extra routing request on speed toggle. Local Android unit tests + lint passed; POCO on-device UAT is still required.

## Next-phase enhancement — resting behaviour
- [ ] Research Locwarp-like automatic rest/break options and their relation to existing manual pause/resume. Decide trigger, duration, restart and persistence rules in the next phase; do not include resting time in the present ETA or silently change route playback.

## v0.3 map-first POCO UAT — November 18, 2026
- [x] Map-first home: native MapLibre map as the main interaction area, with a top coordinate-only paste/copy bar, a current-location camera button and mode navigation in a hamburger drawer.
- [x] Explicit coordinate parsing; tap the map or paste GPS coordinates to select a point without teleport. Route waypoints require explicit confirmation; no geocoding or additional map API.
- [x] Context-specific collapsible bottom sheet for teleport / pedestrian route / flowers / favourites; saved route library is centralised under favourites.
- [x] Shared settings page: map provider/style, light/dark appearance, walking defaults, floating bee and Wi-Fi. Live speed controls remain in active travel modes.
- [x] First-launch sequential permission guide with actual Android permission/status checks and a map-only escape; optional features never block map use.
- [x] Liquid Glass-inspired high-contrast, near-black native control surfaces without GPU-heavy continuous blur above tiles.
- [x] Dedicated signed UAT application ID com.naos.pikminbee.uat, fixed GitHub Actions secrets, cert fingerprint gate and monotonically increasing test versionCode. This package coexists with the old debug app but has distinct private storage.
- [ ] Validate the new map-first UI, permissions, route actions and sustained mock GPS function on POCO. See [MAP-FIRST-UAT.md](MAP-FIRST-UAT.md).
- [ ] Run full source/CI validation, promote develop → staging and manually upload a new fixed-signer UAT APK. **main remains unchanged.**


## First POCO UI UAT — contrast / usability follow-up (2026-11-18)
- [x] Reproduced visually from user-provided dark/light POCO screenshots: dark text on dark cards, unreadable unselected map-style chips, GPS coordinate fields and text buttons. Root cause was a user-selected dark appearance with a default light Material 3 colour scheme.
- [x] Unified dark/light Material 3 colour scheme with WCAG AA 4.5:1 source tests for normal content and selected chips, denser high-opacity cards, explicit primary-button text colour.
- [x] Prefer a compact header with collapsed map provider/style/theme settings, wrapped style chips, 48dp selected tabs and visible in-context operation messages.
- [ ] Reinstall a **new staging UAT APK** and visually verify with the user on POCO: dark/light/system appearance, all four tabs, map controls, font scaling, keyboard, touch targets and notifications. Keep [UAT Issue #26](https://github.com/Naos2w/PikminBee/issues/26) open until device evidence confirms.
- [ ] Source tests and GitHub CI do not constitute real-device screenshot acceptance.

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
- [x] Basic draggable bee overlay sharing the existing GPS foreground service, with Pause/Resume/Stop/Map/Hide/Restore actions; [x] one-tap "開始尋花" shortcut uses the saved flower draft, current explicit simulated position and Valhalla pedestrian loop, with cancel/stop and stale-result rejection; no second GPS engine. [x] Shared App/overlay/LAN speed presets: Slow 0.8 km/h, Walk 1.4 km/h (factory default), Fast 10.8 km/h. [x] Explicit user-selected startup default is stored separately from the current session's live speed, with migration of the previous saved selection. Live adjustment never resets route geometry. [ ] Device-level interaction, permission, speed accuracy, background and route-planning UAT.
- [x] Foreground GPS service + notification actions (pause/resume/stop/restore), 5-second last-coordinate journal and safe START_STICKY null-Intent recovery as PARKED; [ ] on-device lifecycle/restart UAT.
- [x] In-app HyperOS/POCO background guidance, standard Android app settings link and user-triggered permission flow; force-stop/reboot limitations documented in [BACKGROUND-LIFECYCLE.md](BACKGROUND-LIFECYCLE.md). [ ] POCO/UAT verification.

- [x] Draggable bee overlay placement is persisted in private App preferences and clamped to the visible viewport when moved, expanded, or reopened. [x] The expanded control panel scrolls on smaller screens, with display-orientation reflow. [ ] Real POCO window insets, orientation and drag/restore UAT; no guarantee of background survival after force-stop.

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
