# PikminBee TODO

## P0 · Unwanted coordinate TextField focus on map (21 Nov 2026)
- [x] Diagnose: `CoordinateTopBar` always mounted a focusable `BasicTextField`, even in read-only non-teleport modes. Android could focus its first text field automatically on activity/window resumption, showing keyboard/caret over the map.
- [x] Map-first fix: render a non-focusable text label by default; mount a `BasicTextField` only after tapping a dedicated Edit icon in Teleport mode. Confirm (checkmark)/Go/Teleport dismiss keyboard and focus. Never request focus just because map GPS/selection text changes.
- [x] Map pick, flower/waypoint selection, current-position action, tab change, details/explore open, app ON_STOP all end edit mode and clear the IME. Other tabs never render a coordinate input. Keep edit/confirm controls accessible with explicit content descriptions.
- [x] Add pure JVM input-state regression tests for default/no-edit, explicit edit gating, mode/sheet invalidation and dismiss.
- [x] MSI Kotlin compile, **185/185** JVM tests (0 failures/errors/skipped), Android Lint and `git diff --check` succeeded (Gradle exit 0); submit a checked DEV PR and only merge after GitHub source verify.
- [ ] POCO acceptance: fresh app start and return from background must not open keyboard; drag/zoom map, switch all 4 tabs, tap routes/flowers, open details and back, then manually edit coordinates in Teleport, confirm and use Go. No production or UAT promotion without release approval.

## P1 · Real-time per-walk estimated steps + icon-first controls (21 Nov 2026)
- [x] Source: moving distance = selected speed in m/s × elapsed time (bounded to remaining planned route); virtual estimate = floor(all-time accepted metres / fixed 0.72 m stride). This is a virtual estimate, not physical pedometer activity; the 10.8 km/h preset is not a calibrated running stride.
- [x] Separate all-time virtual estimate from **per-walk** steps using an immutable walk-start baseline and a process-local observable flow shared by the main screen and floating overlay. Each new route resets its own displayed count; GPS virtual-step checkpoints do not reset its display.
- [x] Pause freezes stepping, checkpoints pending manual-estimate records, updates the overlay and notification immediately; resume excludes the paused interval from the next recorded batch; stop freezes the final count and posts the final batch. Health Connect cannot store a separate running/paused state; it stores opted-in manual estimate intervals asynchronously.
- [x] Main map shows prominent “本次估算 X 步” while moving/paused and after stop in the relevant mode. Detail sheet, floating panel, active notification and HC settings show the same per-walk estimate/status.
- [x] Compact SVG-style Compose Canvas vectors replace verbose tool shortcuts (real/virtual GPS, flower vision, speed, undo and HC refresh); primary start/pause/stop retain brief text for safety and accessibility. All icon-only touch targets have TalkBack descriptions.
- [x] JVM regression checks compare speed-dependent estimate rate and current-walk baseline across pause/stop/new walk.
- [x] MSI Kotlin compilation + **181/181** JVM tests, 0 failures/errors/skipped, Android Lint and `git diff --check` succeeded (Gradle exit 0). Stage for normal checked DEV PR; do not auto-promote to staging/main.
- [ ] POCO UAT acceptance: 60s at each speed, pause/resume/stop/terminal arrival, main vs overlay vs notification count and phase, old pending HC queue, manual-entry source and TalkBack controls.

## P0 · Health Connect virtual steps not visibly increasing (21 Nov 2026)
- [x] Source diagnosis: on-screen total is a historical cumulative virtual estimate, not a live Health Connect read. Previous implementation only queued a HC manual-record export on route stop/arrival, while the aggregate screen refreshed only on entry/manual refresh. Zero pending is not evidence of a successful write.
- [x] Add opt-in-only 60-second session checkpoints and pause/finish flush, preserving MANUAL_ENTRY metadata, user permissions and stable client IDs; do not present mocked distance as physical sensor data.
- [x] Prevent enqueued chunks being silently stranded by WorkManager KEEP while another sync is running: use APPEND_OR_REPLACE and show the last write outcome.
- [x] UI reads today's actual PikminBee manual virtual entries separately from Health Connect's deduplicated aggregate, refreshes upon queue completion/periodically while visible, and distinguishes the all-time local estimate.
- [x] Add JVM tests for checkpoint opt-in, 60-second cadence and no double-counting after advancing step baseline.
- [x] MSI Android Kotlin compile, **179/179** JVM unit tests (0 failures/errors/skipped), and Android Lint succeeded (Gradle exit 0); source changes ready for a normal checked PR to develop. No UAT promotion or physical-device acceptance claimed.
- [ ] POCO acceptance: with virtual opt-in and WRITE_STEPS, walk >60s, pause/stop, refresh Settings, inspect this app's HC virtual entries and total versus other higher-priority data; verify pending records drain and no duplicates. No connected Android ADB device at present.

