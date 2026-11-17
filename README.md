# PikminBee

PikminBee is a free, ad-free Android GPS testing app: a bee exploring the world, visiting flowers and collecting nectar. Built with Kotlin and Jetpack Compose.

**Project status:** v0.2 development branch. POCO device UAT is still pending; not a production-ready release.

- Maps: OpenFreeMap Liberty/Dark via MapLibre; optional official Google Maps SDK (requires your own API key).
- Interface: iOS-27-inspired frosted controls with accessible contrasts and a clear light/dark theme.
- Location: explicit mock-location test mode on the owner's device (not a bypass for application integrity or location checks).
- v0.2 development: map-picked walking stops, rate-limited Valhalla pedestrian routing, walking / pause / stop / clear, manually marked flower loops (not automatic game flower discovery). All require device UAT.
- v0.3 development: opt-in, draggable bee floating controls connected to the same foreground GPS service (Android overlay permission and POCO UAT required).
- Wi-Fi browser control: later milestone. See [TODO](docs/TODO.md).
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
