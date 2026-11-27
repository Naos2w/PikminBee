# PikminBee V3 — floating controls and Health Connect

Status: DEV implementation in progress. No UAT APK has been promoted from this feature branch.

Design source: \`C:\Naos\02.Git\personal\design-previews\PikminBee-UI-V3-20261119.fig\`.
12 editable screens: light/dark map, route editor, floating teleport/route/flower/saved modes,
settings, skippable permissions, Health Connect and related state screens.

## Overlay contract

- Four tabs always available: **瞬移**, **路徑**, **尋花**, **收藏**.
- A mode-tab change, coordinate selection or draft edit **never** starts virtual GPS.
- The user can type a coordinate, press **瞬移**, add/reorder/delete waypoints, start
  route/flower walking, load favourites and save routes without switching app pages.
- Route/flower planning starts with the currently parked virtual fix or a recent device
  fix, not the first user-added destination. If no trustworthy origin is available,
  show an error; never assume a hard-coded latitude/longitude.
- Pause, resume, stop and restore-real-GPS remain explicit global controls.
- Appearance, permissions and HC consent stay in the **設定** page in the main app.

## Steps and Health Connect

- Distinguish **real sensor steps** from **virtual walking estimates**. Both now have
  separate DEV implementations. GPS simulation is **never** a hardware step sensor.
- On Android 14+ with SDK Extension 20+ and Health Connect available, use the
  Health Connect on-device step source behind explicit READ_STEPS consent. Its
  aggregates may include other sources and should not be labelled sensor-only.
- On older devices, offer user-started TYPE_STEP_COUNTER foreground tracking
  after ACTIVITY_RECOGNITION permission. Show a notification and clear stop action;
  record only steps observed while running. The feature cannot guarantee step
  coverage after Android force-stop, process death, reboot or sensor unavailability.
- Legacy sensor exports require separate user opt-in and WRITE_STEPS permission,
  with stable source-specific IDs; do not export when native on-device HC capture
  exists, avoiding writing an extra copy of the same hardware steps.
- During the simulated moving phase, integrate travelled route distance divided by a
  configurable future stride length (current source constant: **0.72 metres per step**).
  Pauses, stops and teleports do not add distance or steps.
- Session records persist locally with stable UUID-based record IDs, start/end times,
  estimated step counts and sync acknowledgements. Pending data is never silently
  discarded on failed network/IPC sync or a service stop.
- **Updated owner decision (2026-11-27):** route estimates are queued while moving;
  HC export is automatic when Android grants WRITE_STEPS. There is no additional
  app-side authorization preference or switch. Legacy history is not imported.
- Export virtual estimates with \`Metadata.manualEntry\`, not an automatically
  recorded or physically sensed method. \`clientRecordId\` is stable on retries.
- WorkManager exports pending estimates at periodic moving checkpoints and pause/stop/arrival.
  If the user revokes permission, leave records pending until re-authorized.
- Users can revoke the Android write permission to stop exports and clear local
  *unsynced* route estimates when the route is stopped.
  Already exported records must be managed in Health Connect; deleting the local
  queue does not delete previously exported health data.
- Different apps/sensors can supply overlapping step data. Do **not** claim
  deduplicated total physical steps from this simulated tracker.
- Physical step capture and HC source selection are implemented in DEV 0.1.5
  but **not** verified on POCO: real permission dialogs, live sensor values,
  reboot/force-stop recovery and actual HC record identity all need device UAT.

## Acceptance requirements

1. POCO: real overlay visible atop other apps; touch, drag, keyboard and 4-mode
   switching all work without enabling extra Android permissions inside the overlay.
2. Route starts at the current position; no jump to route waypoint 1; app can
   pause/resume/stop without losing the draft.
3. Estimated step count rises only while moving, including a looping flower route;
   pausing and teleporting do not add steps. Check minutely rounding and persistence.
4. HC permission request can be granted/refused/revoked; missing/revoked permission makes zero writes.
5. With system write permission, new route intervals are exported as manually entered
   StepsRecords during movement without another app-side switch or authorization.
   Retrying does not produce duplicate records.
6. No HC records are exported on merely opening the app or restoring a previous position.
7. Compare actual Android UI against the approved OpenPencil design on POCO.
8. Follow normal PR CI and explicit staging UAT packaging, preserving signer and
   existing UAT versionCode upgradeability.

References:
- https://developer.android.com/health-and-fitness/health-connect/get-started
- https://developer.android.com/health-and-fitness/health-connect/write-data
- https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/metadata/Metadata
- https://developer.android.com/jetpack/androidx/releases/work