## P0 · Road snapping and two-step main-map route controls (21 Nov 2026)
- [x] DEV source: replace strict 45 m proximity rule with bounded 200 m Valhalla pedestrian-edge snapping and matching candidate search radius; keep an explicit failure beyond the bound rather than silently using straight lines or an arbitrarily distant route.
- [x] Main route quick button is now Plan → Start on the exact confirmed preview geometry; no location modification or mock-service start on preview. GPS jitter up to 30 m preserves preview; edited waypoints or moved starting point invalidate it.
- [x] Main sheet and map quick button now use a single route-primary handler; during travel the global quick bar offers Pause/Resume and independent Stop even after switching map modes. Expanded main sheet adds shared real/virtual GPS, flower vision and speed shortcuts while the floating panel stays lightweight.
- [x] Pure JVM regression cases cover nearby vs far snap and preview freshness across jitter/edit/moved origin.
- [x] GitHub Android verify (PR #59, run 44776752065) passed: Kotlin compile, JVM unit tests and Android Lint; no APK packaging. MSI OpenChatX was offline at the start of this task, so no local Android build or live-device evaluation is claimed.
- [ ] Physical POCO acceptance: snap taps in parks/courtyards, verify nearby footpath choice and ≤200 m displacement; check 2-stage preview/start, rapid tap guard, active Pause/Resume/Stop in all map tabs, overlay parity and route switch with mock/real GPS. Routing provider may still refuse genuinely disconnected areas.
- [ ] Promote only through normal PR gates when ready; existing UAT 0.7.1 on staging is not this DEV change, and main must remain untouched.

## V4-C · Glass lens rim / overlay entry / concise settings (20 Nov 2026)
- [x] DEV source: correct the inset glass rim and inner bevel, strengthen AGSL edge refraction with restrained dispersion/specular highlights and keep safe frosted fallback. Log shader failures for device diagnosis.
- [x] Request initial native map snapshots when the MapLibre style has settled or Google map tiles have loaded; keep existing bounded in-motion sampling.
- [x] Add a subtle glass rim around the collapsed bee overlay, retain the same state-coloured bee and touch/drag behaviour; give the expanded cross-app panel a restrained glass gradient without claiming access to other apps' pixels.
- [x] Add floating-overlay show/hide next to Settings in the map's detail-sheet header, sharing the existing permission and first-use-guide flow with Settings.
- [x] Simplify settings copy: remove developer/system explanations, redundant speed descriptions, and UAT decorative labels. Make the overlay preference a switch.
- [x] MSI Kotlin compilation + **171/171** JVM unit tests + Android Lint passed (`BUILD SUCCESSFUL`, exit 0). `git diff --check` clean before TODO update.
- [ ] Inspect on POCO: Light/Dark at actual map lines, edge refraction vs blur-only, initial idle capture, pan and pinch, GPU shader warnings, overlay rim/drag/tap, and settings/quick-switch permission states. ADB currently lists no device. Screenshot's purple flower floating icon differs from source's bee outline; verify which installed app owns that overlay.
- [ ] Normal squash PR to `develop` and subsequent *separately approved* UAT build/installation; no automatic promotion to `staging` or `main` for this V4-C change.

## UAT 0.6.1 acceptance candidate (20 Nov 2026)
- [x] DEV source incorporated: PR #52 (floating bee tap, route preview, Mineral/Honey theme, single-level coloured actions) and PR #53 (bounded live-camera map-glass sampling). All GitHub PR checks passed at source merge; source merge does not imply device acceptance.
- [x] UAT version bump via `scripts/bump_version.py uat`: UAT `0.6.1`; DEV `0.6.2-dev`. Version rule checks passed.
- [ ] Promote `develop → staging` by checked Merge Commit; use the existing manual GitHub Actions workflow with `package_apk=true` to create fixed-signer `PikminBee-UAT-stable-signed`; verify applicationId, displayed version, increasing versionCode and signer digest.
- [ ] POCO UAT: repeated map pan and pinch at varied speeds with no hard glass flicker; compare actual high-contrast map-edge distortion with a frozen frame; check smoothness/heat, panel tap/drag, eight icons, motion colours, route preview road fit, physical/virtual Steps and permissions. Keep `main` unchanged until explicit user acceptance.
- [ ] Revisit any phone-specific GPU/map SDK limitation discovered in UAT rather than claiming 60 FPS continuous refraction from asynchronously sampled map snapshots.

## P0 · Live Liquid Glass during native-map gestures (20 Nov 2026)
- [x] PR #52 Squash Merged to `develop`: commit `ffa69d8`, GitHub Android verify passed.
- [x] Both MapLibre and Google Maps now request genuine native-map snapshots during camera movement instead of clearing optics at movement start. The existing AGSL border-refraction shader samples these changing map pixels.
- [x] `MapGlassCaptureGate` limits capture requests to at most one every 320 ms, permits only one outstanding request, prioritises the final idle snapshot, discards late/obsolete callbacks, and recovers after stalled native callbacks.
- [x] `MapGlassSnapshotBridge` marshals bitmap results back to the UI thread, rejects disposed views, and drops samples that are too stale while retaining the low-cost translucent mineral fallback.
- [ ] Verify on POCO: map drag, continuous zoom, both available providers, AGSL refraction line distortion during gestures, screenshot latency and frame pacing, memory allocation and thermal load. Never claim 60 FPS optical refraction from SDK snapshots. If live capture cannot keep up, safely use frosted glass without blocking map touch.
- [x] MSI local `:app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --offline` passed: **171 / 171 JVM tests**, zero failures/skips. `git diff --check` clean; no connected POCO device. Changes prepared on `fix/map-glass-live-camera-sampling` for a standard DEV squash PR. No APK or promotion to `staging`/`main`.

## V4-B · UAT 0.5.1 user-reported regressions (20 Nov 2026)
- [x] **Source fix / not yet device accepted:** collapsed bee was consuming touch UP and calling `performClick()` without a registered click listener; wire `setOnClickListener` so tapping really expands the panel. Dragging remains separate.
- [x] **Source fix / not yet device accepted:** remove the decorative collapsed button/background and status dot. Keep a 54dp invisible touch target around an original outline-only bee logo. Idle: near black on Light / near white on Dark; expanded: honey; walking: vivid green with gentle flying motion; **paused: clearly distinct blue with an integrated Ⅱ glyph** and slight hover; stopped: neutral/no motion. Respect Android reduced-motion settings and cancel animators on dismiss.
- [x] **Source fix / not yet device accepted:** retaining route preview no longer depends on whether JourneyControls is currently composed; remove its mount-time `onPreviewRoad(emptyList())`. Ignore harmless phone GPS jitter while an asynchronous routing request is in flight. On successful pedestrian preview, fit the road and close the bottom sheet. Never move GPS on a preview-only click. Explicit route/loop edits still invalidate the relevant preview.
- [x] **Source fix / not yet device accepted:** preserve a translucent mineral glass material and subtle edge highlights while moving the map, then blend back to genuine map-snapshot optics when idle. No claim of continuous real-time pixel refraction during camera motion; no costly per-frame map snapshots.
- [x] **Source fix:** remove theme-family and Regular/Clear/Blur selectors; the product now uses fixed Mineral + Regular with restrained Honey logo accents. Only Light/Dark are selectable; old SYSTEM/family/material preferences are migrated/ignored.
- [x] MSI network diagnostic: Valhalla's pedestrian routing test request returned HTTP 200 with one route leg, so the server was reachable from MSI; this does not prove reachability from a POCO phone.
- [x] Editable OpenPencil design preview `C:\Naos\02.Git\personal\design-previews\PikminBee-V4-B-Mineral-Honey-Logo-20261120.fig`. The updated frame `PikminBee-V4-B-Main-Overlay-States-20261120.png` now shows **both main App and floating overlay**, Light/Dark, running/paused and all four bee-logo states; repository copy at `docs/pikminbee-v4b-mineral-honey.png`.
- [x] **DEV colour/state refinement:** main App's map route quick controls and detailed JourneyControls now share one action palette with the icon-only floating panel: vivid green Start/Resume, amber Pause, red enabled Stop; unavailable Stop stays muted. Expanded honey is not conflated with paused blue logo + Ⅱ. Controls retain labels/glyphs for non-colour recognition.
- [x] MSI local `:app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --offline` passed; **164 / 164** JVM tests with 0 failures or skipped. New BeeActionColors tests cover primary/stop semantics and distinct expanded/paused appearance; Gradle exit 0.
- [x] PR #52 (`fix/* → develop`) passed GitHub checks and was Squash Merged to `develop` as `ffa69d8`. Main App and overlay share action-state colours; this remains separate from UAT APK promotion.
- [ ] POCO UAT: validate touch/drag, logo motion and idle colours, map glass on continuous pan, route preview line and pan, light/dark, accessibility, Android overlay permission and real/virtual location state. Then proceed through the next explicit staging UAT release gate; do not modify production `main`.

## V4-A follow-up · Icon-only floating controls and map refinements (20 Nov 2026)
- [x] DEV source: compact draggable bee and 2 × 4 icon-only single-level toolbar with status and separate virtual/real Steps. User approved removing the old 「進階操作」 area entirely; coordinate entry, route edits and favourites are available only in the main App. No duplicate overlay editor or keyboard input.
- [x] DEV source: dynamic start / pause / resume; independent stop; temporary speed-cycle 0.8 → 1.4 → 10.8 km/h; opening the map never stops walking; closing the overlay is separate from stopping the GPS route.
- [x] DEV source: real/virtual GPS toggle remembers only the last virtual coordinate for up to 12 hours and restores it as PARKED without route auto-resume; mock-location permissions still gate changes. Real GPS restore keeps the overlay available.
- [x] DEV source: flower-vision quick action opens the main App's existing consent-gated vision panel, or stops an already active capture. No screenshot/MediaProjection permission is bypassed, and no new capture engine is created.
- [x] DEV source: first-use 3-step icon explanation (can skip), persisted on/off setting, replay in Settings, optional long-press icon labels and accessibility descriptions.
- [x] DEV source: map header bee/Home action recentres to the currently available position and collapses the editor; bottom sheet Settings/Permissions use vector icons. Motion includes subtle selected-tab and panel transitions.
- [x] DEV source: route playback progress (ordered next stop / distance / remaining ETA / speed / visual progress) and compact overlay status. Fraction and ETA are derived from existing road geometry, not invented values.
- [x] DEV source: optical-to-frosted map glass transition over 160 ms when a camera moves; never perform continuous expensive pixel capture while panning. Native Android 13+ AGSL support and fallback remain unchanged.
- [x] Editable focused UI preview: `C:\Naos\02.Git\personal\design-previews\PikminBee-V4-A-Overlay-Icons-20261120.fig` (Light/Dark). Removed advanced-actions labels after user approval; re-exported reviewed PNG as `PikminBee-V4-A-Overlay-SingleLevel-20261120.png` in the same folder.
- [x] Final MSI local `:app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --offline` passed (Gradle exit 0, 158/158 JVM tests, 0 failures or skipped, Android Lint successful) for the source revision. Git diff --check passed. No GitHub CI wait, new APK/UAT promotion or `main` change.
- [ ] Real POCO UAT: panel size/drag/edge clipping and animations, permissions and projection prompt, external-app overlay, real→virtual GPS return after service lifecycle changes, Health Connect/steps, responsive map gestures, real shader optical effect and accessibility/font scales. No connected physical phone; compile/preview is not UAT.
- [ ] UAT 0.5.1 candidate: after PR #49 source merge, explicitly prepare the version PR, promote `develop → staging` by merge commit after checks, then manually request the stable-signed APK from staging for POCO acceptance. The APK is the test candidate, not proof of device acceptance. Never promote to `main` before user acceptance.

## Naos Liquid Glass V3 · User-approved A/map-first Android UI (20 Nov 2026)
- [x] DEV 0.3.3: canonical Naos six theme families with Light/Dark and Regular/Clear/Blur, persistent settings, vector icons and low-saturation controls.
- [x] DEV 0.3.3: AGSL source-sampled rounded-edge lens effect on Android 13+ with idle MapLibre/Google snapshot, frosted fallback where unsupported; cross-app overlay uses privacy-safe frosted material.
- [x] DEV 0.3.3: top direct-teleport entry, floating four-mode dock, route undo/start/pause/stop, and compact bee quick actions with full controls behind deliberate expansion.
- [x] Initial Kotlin compile, 152 JVM tests, Android Lint and DEV assemble passed on MSI.
- [x] PR #48 merged `develop → staging`, GitHub Actions run #44580016357 succeeded and produced the fixed-signer `PikminBee-UAT-stable-signed` 0.4.1 artifact (20 Nov 2026); production `main` unchanged. That artifact predates this V4-A icon-only follow-up.
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
