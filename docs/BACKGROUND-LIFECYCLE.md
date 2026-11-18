# PikminBee — Android background execution and safe session recovery

**Status:** v0.3 source implementation with local JVM tests. HyperOS and POCO background behaviour require UAT; there is no guarantee of survival after force-stop, battery manager intervention, or manufacturer-specific task cleanup.

## What happens to a running session?

- **Home button / changing apps / screen off:** The location foreground service is kept separate from the Activity. It continues while Android allows it to run, and has a persistent notification with pause/resume, stop walking and restore real GPS controls.
- **Swipe the Activity from Recents:** `android:stopWithTask="false"` keeps the service independent of UI task removal on supported devices. `onTaskRemoved` records the last position; it does **not** schedule alarms, start new services or bypass the OS.
- **Android reclaims the process and later restarts a sticky foreground service:** `START_STICKY` may re-create the service with a **null Intent**. PikminBee reads a short-lived checkpoint and attempts to restore **only the last stationary simulated coordinate**. Previously moving routes stay stopped. LAN access and overlay controls are not silently reopened.
- **If background restart is not permitted:** The app stops gracefully and retains the checkpoint. Open the UI to request restoration manually; Android 14+ can refuse creation of a location foreground service from the background when the while-in-use permission is unavailable.
- **User selects Restore real GPS:** The mock provider and session are stopped and the checkpoint is explicitly deleted. This prevents old test locations from being automatically resurrected.
- **Force-stop, reboot, revoked developer-option mock-app selection:** No background restart promise. After opening PikminBee again, any recent checkpoint is visible in the background section and can be manually resumed if the OS permits.

## 懸浮蜜蜂：從已儲存路點開始尋花（DEV 原始碼功能）

- 在 PikminBee 主畫面先設定尋花路點，最多 **14 點**；明確啟用模擬位置、允許懸浮窗並點「🐝 開啟懸浮控制」。這個功能沒有增加獨立 GPS 引擎或背景自啟機制。
- 點懸浮蜜蜂展開，按「🌸 開始尋花」，才會從**當下模擬位置**以預存的路點向 Valhalla 查詢**循環行人道路**。速度採 App／懸浮窗共用的三種 **km/h** 預設：**慢走 0.8 km/h、走路 1.4 km/h（出廠預設）、快走 10.8 km/h**；最後一項接近跑步速度。使用者可**臨時切換目前速度**，也可在 App 或懸浮窗明確點「設為預設」保存下次開啟 App 使用的速度；臨時切換**不會覆寫個人預設**。行走中調速無需重新規劃道路。按鈕在沒有有效草稿、正在步行或查詢中停用；仍有服務端驗證以阻擋無效意圖。
- 查詢中**不移動**。只有得到完整合格的步行道路，才會使用原本的前景 GPS 服務開始尋花；失敗**不會改成直線行走**。
- 查詢期間「停止行走」會顯示「取消規劃」，也能透過其他明確停止／清除／瞬移／恢復真實定位命令取消舊查詢；舊網路回應不會再啟動步行。已開始行走仍可暫停、繼續、停止／原地停留。
- 服務被 Android 回收後，**仍只可恢復最後的原地位置**；不會自動重跑尋花、恢復浮窗或重開 Wi-Fi。路點草稿存在 App 資料，並非會自動執行的排程。
- 真正的操作手感、懸浮窗版面、失敗提示和 Android／HyperOS 背景限制，仍須 POCO 實機 UAT 驗證，程式碼通過測試不表示實機已通過。

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
