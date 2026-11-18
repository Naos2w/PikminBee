# PikminBee Wi-Fi browser controller (v0.4 source prototype)

This is a **private LAN-only, opt-in** controller. It shares the same Android GPS foreground service as the phone UI and floating bee controls. It does not install or operate a second mock-location engine.

## Phone + MSI user flow

1. On the phone, connect to a trusted private Wi-Fi network.
2. Start a PikminBee mock-location session through the phone, with the explicit Android developer-option permission already granted.
3. Open the phone's **Wi-Fi 遠端控制** section and tap **開啟網頁控制**.
4. The phone shows a private network URL like `http://192.168.1.50:8765/` and a fresh six-digit pairing PIN.
5. On MSI or another device **on the same Wi-Fi**, open the URL; enter the PIN.
6. **Return to the phone and tap 核准裝置**; pairing does **not** create a remote control session before this step.
7. From the browser, click OpenFreeMap to choose coordinates, teleport, select ordered stops, start a pedestrian route, pause, resume, stop or restore real GPS. Browser map style supports **Auto/Liberty/Bright/Positron/Dark** without a Google Maps API Key.
8. Tap **關閉並撤銷配對** on the phone to disable the listener, revoke sessions and rotate pairing on the next start.

**All operations require user intent.** Wi-Fi control is off by default; stopping the GPS foreground service also stops the Wi-Fi listener. When the bound Wi-Fi IPv4 address disappears/changes, the listener automatically shuts down. It never binds to `0.0.0.0`, a public address, or a mobile network.

## API and control semantics

- `GET /` / `GET /controller.js`: locally served controller UI.
- `POST /api/pair`: six-digit PIN + browser device name; returns short-lived pending ticket only.
- `GET /api/pair/status` with `X-PikminBee-Pending`: polling while the phone decides. An approved browser receives one 30-minute bearer session.
- All control calls require the in-memory `X-PikminBee-Token` header.
- `GET /api/state`: current simulated position, mode, moving/paused/parked state, route draft and decimated active route. Browser polls every two seconds (no WebSocket in this prototype).
- `POST /api/teleport`, `/api/points/add`, `/api/points/undo`, `/api/points/clear`, `/api/route/start`, `/api/speed`, `/api/control`: shared existing location-service operations. Speed presets in all control surfaces use km/h (Slow 0.8, Walk 1.4 default, Fast 10.8) and are saved locally. The remote speed endpoint requires a paired browser token and updates an active walk without restarting its route.
- `stop` keeps the simulated GPS where it is. `restore-real` explicitly ends mock mode **and closes the web listener**.
- Routing uses Valhalla pedestrian roads. Failed route planning returns an error state; it never silently switches to a straight-line walking path.
- Server, **not just browser buttons**, checks route limits: normal route 15 stops, looping normal route 14 stops, flower route **14 stops**. Flower stops require an active mock GPS origin and cannot be within 2 m of the start or 25 m of an existing flower stop. Invalid requests return HTTP 409 without changing the draft; invalid speed presets return HTTP 400 before network route planning. A failed external route response does not move GPS.
- The browser disables Add/Start when preconditions are missing, and shows a per-mode limit. These controls are hints only; direct API requests are independently validated.

## Safety and limitations

- This version uses **plain HTTP on a private Wi-Fi IP**. Authentication cannot prevent a malicious actor capable of intercepting local traffic from reading network packets. **Do not use on shared/public/untrusted Wi-Fi**, do not expose port 8765 to the internet, and do not enable router port forwarding.
- Credentials and session tokens are memory-only. They are not written to URLs, cookies, cloud accounts or device storage. Browser refresh requires new phone approval.
- Bad PIN entries are throttled; five failed entries lock that peer for five minutes. All API requests are rate-limited. Session tokens expire and bind to the approved peer IP. Pairing revokes prior sessions only after a new phone approval.
- A bounded HTTP server accepts only private IP clients and validates the `Host`, `Origin`, request method, headers and JSON body length. Cross-origin browser control is not enabled.
- Browser map: **OpenFreeMap Auto/Liberty/Bright/Positron/Dark**, using a locally bundled, hash-verified copy of **MapLibre GL JS 6.18.0** (BSD-3-Clause; see [THIRD-PARTY.md](THIRD-PARTY.md)). Browser JavaScript/CSS is served from the phone, with a CSP disallowing third-party executable scripts. The map still needs internet access to fetch OpenFreeMap vector tiles/style assets. If map tiles cannot load, latitude/longitude input and remote controls remain available.
- Browser style preference is **local to that browser** (no phone style mutation); the phone offers the same four named keyless map styles and an automatic appearance mode. Switching map styles does **not** trigger teleport or GPS route commands.
- Mobile app map remains OpenFreeMap or official Google Maps SDK. The browser does not include Google Maps JS, which would need a separately authorised web API key/billing.
- Android targetSdk 36: current `INTERNET` permission covers LAN access. **Before updating targetSdk to 37**, add the Android 17 `ACCESS_LOCAL_NETWORK` runtime permission flow.
- Browser pairing, phone approval, map rendering, LAN switching, HTTP errors and HyperOS background operation are **not device-tested yet**. Code build and JVM tests alone do not qualify this as released. Grouped POCO UAT must include browser style switching, route limits, loop-at-cap error, duplicate/near-origin flower rejection, and phone/web draft synchronisation.
- For internet/public-network access in the future, design a genuinely encrypted and mutually authenticated transport, not this private-LAN HTTP endpoint.

## Validation approach without APK churn

Every edit: compile Kotlin only where useful. Before PR review: `./gradlew :app:testDebugUnitTest :app:lintDebug --offline`. Only after a coherent UAT milestone: manual `assembleDebug`. Keep the GitHub PR in Draft until ready for review; this avoids unnecessary Actions Android runner minutes.
