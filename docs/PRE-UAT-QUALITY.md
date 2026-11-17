# PikminBee — Pre-UAT quality review (2026-11-17)

**Status:** local source verification complete for `chore/pre-uat-hardening`; Android/POCO device testing is deliberately deferred until UAT. This is **not a release clearance**.

## Checks performed on MSI

| Check | Result | Evidence |
| --- | --- | --- |
| Android source compilation | PASS | `./gradlew :app:testDebugUnitTest :app:lintDebug --offline` |
| JVM unit tests | **48 passing, zero failed** | Unit test XML reports under `app/build/test-results/testDebugUnitTest/` |
| Android Lint | PASS | `app/build/reports/lint-results-debug.html` |
| Browser JS syntax | PASS | `node --check app/src/main/assets/lan/controller.js` |
| Vendored MapLibre module JS syntax | PASS | `node --check app/src/main/assets/lan/vendor/maplibre-gl/maplibre-gl.mjs` |
| Remote executable JS dependency in browser HTML/JS | NONE | MapLibre imported from local `/vendor/maplibre-gl.mjs`; CSS local |
| Vendored JS/CSS integrity | PASS | SHA-256 hashes checked by `LanHttpPolicyTest` |
| Third-party licence | Present | `app/src/main/assets/lan/vendor/maplibre-gl/LICENSE.txt` |
| LAN request policy | PASS in unit tests | Host, Origin, body limit, JSON content type, no CDN scripts in CSP |
| APK packaging / device installation | **NOT RUN** | By user agreement; only UAT will build/install a test APK |
| GitHub Actions usage | Draft PR; no Android runner verification requested | PR intentionally stays Draft |

## Main quality improvement in this PR

Previously the LAN controller's browser dynamically executed MapLibre GL JS from `unpkg.com` in a page holding an authorised control token. Although the version was pinned, the browser still executed remotely loaded third-party code.

Now:
- MapLibre GL JS **6.18.0** and CSS are served from the phone's own LAN listener;
- the original **BSD-3-Clause** licence and reviewed SHA-256 hashes are bundled and checked by local tests;
- the HTTP Content-Security-Policy limits `script-src` to `'self'` and no longer grants an external CDN script origin;
- the vector map **still needs an external tile/style service**; only executable browser code is bundled;
- local LAN **HTTP remains unencrypted**: this change does not make public networks safe, so only trusted private Wi-Fi use is supported.

See `docs/THIRD-PARTY.md` and `docs/LAN-CONTROL.md`.

## Open source and flower-data decision

See `docs/FLOWER-SOURCE-EVALUATION.md`.

- No reliably authorised public **live Big Flower API** was verified. Wayspots and OSM POIs cannot be silently treated as Big Flowers.
- Use **user-recorded** locations or explicitly imported, authorised source data; imported data stays labelled **unverified**.
- A future OSM/Overpass candidate provider would be a **new feature PR**, with user consent, licensing/attribution, rate limits, caching and independent data-quality review.
- Do not scrape private game backends or Facebook logins/cookies.

## Pending UAT acceptance checks (one intentional APK later)

1. **Installation & permissions:** Android 14+/HyperOS, app appearance, precise location permission and developer-option mock-location selection.
2. **Maps:** OpenFreeMap Liberty/Dark, Google Maps with a separately configured Android-restricted key, first render, attribution, map tap, tile failure and provider switch.
3. **GPS controls:** teleport, start/pause/resume/stop/clear, stationary stop vs restore real GPS; avoid rendering invalid or unexpected coordinates.
4. **Routing and flower catalogue:** Valhalla pedestrian route, network failure, map flower markers, personally checked vs unverified candidates, 14-point limit and deduplication.
5. **Local storage:** saved location, saved route, JSON/GeoJSON and GPX import/export (including malformed/oversized files, app restart).
6. **Overlay & lifecycle:** floating controls, notification actions, screen lock, Recents swipe, low-memory restart as PARKED only; no bypass/auto-restart after force-stop.
7. **LAN browser:** phone approval/denial, PIN throttling, local asset serving in desktop/mobile Chrome, private Wi-Fi switching and shutdown, HTTP errors, paired session expiry, no untrusted script execution.
8. **Privacy & licences:** no API keys/keystores committed, MapLibre licence, OSM attribution, public data-source usage restrictions, offline error guidance.
9. **UAT build once only:** manually request **one** debug APK after source review. Do not publish to `main` until UAT passes.

## Remaining release blockers

- POCO UAT and actual permission/navigation/foreground-service lifecycle behaviour are unverified.
- **LAN HTTP is not end-to-end encrypted.** Internet/public-access capability is explicitly unsupported. Future HTTPS/TLS would require a separate design and review.
- Google Maps requires an Android API key and billing/project configuration; web Google Maps integration is intentionally not included.
- Some native MapLibre marker APIs are deprecated; compiling passes but moving to the supported annotation API should be considered in a future focused PR.
- Public community Valhalla routing availability/quotas are not guaranteed. Avoid repeated background retries.
- Room migration and a final release licence/signature audit remain before v1.0.

**Branch policy:** each feature/fix is a Draft PR for review; after predecessor Squash Merge to `develop`, sync and retarget the next PR to `develop`. `develop → staging` and `staging → main` use merge commits. No release build in this PR.
