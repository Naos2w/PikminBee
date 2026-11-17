# PikminBee — Android background execution and safe session recovery

**Status:** v0.3 source implementation with local JVM tests. HyperOS and POCO background behaviour require UAT; there is no guarantee of survival after force-stop, battery manager intervention, or manufacturer-specific task cleanup.

## What happens to a running session?

- **Home button / changing apps / screen off:** The location foreground service is kept separate from the Activity. It continues while Android allows it to run, and has a persistent notification with pause/resume, stop walking and restore real GPS controls.
- **Swipe the Activity from Recents:** `android:stopWithTask="false"` keeps the service independent of UI task removal on supported devices. `onTaskRemoved` records the last position; it does **not** schedule alarms, start new services or bypass the OS.
- **Android reclaims the process and later restarts a sticky foreground service:** `START_STICKY` may re-create the service with a **null Intent**. PikminBee reads a short-lived checkpoint and attempts to restore **only the last stationary simulated coordinate**. Previously moving routes stay stopped. LAN access and overlay controls are not silently reopened.
- **If background restart is not permitted:** The app stops gracefully and retains the checkpoint. Open the UI to request restoration manually; Android 14+ can refuse creation of a location foreground service from the background when the while-in-use permission is unavailable.
- **User selects Restore real GPS:** The mock provider and session are stopped and the checkpoint is explicitly deleted. This prevents old test locations from being automatically resurrected.
- **Force-stop, reboot, revoked developer-option mock-app selection:** No background restart promise. After opening PikminBee again, any recent checkpoint is visible in the background section and can be manually resumed if the OS permits.

## Checkpoint details

- Only the **last virtual WGS84 coordinate and timestamp** are stored in private app preferences; no route geometry, flower details, browser token or Wi-Fi PIN are in the checkpoint.
- One checkpoint immediately when starting a mock session; while moving, it refreshes **at most once every five seconds**. Pause / stop / task removal force a last write.
- Checkpoints are rejected when malformed, in the future, or **older than 12 hours**. Resuming from the UI repeats the expiry check.
- Checkpoints and history are not included in cloud backup (application backup disabled).
- A system-initiated restart never automatically resumes walking. The user's live route and browser-control state are intentionally not restored.

## Settings the user may choose to inspect

Inside PikminBee, open **背景執行與恢復**. For relevant devices, the app displays guidance for HyperOS / POCO / Redmi. A standard Android **Application details** link is provided; **PikminBee does not silently change system battery policies or use accessibility services**.

Possible user-controlled options, whose labels vary by HyperOS version:
- Lock the app in the Recent apps interface (when supported).
- Review battery/background operation restrictions for PikminBee.
- Enable app notifications to access its foreground-service controls.
- Select PikminBee as the device's mock-location app under Android Developer options when intentionally testing.

## UAT matrix (deliberately deferred until POCO is connected)

- [ ] With a running route, press Home, lock/unlock the screen, verify movement and notification controls.
- [ ] Stop route from notification, check virtual location stays stationary rather than returning home.
- [ ] Swipe only the Activity from Recents, verify behaviour with/without the HyperOS lock.
- [ ] Simulate low-memory process reclaim **without force-stop**; on a supported OS restart, check position is PARKED and no movement/LAN/overlay is automatically resumed.
- [ ] Try while-in-use location permission denied and an invalid mock-location developer setting; confirm graceful error.
- [ ] Force-stop through Android Settings and reboot. Confirm nothing tries to bypass restrictions; the user must re-open the app.
- [ ] Restore real GPS and verify that saved checkpoint is cleared.
- [ ] Test an expired 12-hour checkpoint and manual resume option.
- [ ] Confirm no APK is built during ordinary development; package a single UAT APK for this integrated batch.

## Platform documentation

- Android Service `START_STICKY`: https://developer.android.com/reference/android/app/Service
- Foreground service background restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
