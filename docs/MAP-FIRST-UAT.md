# PikminBee Map-first UAT — POCO checklist

## Main UX (first UAT of the redesigned app)

- **Top of map:** a hamburger icon and a *GPS coordinates only* input. Paste `25.0330, 121.5654` and press ↗/Done to focus it; any address, landmark name, incomplete, or out-of-range coordinate is rejected. This app does not invoke a geocoding search API.
- **Map tap:** updates the chosen coordinate and the top input without teleporting, beginning GPS mock location or automatically adding a waypoint. On **路徑** explicitly press `＋ 加入地圖所選路點`; **尋花** uses its existing explicit add/confirm controls.
- **Current position FAB (◎):** only moves the map camera to the active mock GPS fix or the real phone's available location. It does not teleport or start a route. If permission is missing, the permission guide opens instead.
- **Mode drawer:** hamburger → 瞬移 / 路徑 / 尋花 / 收藏. Bottom sheet shows only that mode's content; collapse it to operate the map. Check normal scrolling in the expanded sheet on POCO, with dark/light themes and large-font settings.
- **Shared preferences:** hamburger → 共用設定 for map provider/style, appearance, *personal default walking speed*, overlay, Wi-Fi control and background tips. Temporary speed changes remain available in 路徑 / 尋花 and from the floating bee.
- **First launch:** sequential permissions guide checks precise location, Android developer-option mock app selection, overlay, notifications and background settings. Required mock GPS settings must be confirmed before starting test location; map-only users can bypass initial onboarding. The latter three are optional; OS system settings remain user controlled. The guide is accessible from the drawer later.
- **Visual:** high-contrast light/dark support and near-black, high-opacity Liquid Glass-inspired chrome. No expensive live backdrop blur over dynamic vector tiles.
- **Persistence and safety:** previously saved waypoints, named routes, GPX and manual flower records remain in their existing data stores within the **same installed package**. Do not use an app uninstall to migrate data.

## Fixed UAT package identity / update behaviour

- First GitHub UAT package: `com.naos.pikminbee.uat`, visibly labelled **PikminBee UAT**. It is **separate from** previous `com.naos.pikminbee` test installations so no old Android signing-certificate conflict occurs. **Existing app data does not migrate automatically across packages**.
- This special APK is built only by explicitly requesting `android.yml` on `staging` with `package_apk=true`, and **requires** stable private GitHub Actions signing secrets `PIKMINBEE_UAT_KEYSTORE_BASE64` and `PIKMINBEE_UAT_SIGN_PASSWORD`. On missing secrets, packaging fails closed (it never silently creates another Android debug key).
- The new visible UAT version sequence starts at **`0.1.1`** (then `0.2.1`, `0.3.1`, etc. for new UAT cycles); DEV uses incrementing patch versions such as `0.1.2-dev`, `0.1.3-dev`. `gradle.properties` and `scripts/bump_version.py` define/advance the numbers. The older distributed APK used the display version `0.3.0-dev-uat`; changing the display version does **not** remove its existing data.
- Package identity, exact semantic version and the fixed cert are verified before creating the versioned GitHub Artifact `PikminBee-UAT-<version>-signed` (for example `PikminBee-UAT-0.16.1-signed`). Each explicitly dispatched GitHub UAT build uses `GITHUB_RUN_NUMBER` to generate a monotonically increasing **Android versionCode greater than the previously shipped 1051**, independent from the visible version name, enabling future signed APK installs *over* the previous UAT build.
- Never commit the UAT keystore/password, never use it to sign production releases, and do not auto-install, release, or promote `staging` to `main`.
- Artifact is a downloadable ZIP from the GitHub Actions run; unzip it on the POCO to access **`PikminBee-UAT-<version>.apk`** (for example `PikminBee-UAT-0.16.1.apk`). The release workflow copies the **already verified signed** `app-debug.apk` byte-for-byte, then uploads only the versioned filename. Existing older Artifacts remain unchanged, and GitHub Artifacts expire after **7 days**.
- To enable mock GPS, update Android developer settings to choose **PikminBee UAT** instead of **PikminBee**. The older app stays installed but Android permits only the chosen mock-location app at a time.
- Real POCO UI/UAT is still required: verify all 4 modes, touch interactions, bottom sheet gestures, permissions, map tile rendering, accurate coordinate selection, speed presets, foreground service, GPX/flower persistence, background behaviour and LAN controls. Keep Issues #16, #17, #18, #26 and #29 open until tested on the actual phone.

## No additional location service

The redesign reuses `MockLocationService`, `ValhallaPlanner`, `RouteDraftState`, `FlowerCatalogStore` and the original Android camera APIs. Changing the map viewport or input coordinates cannot trigger GPS movement without an explicit operation. Google Maps API key remains optional; keyless OpenFreeMap is the default.
