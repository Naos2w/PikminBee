# PikminBee

PikminBee is a free, ad-free Android location and route testing app built with Kotlin and Jetpack Compose.

**Project status:** v0.4 source development branch; not a production-ready release. PR #120 removed the retired `尋花` mode, flower vision and MediaProjection service; PR #122 refined walk controls and route UI. Both are merged into `develop`. Existing flower records, legacy route metadata and GPX compatibility remain readable/exportable. Two-hour Wi-Fi sessions and explicit phone-approved renewal are tracked in PR #121 (source-only until POCO/Edge acceptance). **None of these new changes have been installed in the POCO UAT APK.**

- Maps: OpenFreeMap Liberty/Dark via MapLibre; optional official Google Maps SDK (requires your own API key).
- Interface: iOS-27-inspired frosted controls with accessible contrasts and a clear light/dark theme.
- Location: explicit mock-location test mode on the owner's device (not a bypass for application integrity or location checks).
- Route development: map-selected pedestrian routes via Valhalla, preview before starting, pause/stop/clear, named favourites and GPX 1.1 import/export. The former flower recognition and dedicated flower route mode are retired; old saved flower-labelled routes can be loaded as normal route drafts and old flower records can be exported without deletion. Historical details: [flower vision](docs/FLOWER-VISION.md), [flower catalogue](docs/FLOWER-CATALOG.md), [route library](docs/ROUTE-LIBRARY.md). Device UAT remains pending.
- v0.3 development: opt-in draggable bee floating controls and foreground notification actions. The last simulated location is checkpointed at most every 5 seconds; an Android sticky service restart may restore it as **stationary only**, without automatically resuming the route or the Wi-Fi listener. A phone settings/help panel offers manual recovery and HyperOS guidance. [Background/recovery guide](docs/BACKGROUND-LIFECYCLE.md); POCO UAT required.
- v0.4 source prototype: manual private-Wi-Fi browser controller with six-digit PIN, explicit on-phone device approval, two-hour non-sliding bearer sessions (source in PR #121, not yet a released UAT), explicit on-phone renewal with token rotation, OpenFreeMap web map, teleport/route controls and 2-second state sync. The independent last verified physical-GPS snapshot still expires within 30 minutes. The browser's MapLibre GL JS/CSS assets are **bundled locally** with a fixed version, licence and SHA-256 tests (no CDN-executed JS). **LAN HTTP is still unencrypted: trusted private networks only; device UAT pending.** See [LAN control instructions](docs/LAN-CONTROL.md), [third-party inventory](docs/THIRD-PARTY.md), [flower data source assessment](docs/FLOWER-SOURCE-EVALUATION.md) and [TODO](docs/TODO.md).
- No root or accessibility service needed for the basic map/teleport workflow.

## Setup

1. Open the project with Android Studio on Windows.
2. Install the Android SDK and let Gradle sync.
3. During development run `./gradlew :app:testDebugUnitTest :app:lintDebug --offline` (or just `:app:compileDebugKotlin` for a quick compile).
4. Only when preparing device UAT, manually run `./gradlew :app:assembleDebug` or dispatch GitHub Actions with `package_apk=true`. Debug APK packaging is not part of routine PR verification.
5. OpenFreeMap needs no key.
6. To enable Google Maps, create an untracked `secrets.properties` containing `GOOGLE_MAPS_API_KEY=...` with a properly Android-restricted API key and billing project.

Only set PikminBee as your device's mock location application when intentionally testing on your own device. Stop testing to restore the real location provider.

## Branches

`feature/*` / `fix/*` → **squash** → `develop` (DEV)

`develop` → **merge commit** → `staging` (UAT)

`staging` → **merge commit** → `main` (PROD)

See [branch policy](docs/BRANCHING.md). Repository: https://github.com/Naos2w/PikminBee.
