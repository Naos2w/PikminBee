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

- Distinguish **real sensor steps** from **virtual walking estimates**. Only the
  latter are presently implemented. A GPS simulator is not a hardware step sensor.
- During the simulated moving phase, integrate travelled route distance divided by a
  configurable future stride length (current source constant: **0.72 metres per step**).
  Pauses, stops and teleports do not add distance or steps.
- Session records persist locally with stable UUID-based record IDs, start/end times,
  estimated step counts and sync acknowledgements. Pending data is never silently
  discarded on failed network/IPC sync or a service stop.
- **Default HC export is OFF**. Only after the user explicitly enables estimated
  step export and grants WRITE_STEPS can the app insert these records.
- Export virtual estimates with \`Metadata.manualEntry\`, not an automatically
  recorded or physically sensed method. \`clientRecordId\` is stable on retries.
- WorkManager performs opt-in background export after each completed session.
  If the user revokes permission, leave records pending until re-authorized.
- Users can switch off future exports and clear local *unsynced* virtual records.
  Already exported records must be managed in Health Connect; deleting the local
  queue does not delete previously exported health data.
- Different apps/sensors can supply overlapping step data. Do **not** claim
  deduplicated total physical steps from this simulated tracker.
- Real step sensor capture and any independent physical-step syncing are separate,
  not yet implemented and must not be represented as completed.

## Acceptance requirements

1. POCO: real overlay visible atop other apps; touch, drag, keyboard and 4-mode
   switching all work without enabling extra Android permissions inside the overlay.
2. Route starts at the current position; no jump to route waypoint 1; app can
   pause/resume/stop without losing the draft.
3. Estimated step count rises only while moving, including a looping flower route;
   pausing and teleporting do not add steps. Check minutely rounding and persistence.
4. HC permission request can be granted/refused/revoked; opt-in OFF makes zero writes.
5. With permission and opt-in, a completed simulated session appears in HC as a
   manually entered StepsRecord. Retrying/force stopping does not produce duplicates.
6. No HC records are exported on merely opening the app or restoring a previous position.
7. Compare actual Android UI against the approved OpenPencil design on POCO.
8. Follow normal PR CI and explicit staging UAT packaging, preserving signer and
   existing UAT versionCode upgradeability.

References:
- https://developer.android.com/health-and-fitness/health-connect/get-started
- https://developer.android.com/health-and-fitness/health-connect/write-data
- https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/metadata/Metadata
- https://developer.android.com/jetpack/androidx/releases/work
