# PikminBee · Naos Liquid Glass V3 · Android Release Notes

## Approved visual source
- The only canonical design specification is `C:\Users\Naos\.agents\skills\naos-universal-uiux\SKILL.md`; follow its `references/AGENT_GUIDE.md` and `references/themes.json` rather than creating independent palettes.
- User-approved editable A/map-first artboards: `C:\Naos\02.Git\personal\design-previews\PikminBee-V4-A-Naos-LiquidGlassV3-20261120.fig`.
- Approved interactive design reference: `PikminBee-LiquidGlass-V3-Interactive-20261120.html`; design notes in `PikminBee-LiquidGlassV3-DESIGN.md`.

## V4-B UAT feedback repairs (source only, 20 November)
- Product theme locked to Mineral + Regular. Settings expose Light / Dark only; old family/material preferences no longer affect appearance. Mineral surfaces give the calm foundation; honey `#D9AC4E` identifies expanded controls, green `#78A58C` represents movement, and neutral white/black represents idle status.
- Collapsed overlay is an independent vector bee logo with **transparent background and invisible 54dp touch area**, not a button surface. A real click listener expands the panel; touch drag saves placement. MOVING has gentle green flying animation, PAUSED uses amber hover, and IDLE/PARKED stop animations. Android disabled-animation settings are honoured.
- Route preview is preserved when the editor sheet re-enters composition. While an asynchronous Valhalla request is running, ordinary GPS jitter no longer invalidates the pending result; route-point/loop edits still do. On successful retrieval, the result is drawn on the native map, camera fitted to the road, and the editor is dismissed for map visibility. Failure reasons are displayed in the editor. Preview never changes the GPS location.
- Native map pan clears stale optical snapshots, but keeps a mineral-tinted translucent gradient, glass rim and legible material through the gesture. Genuine map-pixel AGSL refraction resumes on idle when a fresh snapshot is available; continuous moving refraction is **not claimed**.
- Editable inspected design artboard: `C:\Naos\02.Git\personal\design-previews\PikminBee-V4-B-Mineral-Honey-Logo-20261120.fig` with same-name PNG. A viewable copy is included in this repository at [`docs/pikminbee-v4b-mineral-honey.png`](pikminbee-v4b-mineral-honey.png). This preview depicts four logo states and both product brightness modes; it is not a native runtime screenshot.
- UAT 0.5.1 was the source of the user's reported issues; these are **new DEV fixes** until promoted through a subsequent UAT cycle. POCO physical-device acceptance is still required.

## V4-A overlay follow-up (source only, 20 November)
- Focused OpenPencil revision: `C:\Naos\02.Git\personal\design-previews\PikminBee-V4-A-Overlay-Icons-20261120.fig`, with updated single-level PNG `PikminBee-V4-A-Overlay-SingleLevel-20261120.png` in the same directory (Light/Dark Sage). Earlier canonical A-map design stays intact.
- The floating bee is a small draggable collapsed button; expansion shows precisely eight semantic vector icons in two rows, a short status and separate real/estimated step counts. The user approved a **single-level overlay with no advanced-actions expander**; route editing, coordinate entry, favourites and detailed Health Connect settings remain exclusively in the main App. Long-press descriptions, a replayable three-step help dialog and a persisted first-use toggle live in Settings.
- Main map remains the sole map/route editor. Overlay Map returns to the app, never stops a route; Flower Vision returns to the permission-gated existing screen-capture UI. Stopping travel, restoring real GPS, restoring the remembered parked virtual fix, collapsing and closing are distinct operations.
- The map sheet displays road-progress distance, upcoming stop, estimated remaining minutes and speed where an active route exists. No location or screen effects are triggered by a navigation tab alone.
- Optical glass fades to frosted backing when the native map moves, avoiding stale distorted pixels and continuous snapshot capture. Android GPU correctness and map smoothness remain subject to POCO UAT.
- This set of source changes is not included in already-signed UAT 0.4.1; no new APK, `staging`/production promotion or CI wait is part of the source-only task.

## DEV implementation
1. `NaosDesignSystem.kt`: all six Light/Dark canonical themes and Regular/Clear/Blur semantic materials. Sage + Regular is default. Per-device choices persist in `AppPreferences` and are presented on the settings screen.
2. `MapFirstShell.kt` + `MapFirstComponents.kt`: full-screen native map, floating top coordinate entry, 4 vector-only tabs in bottom dock, dedicated sheet handle, route quick actions and small recenter control. No left-hand drawer or full-screen map gesture interceptor.
3. `MapPane.kt` + `NaosMapGlassView.kt`: snapshot of the map provider's pixels only after the map camera goes idle, cleared when movement begins. Android 13+ AGSL samples the real map snapshot and displaces it around rounded glass edges for Regular/Clear; older devices or missing/invalid snapshots use a visibly frosted fallback. Blur has no edge displacement. Snapshot is not taken on every composition or animation frame. The shader is reused until its map snapshot, position, dimensions or material changes.
4. `FloatingControls.kt`: draggable collapsed bee and single-level compact quick panel (two rows of eight action icons, status and estimated/physical steps). No second-level floating route editor, coordinate field or saved-route manager. The main App retains those detailed actions. Cross-app overlays **cannot read backdrop pixels from other apps**; they use a tinted/frosted canonical palette without claiming lens refraction.
5. Route selection/undo/reselection and direct teleport behaviour from the previous map-first UAT remain wired to the Android location engine. Permission checks still gate location effects, and the physical/virtual step ledgers are unchanged.

## Validation and limits
- Local Gradle `compileDebugKotlin testDebugUnitTest lintDebug assembleDebug` passed; 152 tests, 0 errors/failures/skipped.
- The existing 0.3.1 UAT has not been changed until CI-gated promotion of new 0.4.1.
- The OpenPencil/HTML preview demonstrated pixel displacement and Light/Dark/Regular/Clear/Blur variants. **It does not prove the Android GPU/driver renders the shader correctly on POCO.** There was no connected phone; map pan/zoom, AGSL output, transparent overlays, keyboard and Health Connect consent still need physical-device UAT.
- Device-specific driver/shader compilation failures use the fallback instead of crashing. Treat unsupported GPU/render paths as Blur-like rather than visually claiming true glass.
- No new dependency, service, background scheduler, elevated permission or real GPS/HC writes were introduced for rendering.
- Production `main` remains untouched. Release follows feature PR → `develop`, version PR → `develop`, `develop` → `staging` merge commit, manual fixed-signer UAT build and artifact verification. Do not install to the phone automatically.

## UAT acceptance checklist
- Confirm real Google/MapLibre map remains draggable and pinch-zoomable outside the small controls and responds to map-edge gestures.
- Confirm top coordinate input/arrow teleports only on explicit action; restore real GPS is available.
- Confirm route numbered map taps, undo and editing a numbered marker, and that starting follows actual roads.
- Check Sage/Clay/Graphite/Sand/Mineral/Mauve in Light and Dark, switching Regular/Clear/Blur while checking legibility.
- Test glass with high-contrast lines underneath; verify actual refraction in device recording if claimed, not mere tint/blur.
- Confirm no flicker/excess memory/performance cost when panning repeatedly, and frosted fallback on unsupported hardware.
- Check collapsed overlay can be dragged on POCO, expanded single-level 2 × 4 icon panel is compact and theme matches, all eight icons work and no advanced-action drawer remains. Full route/coordinate editing is available via the Map icon in the main App.
- Verify no regression in location permissions, Health Connect opt-in, and UAT upgrade over 0.3.1 with preserved data.
