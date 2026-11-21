# PikminBee · Wi-Fi 控制設計與安全驗收

Status: proposed follow-up. This document does **not** imply that the LAN API has been changed or the phone has been tested.

## Goal and scope

Use a PC or second phone's browser as an optional, larger controller for the same
PikminBee instance already running on the Android phone. No cloud server, no
public internet exposure and no independent GPS engine.

## Current code verified

- `LanController` exposes an HTTP service at the current private Wi-Fi IPv4
  address, port 8765, only after a manual enable action in the settings.
- A randomly rotated six-digit PIN creates a **pending** pairing; the phone
  owner explicitly approves or denies the request. A short-lived browser
  session is bound to the connecting client address; the controller closes on
  Wi-Fi change/disconnection and revokes sessions when turned off.
- The phone currently needs an active mock-location service for this option.
- The HTTP transport is **not encrypted**. Treat current LAN control as
  experimental and use only on a trusted private Wi-Fi, never public Wi-Fi.
- Current `/api/route/start` still plans and immediately starts walking. It
  does **not** follow the new preview → confirm → start rule and must be
  fixed before considering the LAN workflow fully accepted.

## Proposed UI/UX

1. Settings → **Wi-Fi 遠端控制 · 選用**. Off by default; display
   Off / Ready / Pairing request / Connected / Connection lost.
2. Enable only after explaining same-LAN restrictions. Present local URL,
   share/copy action and six-digit PIN; optional QR **must not embed the PIN
   in a public link**.
3. A paired PC or other phone opens a responsive browser dashboard:
   **瞬移 / 路徑 / 尋花 / 收藏**, matching Android's bottom navigation.
   Show current segment (e.g. 2 → 3), session-only estimated steps, route
   position and start/pause/resume/stop using the *same* service state.
4. Route editor must first call a read-only preview endpoint and show the
   snapped pedestrian geometry/distance. Starting needs a second explicit
   user action and mandatory phone-side approval if the snap offset is high.
   Never restart or move GPS on connection, page load or re-pairing.
5. Add active-session revoke, last-connected-device label, short expiry and
   a permanent Kill/Disable button in Android settings. Auto stop LAN on
   private Wi-Fi loss and on explicitly disabling it; leaving Wi-Fi
   control must **not** stop a route unless the user requests stop.
6. Accessibility: keyboard navigation, 48dp-equivalent click targets,
   clear playback status and no icon-only mystery actions.

## Permissions / safeguards

- For a private-LAN HTTP server on the current Android-16-targeting app:
  `INTERNET` and network-state access already exist. This is not an
  ordinary Wi-Fi scanning feature.
- On future targetSdk 37 (Android 17), review new runtime
  `ACCESS_LOCAL_NETWORK` permission before upgrading the target SDK,
  using Android developer guidance for accepted incoming TCP connections.
- Never request Health Connect, mock location or screen recording permission
  just to **view** the Wi-Fi control settings. Each sensitive action has its
  own explicit permission gate.
- Reject unpaired requests, rate-limit PIN guessing, avoid printing PIN/token
  in logs, and do not advertise on public networks.
- Production hardening: move from plaintext LAN HTTP to a secure authenticated
  transport or clearly limit the capability to trusted private Wi-Fi UAT.

## Acceptance tests to perform on connected phones

- Pair/deny/revoke/timeout/wrong PIN and active network change.
- PC/mobile layouts and real/virtual location state parity.
- Create four points; show 2 → 3 correctly; plan only, then explicit start.
- Pause/resume/stop; no movement when disconnected; no duplicate steps.
- Verify unsupported Android OS permission cases and accessibility.
