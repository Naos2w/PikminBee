# PikminBee · Wi-Fi 控制設計與安全驗收

Status: **same-private-LAN browser control implemented in DEV source, not yet verified on POCO**. VPN/Tailscale and custom-DNS support remain future options, not required for the basic workflow.

## Confirmed PC-to-Android control scope (21 Nov 2026)

The requirement is **network reachability, not the same Wi-Fi SSID**. Whenever a
computer has an authorised, routable connection to the Android phone, its
browser should be able to operate PikminBee's existing features. This is a
browser-based functional controller, **not** a screen-mirroring tool or a
second GPS engine.

Target connections: same LAN, routed private subnets/VLANs, or an approved
private VPN/tailnet (e.g. Tailscale or WireGuard) even when PC and phone are
on separate physical networks. A DNS name or MagicDNS is just an address:
it does not establish connectivity on its own.

### Network limitations still applicable

- LanWifiAddress discovers only TRANSPORT_WIFI and selects a private IPv4
  listener. It has no verified VPN-interface selection.
- LanHttpServer checks both local and peer IP with isSiteLocalAddress.
  Tailscale's usual 100.64.0.0/10 addresses are therefore not accepted.
- LanHttpPolicy requires the exact bound IP:port in Host and POST Origin,
  so a MagicDNS or private custom domain does not currently work.
- `/api/route/preview` now builds a bounded pedestrian road plan without GPS movement.
  `/api/route/start` refuses to execute until an unmodified preview ID has been
  explicitly confirmed, including an extra acknowledgment for large road snaps.
- Current HTTP is plaintext: never forward port 8765 to the public internet.

### Implementation priorities

1. Keep the phone as the sole executor; PC browser tabs must mirror
   瞬移 / 路徑 / 尋花 / 收藏, including step count and segment 2 -> 3.
2. Allow explicitly approved routable private LAN endpoints, irrespective
   of Wi-Fi name. Keep a user-controlled off switch, six-digit PIN, phone
   approval and time-limited/revocable sessions.
3. Add a verified VPN network path for Tailscale/WireGuard. Do not trust
   every 100.x address blindly; confirm an authenticated VPN interface and
   access controls. Test Android inbound socket support on actual hardware.
4. Support private hostnames/MagicDNS only after strict Host/Origin validation
   and safe transport decisions; never loosen header checks for arbitrary
   public domains or enable a wildcard public listener.
5. If Android VPN inbound access is unavailable, use a phone-initiated
   authenticated outbound connection to a PC-hosted relay on that same VPN.
6. Opening the browser dashboard must not activate GPS or start a route.
   Require preview then an explicit Start command, with high-snap warnings.

### Network acceptance matrix

- Same private Wi-Fi: browser connects by phone's reachable private IP.
- Different Wi-Fi/VLAN with routed RFC1918 connectivity: browser control
  works when network/firewall policies allow it; verify PIN and approval.
- Different physical networks joined by a private VPN/tailnet: supported
  after interface/network checks and authentication are implemented.
- Optional MagicDNS name: works only with validated hostname/Origin support.
- Network loss/permission revocation: disconnect and revoke remote control,
  without automatically stopping the phone's already-active route.
- Untrusted public endpoints: denied; no publicly accessible control port.


## Goal and scope

Use a PC or second phone's browser to control the existing Android PikminBee
instance over any trusted, routable private network or approved VPN, without
requiring the same Wi-Fi network. No public control endpoint and no second GPS engine.

## Current code verified

- `LanController` exposes an HTTP service at the current private Wi-Fi IPv4
  address, port 8765, only after a manual enable action in the settings.
- A randomly rotated six-digit PIN creates a **pending** pairing; the phone
  owner explicitly approves or denies the request. A short-lived browser
  session is bound to the connecting client address; the controller closes on
  Wi-Fi change/disconnection and revokes sessions when turned off.
- A user-started `LanControlService` foreground listener can open the browser
  control without starting the mock GPS engine. Actual teleport/start still
  requires system location and mock-app permissions.
- The HTTP transport is **not encrypted**. Treat current LAN control as
  experimental and use only on a trusted private Wi-Fi, never public Wi-Fi.
- `/api/route/preview` is a no-movement road preview; `/api/route/start`
  requires a fresh matching preview ID and separate explicit confirmation.
  High snap offsets require an additional browser acknowledgment.

## Proposed UI/UX

1. Settings → **電腦遠端控制 · 選用**. Off by default; display
   Off / Ready / Pairing request / Connected / Connection lost.
2. Enable only after explaining routed-private-network restrictions. Present the reachable private URL,
   share/copy action and six-digit PIN; optional QR **must not embed the PIN
   in a public link**.
3. A paired PC or other phone opens a responsive browser dashboard:
   **瞬移 / 路徑 / 尋花 / 收藏**, matching Android's bottom navigation.
   Show current segment (e.g. 2 → 3), session-only estimated steps, route
   position and start/pause/resume/stop using the *same* service state.
4. Route editor calls a read-only preview endpoint and shows the snapped
   pedestrian geometry/distance. Starting needs a second explicit action;
   large snap offsets require another visible browser confirmation after
   the browser device was independently approved on the phone.
   Never restart or move GPS on connection, page load or re-pairing.
5. Add active-session revoke, last-connected-device label, short expiry and
   a permanent Kill/Disable button in Android settings. Auto stop LAN on
   private Wi-Fi loss and on explicitly disabling it; leaving Wi-Fi
   control must **not** stop a route unless the user requests stop.
6. Accessibility: keyboard navigation, 48dp-equivalent click targets,
   clear playback status and no icon-only mystery actions.

## Permissions / safeguards

- For Android-16-targeting local browser control, the app declares `INTERNET`,
  network-state access, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, and the normal
  `CHANGE_NETWORK_STATE` prerequisite. A user-initiated connected-device
  foreground service hosts the controller without booting mock GPS.
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

- Pair/deny/revoke/timeout/wrong PIN and active network change. Check the
  foreground control notification and permission behaviour on the real POCO.
- PC/mobile layouts and real/virtual location state parity.
- Create four points; show 2 → 3 correctly; plan only, then explicit start.
- Pause/resume/stop; no movement when disconnected; no duplicate steps.
- Verify unsupported Android OS permission cases and accessibility.
