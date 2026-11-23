# PikminBee TODO

## P1 · 2026-11-23 approved development backlog — GPS jitter / virtual steps / flower-planting speed / end notification / flower vision
**State:** User requested that all items below become development work. The source observations are verified, but **none of the new behaviours below is implemented or POCO-accepted** by recording this section. Reuse existing DEV/staging/POCO UAT gates; no production `main` promotion or device installation is implied.

### A. Optional simulated-GPS jitter (new development)
**Current evidence:** `MockLocationService.tick` uses deterministic `WalkingGeometry.sampleAt(track, walkingDistanceMeters)` and `setTestProviderLocation`. The injected `accuracy = 5f` is metadata; it does **not** apply a 5-metre random offset. There is no user-adjustable positional jitter in this playback path.
- [ ] Design a **default-off, clearly labelled mock-location test option** for bounded, temporally smooth horizontal position variation; offer an off/on and conservative amplitude control only if useful. Never represent simulated accuracy as verified physical GPS.
- [ ] Keep the original, unperturbed road geometry as ground truth for progress, ETA, speed and arrival. Stationary/paused/parked states must not become phantom walking or produce extra estimated steps, and jitter must not push location unexpectedly beyond reasonable road bounds.
- [ ] Add Kotlin boundary/continuity/reproducibility tests, Android mock provider tests, and physical POCO validation for route, teleport, pause/resume, real-GPS restoration, speed display and app/Web/overlay consistency. Do not claim the option improves third-party game acceptance.

### B. Virtual step estimate and selected speed (new development)
**Current evidence:** `EstimatedStepCounter` computes `floor(accumulatedTravelMetres / 0.72)`; `MockLocationService` adds travel from `speedMetersPerSecond * deltaSeconds`. Thus the **same elapsed time** at higher speed generates more estimated steps, while the **same distance** generates approximately the same steps regardless of speed. This is explicitly virtual, not sensor-measured activity.
- [ ] Review the realism of the current fixed 0.72-metre virtual stride, especially at 10.8 km/h (~250 estimated steps/minute). Assess a documented, testable pace/stride model (or explicitly explain why fixed distance-to-step conversion is retained), preserving transparent *estimated* labels.
- [ ] Ensure selected-speed changes take effect without resetting routes, introducing discontinuities or counting paused/parked/jitter-only movement; cover 0.8, 1.4 and 10.8 km/h plus any future speed presets, loops and elapsed-time changes.
- [ ] Keep hardware/Health Connect actual steps separate from virtual estimates; preserve explicit consent, manual-entry labelling and duplicate protection for optional virtual-statistic synchronisation. Test stop/restart/recovery/permission denial on POCO.

### C. Flower-planting speed strategy and optional new presets (new development)
**Current evidence:** `WalkSpeedPreset` currently offers 0.8 / 1.4 / 10.8 km/h; `activateRoute` clamps speed to 0.2–3.0 m/s (max 10.8 km/h). Official Pikmin Bloom support warns that moving too quickly, standing still, or repeating the same area can prevent planting **while petals continue to be consumed**; it does **not** publish a guaranteed optimal speed. Community reports suggest a variable effective limit around 16–20 km/h, with a conservative 12–15 km/h evaluation range and some 15–18 km/h results; this is **not an official guarantee**.
References: https://niantic.helpshift.com/hc/zh-hant/23-pikmin-bloom/faq/2855-planting-flowers/ ; https://niantic.helpshift.com/hc/en/23-pikmin-bloom/faq/2877-i-can-t-plant-flowers-1636017814/ ; https://forum.gamer.com.tw/C.php?bsn=72889&snA=595
- [ ] Evaluate optional flower-planting speed choices around 12 and 15 km/h, with higher-speed research as a separate explicit choice **only after real results**; retain existing user-selected 0.8 / 1.4 / 10.8 km/h options and current personal default unless changed intentionally.
- [ ] Review the 3.0 m/s playback clamp and all App / floating controller / paired Wi-Fi speed validation and display contracts before adding any faster preset. Keep route preview, start confirmation, actual speed (m/s vs km/h), ETA and virtual-step modelling consistent.
- [ ] Define an honest real-device evaluation comparing distance, elapsed time, planted-flower outcome, petal usage, repeated-area cooldown and GPS uncertainty; do not claim guaranteed planting, game compatibility or evasion of speed restrictions.


