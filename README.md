# PikminBee

PikminBee is a free, ad-free Android GPS testing app: a bee exploring the world, visiting flowers and collecting nectar. Built with Kotlin and Jetpack Compose.

**Project status:** v0.4 source development branch. All new LAN/browser controls are unverified on POCO; not a production-ready release.

- Maps: OpenFreeMap Liberty/Dark via MapLibre; optional official Google Maps SDK (requires your own API key).
- Interface: iOS-27-inspired frosted controls with accessible contrasts and a clear light/dark theme.
- Location: explicit mock-location test mode on the owner's device (not a bypass for application integrity or location checks).
- v0.2 development: map-picked walking stops, rate-limited Valhalla pedestrian routing, walking / pause / stop / clear, manually marked flower loops (not automatic game flower discovery). Includes named route favourites and GPX 1.1 import/export via Android's document picker; large imported tracks require user confirmation before simplification. All require device UAT. See [route library guide](docs/ROUTE-LIBRARY.md).
- v0.3 development: opt-in draggable bee floating controls and foreground notification actions. The last simulated location is checkpointed at most every 5 seconds; an Android sticky service restart may restore it as **stationary only**, without automatically resuming the route or the Wi-Fi listener. A phone settings/help panel offers manual recovery and HyperOS guidance. [Background/recovery guide](docs/BACKGROUND-LIFECYCLE.md); POCO UAT required.
- v0.4 source prototype: manual private-Wi-Fi browser controller with six-digit PIN, explicit on-phone device approval, 30-minute sessions, OpenFreeMap web map, teleport/route controls and 2-second state sync. **LAN HTTP is unencrypted: trusted private networks only; device UAT pending.** See [LAN control instructions](docs/LAN-CONTROL.md) and [TODO](docs/TODO.md).
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
