# PikminBee · Naos Liquid Glass V3 · Android Release Notes

## Approved visual source
- The only canonical design specification is `C:\Users\Naos\.agents\skills\naos-universal-uiux\SKILL.md`; follow its `references/AGENT_GUIDE.md` and `references/themes.json` rather than creating independent palettes.
- User-approved editable A/map-first artboards: `C:\Naos\02.Git\personal\design-previews\PikminBee-V4-A-Naos-LiquidGlassV3-20261120.fig`.
- Approved interactive design reference: `PikminBee-LiquidGlass-V3-Interactive-20261120.html`; design notes in `PikminBee-LiquidGlassV3-DESIGN.md`.

## DEV implementation
1. `NaosDesignSystem.kt`: all six Light/Dark canonical themes and Regular/Clear/Blur semantic materials. Sage + Regular is default. Per-device choices persist in `AppPreferences` and are presented on the settings screen.
2. `MapFirstShell.kt` + `MapFirstComponents.kt`: full-screen native map, floating top coordinate entry, 4 vector-only tabs in bottom dock, dedicated sheet handle, route quick actions and small recenter control. No left-hand drawer or full-screen map gesture interceptor.
3. `MapPane.kt` + `NaosMapGlassView.kt`: snapshot of the map provider's pixels only after the map camera goes idle, cleared when movement begins. Android 13+ AGSL samples the real map snapshot and displaces it around rounded glass edges for Regular/Clear; older devices or missing/invalid snapshots use a visibly frosted fallback. Blur has no edge displacement. Snapshot is not taken on every composition or animation frame. The shader is reused until its map snapshot, position, dimensions or material changes.
4. `FloatingControls.kt`: collapsible draggable bee and compact quick panel (four semantic vector modes, status, two primary actions). Advanced routes, speeds, saved locations and Health Connect links remain accessible on deliberate expansion. Cross-app overlays **cannot read backdrop pixels from other apps**; they use a tinted/frosted canonical palette without claiming lens refraction.
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
- Check collapsed overlay can be dragged on POCO, expanded quick panel is compact and theme matches, advanced functionality reachable.
- Verify no regression in location permissions, Health Connect opt-in, and UAT upgrade over 0.3.1 with preserved data.