### LocWarp / 巡花助手 source-inspection reference (2026-11-23, requested by user)
**Use behaviour and design as reference, not wholesale code.** Sources: [LocWarp](https://github.com/keezxc1223/locwarp) (`backend/config.py`, `backend/core/simulation_engine.py`, `backend/services/interpolator.py`; MIT licence) and [皮克敏巡花助手](https://github.com/SmailDot/Pikmin-Bloom-GPS) (`Prefs.kt`, `WalkSimulator.kt`, `PatrolService.kt`, `StepInjector.kt`, `DailyLedger.kt`). The 巡花助手 repository's inspected root tree had **no identifiable LICENSE file**, so do not reuse its original code without permission.

**LocWarp — speed interaction and GPS-jitter findings**
- Current preset buttons: **10.8 / 19.8 / 60 km/h** (walk / cycle / drive), plus custom fixed km/h or a min–max range sampled **per leg**, precedence range > fixed > preset. Applying a newly chosen speed while moving updates the active route without stop/restart; selected settings persist. `make_speed_profile` also supplies speed-dependent positional jitter (up to 1.5 m) and a 0.5/1-second tick interval; do **not** confuse this with the user's earlier separate GPS jitter requirement.
- [ ] Design an **advanced custom-speed control** for PikminBee (fixed km/h, validated minimum/maximum and optional bounded per-leg random range) alongside existing 0.8 / 1.4 / 10.8 presets. Avoid adding 60 km/h to the **flower-planting** choices merely because LocWarp offers a driving mode.
- [ ] Allow explicit application of speed while MOVING or PAUSED, continuing the same route from the current true progress; show **selected/typed vs currently applied** speed, correctly sync Android App, 2×4 overlay and paired Wi-Fi Web; keep current user's saved personal default intact until expressly changed. Preserve loop boundaries, route confirmation and stop semantics.
- [ ] Audit the current `activateRoute` `0.2..3.0 m/s` clamp, speed validation, ETA, display conversions, time base, and GPS tick interpolation before supporting 12 / 15 km/h or an independently evaluated advanced **18 km/h** planting test preset. Confirm with physical outcomes rather than assuming the highest speed is the most efficient.
- [ ] For optional GPS jitter, keep the underlying route progress and walked-distance ledger exact and perturb **only the reported test coordinate**; use bounded, smooth noise so each tick's new GPS location does not generate phantom distance/steps. Do not adopt blind tick-to-tick teleports or promise undetectability.

**巡花助手 — step accounting and synchronisation findings**
- Reference implementation tracks `walkedDeltaM` **separately from total distance** and accumulates `stepsAccrued += walkedDeltaM / strideM`. Its walking stride defaults to **0.70 m** (configurable **0.40–1.20 m**); vehicle legs can travel distance while `countsSteps=false`. It supports periodic Health Connect `StepsRecord` + `DistanceRecord` writes (user-configurable roughly 20–600-second flush), an optional daily cap, and a local-midnight daily-ledger reset.
- **Provenance distinction:** 巡花助手's `StepInjector` uses `Metadata.autoRecorded` and device `TYPE_PHONE` for *simulated* sessions. PikminBee deliberately writes opt-in virtual estimates with `Metadata.manualEntry` and separate IDs; **retain honest virtual-vs-measured data labelling**. Do not claim Health Connect or any third-party game will count estimates as sensor-recorded steps.
- [ ] Evaluate configurable virtual stride with an explicit range and explanation (retain existing **0.72 m** default until a user-facing change is chosen). Do not confuse increased speed with a measured human cadence; tests must cover constant distance, varying elapsed time, pause, stationary jitter and speed changes.
- [ ] Separate `totalTravelMetres`, `walkedMetres`, `estimatedSteps` and **real sensor steps**. If separate transport modes are added, do not accrue estimated *walking* steps for vehicle-only segments; preserve old session data and avoid step resets when speed changes mid-route.
- [ ] Review local-day rollover, optional user-configurable daily maximum, bounded periodic checkpoints, permission/reconnect errors and duplicate protection in PikminBee's existing `EstimatedStepLedger` / Health Connect sync implementation. Preserve explicit opt-in and MANUAL_ENTRY provenance; do not copy the unlicensed reference's metadata mislabelling.
- [ ] Test the comparison with repeatable, explicit cases and report **estimated vs Health Connect accepted vs external application observed** results separately; no third-party compatibility guarantee.

### D. End-of-route completion notification / sound (approved prior request, still development)
**Current evidence:** Non-loop route arrival changes `MockLocationService` to `PARKED`, shows `已抵達終點 · 原地停留` and refreshes a low-importance ongoing notification. There is no independent one-shot audible completion notification.
- [ ] Emit an exactly-once route-completed event on non-loop arrival, share the confirmed state across Android App, overlay and authorised Wi-Fi Web controls, and support a visible notification with optional audible/haptic feedback respecting OS permissions, Do Not Disturb and user preferences.
- [ ] Define explicit behaviour for looping routes (no notification on every lap by default), manual stop, failed route, repeat/reconnect and Web browser audio gesture/permission limits. Do not resume walking automatically.
- [ ] Add completion-event regression tests and POCO/PC LAN end-to-end acceptance before marking done.

### E. Screen-based flower recognition (existing work, further development/acceptance)
**Current evidence:** `BloomCaptureService` already processes an explicitly authorised screen capture about once every 3 seconds, using `BloomDetector`; static-image import and manual pixel-to-GPS anchor preview also exist. They have synthetic tests, **not verified real-game precision/recall or reliable world coordinates**.
- [ ] Reuse the existing flower-vision TODO and [Issue #17](https://github.com/Naos2w/PikminBee/issues/17), not a second detector. Create a permitted, user-authorised non-protected test image set, evaluate false positives/negatives and independent location-error measurements; use NAOS-PC only if accessible and authorised.
- [ ] Test Android MediaProjection consent/stop/rotation/protected black frames, performance and UI feedback on POCO. No capture bypass, game touch automation, or automatic GPS waypoint insertion from unverified candidates.

**Order / acceptance:** First verify the already built **UAT 0.19.1** on actual POCO + trusted-LAN PC browser ([Issue #18](https://github.com/Naos2w/PikminBee/issues/18)); then implement the small route-completion alert, bounded GPS jitter and virtual-step model, evaluate optional planting speeds, and continue flower recognition accuracy. Each implemented item needs affected tests, source PR -> `develop` CI/Squash, independently approved UAT promotion, and actual device evidence. This TODO-only documentation change is not a new APK.

## P0 · Wi-Fi Web V2 FINAL implementation (user-approved 2026-11-22 23:55)
**Source of truth:** `docs/WIFI_WEB_V2_FINAL_SPEC.md`. Scope is feature branch `feature/wifi-web-v2-final-20261122`, no automatic production changes.
- [x] Re-read actual approved V2 final spec and current Android/LAN source; kept existing route preview→explicit Start, pairing PIN and phone approval, mineral/honey matte theme.
- [x] Android Wi-Fi Settings: copy icon + 「複製」 immediately to right of URL; previous big copy button opens the system default browser; retain pairing, revoke and HTTP risk text.
- [x] Web single combined `lat, lon` field, allows comma / fullwidth comma / whitespace; validates latitude and longitude ranges; viewing or editing point never moves GPS without explicit confirmation.
- [x] Map-first Web full-viewport map canvas; desktop right panel defaults open (368px), tablet drawer portrait collapsed/landscape open (326px) with manual override retention; 690×920 explicitly considered portrait tablet.
- [x] Mobile starts with collapsed 56px Bottom Sheet + bottom four-tab navigation. New functional mode opens halfway; re-tapping current mode collapses; Settings tap-only; handle switches collapsed/half/expanded; no map gesture theft.
- [x] When mobile walk MOVING/PAUSED and Sheet collapsed, compact status and pause/resume/confirmed-stop remain available without expanding; controls follow Android-reported phase.
- [x] Phone-side explicit consent for the **currently paired Web token** required before precise physical coordinates can leave Android. Memory-only, verified non-mock, at most 30 minutes after sample timestamp; revoke, new pairing and LAN stop clear the shared snapshot. Virtual/real/selected coordinates remain different markers and statuses.
- [x] JVM tests for the session token policy and expiry; source-level browser DOM contracts; loopback browser smoke: 390×740, 690×920, 830×650, 1280×680, 1440×900 full map with no horizontal overflow; consent→physical→mock historical→expire/revoke; mobile mode toggle, compact pause/resume, combined coordinate validation. No access to the real POCO.
- [ ] Submit checked feature PR to `develop`, require Android JVM/Lint GitHub CI before Squash Merge.
- [ ] **Physical device and private LAN acceptance remains open:** POCO Android Chrome/Edge, PC Edge, real permissions and HTTP risk, 30-min revoke after exact fix time, network failure/repair, actual map gestures, route preview/start/pause/resume/stop, keyboard/focus and tablet rotation.
- [ ] Staging UAT package and any production promotion require a separate release gate. Never directly alter `main` or auto-install to POCO.

## P0 · Floating GPS buttons must SWITCH modes (UAT 0.17.1 follow-up)
- [x] Reviewed actual DEV/staging source: both buttons were wired to `ACTION_REAL_GPS_OVERLAY` and `ACTION_VIRTUAL_GPS_OVERLAY`, and the **lit** mode is derived from the confirmed `LocationStatus.mockActive`. However the **virtual button was disabled when no virtual checkpoint within 12 hours**; tapping an already-selected virtual icon could reset and stop an ongoing route.
- [x] Keep **2×4 icon-only overlay** and approved colour palette. Both GPS icons always respond to a tap. Tapping **the other** requests switching; tapping the active one does not reset walk or GPS. The mock-provider state alone determines which lights up, not the last tap.
- [x] **Virtual → Real:** remember current virtual point, stop fake GPS / any active walk, keep overlay visible, update highlight only after provider stops. No claim of an actual physical satellite fix.
- [x] **Real → Virtual:** restore the most recently saved **unexpired** virtual point; do not auto-resume walk. If no eligible point exists, show concise Toast and open the main app in **瞬移** to pick coordinates; never fabricate a mock coordinate.
- [x] Prevent a failed Android mock-provider permission/setup attempt from silently closing the floating controller; show the actual installed app label in the recovery hint (including after releaseMock resets display), keep REAL highlighted. If Android rejects background startService after switching to REAL, route the explicitly user-tapped GPS action through the still-alive owner Service instead of crashing or swallowing the click.
- [x] Added JVM regression tests for the four GPS toggle decisions, including same-mode no-op and missing-last-point fallback.
- [ ] Full source JVM and Lint CI + checked `fix/* → develop` Squash PR; subsequent checked `develop → staging` Merge Commit and signed UAT is required for phone validation.
- [ ] POCO UAT: verify both taps switch and only the active GPS icon lights, no position/satellite quality misrepresentation, last point restored on switch-back, permissions-failed and no-saved-location guidance, walking safely stopped on Real and **never auto-resumed** on Virtual. User's existing 0.17.1 will not acquire these source changes without an updated APK.

## P0 · User-approved GPS / RWD / navigation / favourites audit — 2026-11-22
**Compared against UAT 0.16.1 staging and latest develop source.** Keep mineral/honey colours and performance-first matte (no blur), and preserve the 2×4 icon-only floating controls.
- [x] GPS floating REAL/VIRTUAL buttons show only one confirmed mock-provider mode using the existing high-contrast accent/ring/dot; long-press/guide carries meaning and no permanent new GPS text. The 0.16.1 fix already passed CI; physical-device visual acceptance remains below.
- [x] Android map dock and detail sheet use **瞬移／路徑／尋花／設定**, with Settings tap-only and swipe boundary limited to first three; map drag/pinch unaffected in source. Actual POCO gestures still need real-device validation.
- [x] Web RWD remains an independent map-first desktop sidebar/tablet stack/mobile fixed dock. Three swipeable functional modes and tap-only Settings; no theme-colour change.
- [x] Top-right Android/Web favourites, independently scrollable searchable lists, named places and routes, user-confirmed view/load without auto-teleport, name editing/deletion and details for coordinates/date.
- [x] New Android favourite: explicit Photon reverse geocoding (POI/landmark/shop within 100m first, then street/district/city, then coordinate), editable proposed name. No automatic location updates or provider-dependent networking.
- [x] **Fixed missing parity**: paired Web can now explicitly add favourites with suggested editable landmark name, using asynchronous phone-side Photon/TimeAPI lookup; no implicit teleport.
- [x] **Fixed legacy gap**: Android/Web now offer explicitly triggered, bounded batches to backfill old favourites' missing country/city/IANA time zone, never overwriting original name, position, date or existing known metadata. Show data-source/privacy notes.
- [x] **Fixed real-zone reference gap**: a verified non-mock physical fix establishes a memory-only 30-minute reference surviving switches to virtual GPS; expiry or missing reference yields 「時差待確認」. Web preserves this in private LAN state without sending true coordinates to the browser; Android expiry is rechecked as the sheet's local clocks update. Never assume Taiwan or derive real zone from mock GPS.
- [x] **Fixed Web long-list clock refresh**: only clock text updates every minute; saved-list scroll position and expanded details survive refreshes.
- [x] JVM policy tests for reference expiry, legacy metadata preservation and invalid values; Node JS syntax; synthetic browser test for Web save and real-zone comparison under virtual GPS. **Synthetic tests are not POCO validation.**
- [ ] Full GitHub Android JVM/Lint CI for this audit-fix PR, then merge via normal checked `fix/* → develop` Squash PR.
- [ ] **Real POCO + Edge UAT acceptance**: physical GPS provider changes, overlay bright/dark state, 3-mode horizontal swipe and tap-only Settings, old-place metadata queries, Web LAN pairing/revocation, timezone and DST, offline/no-permission behaviour, visual layout at different sizes. No ADB device was attached during source work.
- [ ] Optional next signed UAT **after the audit-fix PR is verified and merged**, via `develop → staging` Merge Commit and manually triggered fixed-signed GitHub Actions. Production `main` remains untouched.

## P0 · UAT 0.15.1 floating GPS mode indicator follow-up (2026-11-22)
- [x] Checked actual 0.15.1 source: the expanded overlay chooses REAL/VIRTUAL from `LocationStatus.mockActive` (confirmed mock-provider state). But selected button's border was the same colour as its fill, the unchecked button had no distinguishing low-priority treatment, and virtual restoration did not call `updateOverlay()` explicitly after success (it relied on the synchronous tick refresh).
- [x] Use only existing mineral/honey semantic colours, without new words or animated effects: selected GPS icon has solid accent, visibly contrasting on-accent rim, 8dp corner dot, full opacity and accessible selected state/long-press explanation; inactive GPS icon uses the existing soft surface and lower opacity.
- [x] Explicitly repaint after successful virtual GPS restore, while keeping the actual mock-provider state as the only source of truth; real GPS restoration retains its existing direct repaint.
- [x] Added policy regression for exactly one highlighted GPS action; MSI full offline Android Kotlin/JVM `:app:testDebugUnitTest` passed.
- [x] GitHub CI `android / verify` (JVM + Lint) and squash merge DEV GPS-indicator fix (#93); later released as signed 0.16.1.
- [ ] User's actual installed **0.15.1 cannot contain this follow-up fix**. Physical POCO light/dark, mock on/off and failed-restore visual acceptance requires a later signed UAT; do not claim it was seen on device. No auto-install or production main change.

## P0 · Approved UI/UX & favourites redesign — 2026-11-22
**Settled scope:** Keep existing low-saturation mineral blue/honey theme and lightweight opaque matte components. Android and Web are deliberately platform-specific, not identical layouts. No automated UAT or main promotion in feature PRs.

### Android interaction
- [x] Replace both shared 4-button map docks (main bottom and expanded sheet) with **瞬移 / 路徑 / 尋花 / 設定**. First 3 may swipe inside dock/details; Settings **tap only**, opens existing BeePage.SETTINGS and returns to previous mode. Never steal map pan/pinch; preserve current route and ongoing movement.
- [x] Put a **收藏 bookmark action in the map top-right**. Tap opens a lightweight independently scrollable favourites sheet; group places and saved routes, with search/filter and clear select/load/view actions; selecting a place only previews the map, **never teleports without separate confirmation**. Ensure this still works on small POCO displays.
- [x] In floating overlay, keep existing **2 × 4 icons** and no permanent GPS text. Highlight exactly one of REAL_GPS / VIRTUAL_GPS according to **confirmed Android mock-provider state**, not last button tapped; use existing accent/border, no new theme colour. Do not signal real fix availability merely because mock is off. Document icon meaning in existing overlay guide and preserve TalkBack/long-press.
- [x] Audit text-only actions (initial priority: teleport, restore real GPS and save place now show native vector icons; secondary text actions intentionally remain); add vector icons to high-priority functional buttons without crowding Confirm/Cancel or shrinking touch targets.
- [x] Fix horizontal mode swipe in code (3-mode policy boundary and actual gesture ownership unchanged; physical POCO swipe acceptance still pending) on actual detail content; Settings remains tap-only. Add tests for 3-mode boundary.

### Favourites data / local time / suggested names
- [x] **Geocoding is user-triggered** (on saving/editing a selected place), never called on GPS ticks. Landmark/business/POI first **only if close enough**, then street, district/city, finally raw coordinates. Allow editing and preserve explicit user names. Implement bounded cache/failure fallback and document service/privacy/rate limits.
- [x] Add backward-compatible `countryCode`, display city/area, `timeZoneId`, explicit `createdAtMillis` to saved places; maintain all old favourites on migration. Saved routes retain existing creation timestamp and should get suitable origin metadata without guessing.
- [x] Destination local time is computed using IANA zone (DST aware) and compared with **the phone's last verified real physical GPS time zone**, **never mock GPS nor permanent Taiwan default**. On missing/expired physical fix or unresolved zone display "時差待確認", never a fabricated comparison. Cache verified real reference conservatively; no indefinite last-known zone.
- [x] Favourites list **source** is compact on Android and Web: place name, country/flag, city, destination local clock and verified real-zone difference, with expandable creation date/coordinates and confirmed rename/delete. Clocks update at most once/minute, no GPS-tick repaints. Real POCO and Edge acceptance remains open.
- [x] Web map-first RWD **source/UI smoke**: desktop wide map + independent right controls, tablet stacked, mobile map with viewport-fixed navigation; top-right favourites dialog with search and destination clock/verified-real-zone comparison; tap-only Web settings. 390/834/1440px Playwright map loaded with no horizontal overflow, 0 console errors and no backdrop blur. Real Edge/POCO acceptance remains open.

### Flower detection (separate phase, do not falsely claim fully automatic GPS)
- [x] **Manual bridge source**: existing consented still-image bloom detector → user-provided 3+ pixel/GPS anchors → in-anchor conservative error gate (held-out RMSE ≤60m, worst ≤120m) → explicit "save as UNVERIFIED candidate" into existing local flower catalogue. User may later personally confirm that candidate and add it through the existing ≤14-stop Valhalla route preview. No auto GPS stop injection, no screenshot retention, no automatic walking, no claim of game accuracy.
- [ ] **Real-device validation**: user-authorised actual Pikmin Bloom captures, multiple map scales/orientations and image sets; measure false positives and positional errors before considering automatic mapping or route creation. This still blocks declaring true automatic flower detection complete.

### Quality gates
- [x] Local Kotlin/JVM tests for Android and latest LAN Web source (BUILD SUCCESSFUL), Node syntax, `git diff --check`, synthetic RWD/flower tests in Playwright; existing Android theme untouched.
- [x] Web/flower source PR #90 passed CI and merged into `develop`; promoted to signed UAT 0.15.1 and retained in 0.16.1.
- [ ] **Real POCO/PC acceptance remains an open UAT testing item, not a prerequisite for creating a UAT APK.** MSI ADB had no device and PC2 tunnel did not respond. Confirm GPS modes, native Edge options, favourites, 3-mode swipe, permission denial, local-time/DST, LAN and route safety using the signed test APK before declaring production readiness.
- [x] **Signed UAT 0.16.1 already released** following verified `develop → staging` promotion; user clarified a UAT package is how physical testing can proceed. Follow future release process on request and never auto-install or modify production `main`.

## P0 · UAT 0.13.1 LAN Web feedback — true GPS, numbered route, dark Edge options (21 Nov 2026)
- [x] Source diagnosis: `/api/state.position` is virtual GPS, `restore-real` only ends mock provider; the default Taipei map centre is **not** a phone GPS fix. Never report a mock or stale fix as real.
- [x] Add paired, explicit on-demand real-phone GPS query only with fine-location permission and no active mock provider; reject Android mock fixes, expire old fixes, show separate real/virtual map markers and a status/readiness message. The browser must never move the phone by merely viewing or selecting coordinates.
- [x] Number route and flower markers, draw dashed sequence preview separately from the actual road line, list origin and stops, and add server-validated reorder/remove with preview invalidation. Retain explicit preview-ID → Start gate.
- [x] Fix native Edge/Windows map-style dropdown with explicit dark/light option background and foreground colours; retain opaque matte performance-first styling.
- [x] Add pure JVM tests for mock-fix rejection and reorder boundaries, DOM/API contracts, and loopback-only synthetic Web preview flows. MSI full JVM suite and Kotlin compilation passed (2026-11-21); Node syntax and `git diff --check` passed.
- [x] Browser loopback smoke: map loaded with origin + numbered 1/2/3 stops; reordering visibly changed route order; simulated mock-stop exposed a **clearly labelled synthetic** real fix, never a real phone location; 390px mobile stayed within viewport, browser console reported 0 errors (OpenFreeMap style warnings only).
- [ ] Real POCO acceptance of on-demand physical GPS after restore (with and without cached fix, permissions disabled, background restrictions, and active mock); ensure browser LAN stays connected on restore and GPS is never mislabelled.
- [ ] Real PC Edge verification of map style popup and numbered/reorderable waypoints, mobile widths, screen-reader labels, preview and Start gating; use CI Android verify before developing a new UAT.
- [ ] Do not promote to staging/main or claim real-device acceptance before corresponding evidence.

## P1 · 尋花 feature scope (manual confirmed flower locations first)
- [x] Existing source supports manually selected confirmed flower stops, up to 14, min-distance/duplicate guards, loop and walking-road preview via Valhalla with explicit second Start. This **does not** discover live Pikmin Bloom large flowers automatically.
- [x] Android phone already has a local flower catalogue (confirmed vs unverified import), JSON candidate import/GeoJSON export, and nearby lookup at 500 m / 2 km / 5 km with optional verified-only filter; nearest results are sorted by straight-line distance, NOT optimised walking distance.
- [x] Phase 1 Web parity **source**: authenticated, 100-result bounded nearby lookup on existing Android flower catalogue (500m/2km/5km, checked-only or explicit unverified candidates). Explicit add by catalogue ID; unverified candidates require acknowledgement; preserves ≤14 flower guards and invalidates prior preview. Synthetic Web smoke passed. Real LAN acceptance remains open.
- [x] Phase 2 **source**: optional Valhalla `optimized_route` pedestrian ordering keeps origin and end fixed, validates returned original-index permutation and never changes coordinates; explicit click, no silent GPS movement, failure retains old order, successful reordering invalidates preview and still requires Valhalla walking-road preview and separate Start. Source JVM tests and one synthetic live Valhalla protocol check passed; real POCO performance/provider availability remain open.
- [ ] Phase 3: review optional user-authorised image/manual-calibration flow with real POCO precision/recall evidence. No claim of automatic accurate real-world flower detection from unverified candidates.

## P0 · UAT 0.13.1 performance-first matte surfaces (21 Nov 2026)
- [x] User chose no expensive glass effects for PikminBee; preserve mineral/honey colours, rounded controls, map-first navigation and accessibility. No Web backdrop-filter, shaders, map snapshot or refraction effects.
- [x] Web PC/mobile panels, bottom mode dock and active walk buttons now use opaque lightweight surfaces, short shadows and no blur/animation. Android `MapGlass` already uses opaque Compose surfaces; obsolete optical helper classes are not wired into the active UI.
- [x] Add JVM source contract preventing return of backdrop blur in LAN HTML.
- [ ] Validate real Web layout on mobile/desktop, Gradle unit tests, GitHub PR verify; then squash to develop.
- [ ] Promote develop -> staging with checked merge commit; publish fixed-signed UAT 0.13.1 through manual GitHub Actions and verify package identity, certificate and artifact.
- [ ] POCO / PC real LAN acceptance and physical performance checks remain after release; do not claim measured FPS improvement before testing.


## P0 · Web map-first control UI/UX V2 (21 Nov 2026)
- [x] Rework desktop layout to map-first wide canvas and a compact operation panel; make tablet a single column and mobile map-first with safe-area spacing.
- [x] Share four mode icon + label navigation and clear selected/ARIA pressed state across mobile and desktop; add guarded 72px swipes on form-free control regions without intercepting MapLibre gestures.
- [x] Keep start only after server-verified route preview; preserve existing pairing, CSP, IP validation, phone execution and real/mock GPS permissions.
- [x] Put pause/resume/stop in a reachable mobile fixed action row **only while walking or paused**, above the fixed mode navigation. Distinguish current action using existing amber accent; stop remains danger red.
- [x] Apply restrained mineral blue / muted honey surfaces, dark/light system palette, edge highlights, accessible focus, 48px controls, reduced-motion/transparency fallbacks. This was the historical Web V2 baseline; the subsequent 0.13.1 UAT switches to opaque matte surfaces without CSS blur.
- [x] Collapse secondary default-speed settings without hiding speed/loop/route preview; preserve mobile progress and map load/error/retry.
- [x] Update HTML/JS source contract assertions; manually check unique IDs and all referenced DOM IDs.
- [x] MSI Node syntax and loopback-only mock pairing Playwright: map loaded, 0 console errors, no horizontal overflow at 390×844 / 834×1112 / 1440×900, mobile viewport-fixed modes and no overlap with tested active motion dock; no live GPS/start.
- [x] MSI offline Gradle `:app:testDebugUnitTest`: Kotlin compile + JVM unit tests BUILD SUCCESSFUL; no app/route permission changes.
- [ ] MSI `:app:lintDebug` exceeded the local 3-minute hard limit during `lintAnalyzeDebugUnitTest` (no pass/fail result); require GitHub PR `android / verify` and Lint success before merge.
- [ ] Real LAN acceptance on POCO + PC after a separately approved UAT version bump and staging promotion; confirm fixed bottom controls, WebGL responsiveness, 2 → 3, permission gating, routing preview/start, pairing/revocation.
- [x] Web true refractive glass is deliberately out of scope for PikminBee; prefer opaque matte UI to avoid added GPU cost.


## P0 · PC browser shows empty basemap after pairing (21 Nov 2026)
- [x] Reproduce actual private-LAN web UI in a **loopback-only mock pairing harness** with strict production-equivalent CSP; Playwright showed a blank map despite OpenFreeMap style/TileJSON/sprites HTTP 200. The browser tried to load `/vendor/maplibre-gl-worker.mjs?v=6.18.0` (HTTP **404**) and logged `Worker failed to load`.
- [x] Package the verified `maplibre-gl-worker.mjs` from the **exact official MapLibre GL 6.18.0 npm package** (current `.mjs` and CSS hashes match that package), serve it locally with the proper JavaScript MIME type and set its stable same-origin URL explicitly before creating `maplibre.Map`. Do not allow arbitrary query paths or external JS execution.
- [x] Align restrictive CSP `img-src` with MapLibre requirements (`'self' data: blob:` + the existing OpenFreeMap host), retain `script-src 'self'`, safe Host/Origin, PIN pairing and no public port binding.
- [x] Playwright repeat test with the revised loopback server: map status became **地圖已載入**, browser console reported **0 errors**, and style, vector tile PBF and glyph requests all returned HTTP 200. (Only nonfatal OpenFreeMap style-filter warnings remained.)
- [x] Add explicit `map.error`, WebGL context-loss handling, 15s loading timeout, retry/re-initialise and external OpenStreetMap link. Never pretend a blank map is usable; manual coordinate input remains available.
- [x] Mobile UX: reduce map height on small screens from 340px to 33svh within 240–330px, show first-screen live walk status/steps and clear tile-loading state, keep full-size desktop map, collapse explanatory text and distinguish the map view centre from a **user-selected** teleport coordinate.
- [x] Add source/UI contract tests for the local worker hash, script route, CSP, error controls, mobile breakpoint and no route auto-start; include `scripts/lan_ui_preview.mjs` as a loopback-only UI smoke harness (no real pairing, GPS or public listener).
- [x] MSI final verification passed: Kotlin compilation, **204/204 JVM unit tests**, Android Lint, Node syntax checks and `git diff --check`; loopback Playwright confirmed worker loads with zero browser errors, vector tiles/fonts HTTP 200 and responsive mobile map status.
- [ ] Submit checked develop PR; after GitHub Android verification, promote as one new stable-signed UAT rather than a debug APK.
- [ ] POCO + PC LAN acceptance: use the actual phone's private IP:8765 after PIN approval, verify map on Android Chrome/Edge and PC browser, style changes, zoom, map pick, manual coordinates, routing preview and route status, Wi-Fi/network loss and failure retry. ADB device was not connected during development.

## P1 · Health Connect compact opt-in UX (21 Nov 2026)
- [x] Screenshot review: former expanded UI repeated Health Connect aggregate / app-written estimate / all-time local estimate, technical READ_STEPS/WRITE_STEPS labels, permission notices and sensor explanations in one long card.
- [x] Rebuild optional Health Connect section as two **at-a-glance metrics** (today's HC aggregate vs PikminBee manual estimates), one concise sync state, and independent READ today steps / opt-in WRITE estimates controls. The figures are not additive. No permission prompts or manual writes just from opening Settings.
- [x] Default to compact summary, expand to manage consent, and reveal advanced diagnostics only on request (all-time estimate, underlying source, permission grants, pending/sync errors, local sensor fallback, permission management).
- [x] Maintain existing consent dialog, current queue/worker logic and manual-entry metadata; preserve optional no-Health-Connect operation.
- [x] Add pure JVM tests ensuring a write grant is not confused with sync opt-in, inaccessible daily figures are not shown, and immediate sync only appears for authorised opted-in pending work.
- [x] MSI Kotlin compilation, **204/204** JVM unit tests (0 failures/errors/skipped) and Android Lint passed; no APK packaging.
- [ ] Submit checked develop-only PR and require GitHub Android verify before squash merge.
- [ ] POCO visual acceptance: light/dark theme, small screen, 23006/21077 independent counts, READ-only/WRITE-only/denied/revoked, pending queue/failure disclosure, optional sensor mode; verify no regression in background sync.
- [ ] UAT APK only after release flow; no changes to main/production.

## P0 · Restore floating controls and unify four mode tabs with swipe (21 Nov 2026)
- [x] Keep the **floating expanded panel** as in the earlier screenshot: row 1 **start/pause/resume, stop, real GPS, virtual GPS**; row 2 **flower vision, speed, open app map, hide overlay**. Do not replace its quick actions with app-navigation tabs. Preserve original 2×4 vector-button layout and safe GPS service controls.
- [x] Replace the **four icon-only shortcuts at the bottom of the expanded main-app detail sheet** with the exact same **瞬移／路徑／尋花／收藏** tabs as the bottom map dock. Reuse one Compose tab component so selected background, icons, labels and accessibility always match; announce current selection for TalkBack.
- [x] Horizontal swipes within the expanded sheet's content or mode tab bar, and across the map's bottom mode dock, switch to the previous/next page at a bounded ≥72dp gesture threshold. Keep the sheet open, scroll new page to top; vertical sheet scrolling and the actual map's pan/pinch gestures remain independent.
- [x] Add pure JVM regression tests for swipe direction, minimum threshold, page edges, and floating panel's exact former 2×4 action grouping.
- [ ] Finish Kotlin compilation/JVM tests/Android Lint, checked DEV PR and POCO visual/gesture acceptance.
- [ ] If a new UAT cycle is requested, follow normal semantic-version bump and fixed-signed staging workflow; do not modify production main.

## P0 · Same-private-network PC browser controller and single UAT build (21 Nov 2026)
- [x] Add a **user-started, connected-device foreground LAN control service** that does not require mock GPS activation just to open the browser. No autonomous service restart after a process kill. Settings shows reachable phone IP:8765, fresh 6-digit PIN, phone-side approval/rejection, disable/revoke, and a notification stop action.
- [x] Reuse the existing bounded local HTTP server, private-Wi-Fi-only listener, strict Host/Origin checks, PIN rate limit, 30-minute session, and local-only MapLibre bundle. No public port forwarding, VPN/Tailscale dependency, or new cloud backend.
- [x] Implement PC controls: **瞬移 / 路徑 / 尋花 / 收藏** responsive web map, selectable route origin, waypoints, saved places and saved routes, speed, walk controls and real-time location, current route leg and estimated steps.
- [x] Split remote route planning into **/api/route/preview** (no GPS movement) and **/api/route/start** (fresh matching preview ID + explicit second click). Reject stale, reused or unpreviewed routes; enforce extra browser acknowledgement for >40m pedestrian road snap, guard system mock/fine-location permission, and prevent waypoint edits while walking.
- [x] Add regression tests for preview gates, stale/edited loop and double-start, and browser assets/service manifest requirements. Check JavaScript syntax with Node and Android Kotlin/JVM tests/Lint.
- [x] MSI validation: Kotlin compile, **196/196** JVM tests (zero failures/errors/skips), Android Lint and Node.js browser-script syntax all passed; `git diff --check` clean.
- [ ] Complete checked DEV PR, UAT semantic bump, checked develop→staging merge, manually dispatched fixed-signer UAT APK; verify package ID/versionCode/signing digest. No production main or POCO installation.
- [ ] POCO/PC acceptance: same Wi-Fi or a routed private LAN, connect using phone IP:8765, enter PIN and approve on Android, select origin, preview road, start, pause/resume/stop, verify 2→3/step status, saved routes and rejection of invalid permissions, refresh/revoke/expiry/disconnect and network change. No device connected via ADB at development time.

## P0 · Route leg clarity, matching overlay navigation and optional integrations (21 Nov 2026)
- [x] Correct route status semantics: `nextWaypoint=3` means the phone is walking from waypoint **2 → 3** (not “at 3/4”). Surface `activeLegLabel` consistently in the main mini-bar and floating summary, with start/arrival cases and JVM tests.
- [x] Rename the arrow before Clear: **撤銷上一點** removes just the latest drafted waypoint; **清除全部** removes the entire draft. Avoid ambiguous icon-only destructive actions.
- [x] **Historical decision, superseded by current floating UX refinement:** PR #70 temporarily made the floating lower row mirror the main dock. The floating lower row now returns to **flower vision / speed / map / close** as requested, while the four app mode tabs are shown in the main detail sheet and map dock. Long-pressing the floating header still hides it without stopping the walk.
- [x] Restore **跟隨系統** alongside Light/Dark, making SYSTEM the default for new users and preserving an existing user selection. Add JVM appearance policy tests.
- [x] Health Connect stays **optional and collapsed** by default; offer separate explicit READ_STEPS for today’s total and WRITE_STEPS for exporting *manual virtual estimates*. Require a purpose-specific consent dialog before enabling the virtual export; do not silently turn on synchronisation on a permission grant. Add manage/revoke access and keep Android sensor fallback behind advanced settings.
- [x] Wi-Fi panel is separated as an optional settings card; existing LAN server/pairing code inspected, design and unimplemented security gates recorded in `docs/WIFI_REMOTE_CONTROL_PLAN.md`.
- [x] MSI Kotlin compilation, **189/189** JVM tests (0 failures/errors/skips), Android Lint and `git diff --check` passed (Gradle exit 0). Submit the normal checked DEV PR; this batch is **not** yet in UAT 0.9.1.
- [ ] Real POCO acceptance: visual current 2→3 segment/arrival, 4 overlay navigation icons to proper tabs, undo vs clear, automatic system theme changes, HC skip/READ-only/WRITE-only/deny/revoke without GPS disruption.
- [ ] LAN later: implement paired web controller with preview → explicit start, reconcile shared mode state and security. **Current /api/route/start still starts directly**, so do not claim remote preview parity.

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
