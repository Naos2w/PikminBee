# NaosGPS

A new, independently maintained, free and ad-free Android GPS testing application written in Kotlin and Jetpack Compose.

**Project status:** initial foundation, not a production-ready release.

- Maps: OpenFreeMap Liberty/Dark via MapLibre; optional official Google Maps SDK (requires your own API key).
- Interface: iOS-27-inspired frosted controls with accessible contrasts and a clear light/dark theme.
- Location: explicit mock-location test mode on the owner's device (not a bypass for application integrity or location checks).
- Road routes, Flower Search, overlays, Wi-Fi browser control: planned milestones. See [TODO](docs/TODO.md).
- No root or accessibility service needed for the basic map/teleport workflow.

## Setup

1. Open the project with Android Studio on Windows.
2. Install the Android SDK and let Gradle sync.
3. Build with `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
4. OpenFreeMap needs no key.
5. To enable Google Maps, create an untracked `secrets.properties` file containing `GOOGLE_MAPS_API_KEY=...` and configure an Android-restricted key for the application's package and signing certificate. A Google Maps Platform billing project is required.

Only set NaosGPS as your device's mock location application when intentionally testing on your own device. Stop testing to restore the real location provider.

## Branches

`feature/*` / `fix/*` → **squash** → `develop` (DEV)

`develop` → **merge commit** → `staging` (UAT)

`staging` → **merge commit** → `main` (PROD)

See [branch policy](docs/BRANCHING.md). Remote GitHub repository is not connected yet.
