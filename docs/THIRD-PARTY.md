# Third-party code & asset inventory (PikminBee)

## Locally bundled web-map library

PikminBee's private Wi-Fi browser controller serves a **fixed, reviewed local copy** of MapLibre GL JS rather than executing JavaScript downloaded from a CDN each time an approved remote browser connects.

| Field | Value |
| --- | --- |
| Package | `maplibre-gl` |
| Version | **6.18.0** |
| Distribution | npm public release, `https://unpkg.com/maplibre-gl@6.18.0/` |
| Licence | **BSD-3-Clause** |
| License notice | `app/src/main/assets/lan/vendor/maplibre-gl/LICENSE.txt` |
| JS module | `app/src/main/assets/lan/vendor/maplibre-gl/maplibre-gl.mjs` |
| CSS | `app/src/main/assets/lan/vendor/maplibre-gl/maplibre-gl.css` |
| JS SHA-256 | `1e0a68844041aec6621a10d4725c9970c3b19cf7e1605d98b01dbc993e1058b2` |
| CSS SHA-256 | `a90798a1953017790e590f7e1bd35ff103a8a4263972cb47290b78adf86f8c20` |

The original license notice is retained without changes. Unit tests verify the exact vendored JS/CSS hashes. The repository's `.gitattributes` marks these vendor assets `-text` to prevent Windows/Unix newline conversion from changing their bytes. Updating MapLibre requires updating the bundled assets, licence, tests, and review.

MapLibre JS is code only. The map still uses **OpenFreeMap** network map resources with **OpenStreetMap** attribution. Map data licensing and third-party map style hosting policies remain separate from the MapLibre GL JS licence.

For provenance: https://www.npmjs.com/package/maplibre-gl and https://maplibre.org/maplibre-gl-js/docs/

## Scope of this dependency

- Only the browser UI `/vendor/maplibre-gl.mjs` and `/vendor/maplibre-gl.css` are served by the Android LAN listener.
- The browser receives executable code only from its **same-origin local server**; the HTTP Content-Security-Policy disallows CDN script execution.
- Public/private Wi-Fi **HTTP transport remains unencrypted**; local bundling does not fix lack of TLS or protect against malicious on-path network users.
- This library is unrelated to the Android MapLibre native SDK. Check the native SDK's licence and transitives separately at release gate.
- Any later library upgrade requires rechecking the published version and its release licence; do not silently fetch `latest` at runtime.

## Commercial provider audit — #173 (2026-11-27)

**Status: source and published-policy audit only; G5 remains open.** Local worktree base `d8170aa3e4ae89d3efe50aad5be97900f32edb5a`; source inspection also used local #172 head `3a4d387a1e0218bccd0d6f237f3cccd17f711bdf`. Remote develop `f0eb7ce03fdfd8d5454d4fbdc1758bcf53c1b2d6` is not present locally, so recheck against the eventual integrated commit. No live requests containing user coordinates or credentials were made for this audit. Provider pages checked on 2026-11-27; commercial use of map data, code licences, and a hosted service's contractual permission/capacity are separate questions.

| Actual provider / source | Published terms and release decision |
| --- | --- |
| OpenFreeMap — `MapBasemapPolicy.kt`, `LanHttpPolicy.kt`; native and browser map tiles/style resources | [Provider homepage](https://openfreemap.org/) permits commercial map display, with no published map-view/request cap or charge for its public instance. No SLA; [terms updated 2026-11-24](https://openfreemap.org/tos/) reserve discontinuation and restrict automated collection. Offline download limits: 3,000 tiles AND 50 MB per user-started download, no more than four concurrent requests and an identifying User-Agent; no bulk offline feature is approved here. [Privacy policy](https://openfreemap.org/privacy/) updated 2026-10-08: default access logs omit IP; error/security logging may retain it (7 days for errors, up to 30 days for incidents). CDN processing may occur. Candidate for online map display, contingent on actual attribution, data-flow disclosure, owner review and failure handling; not an SLA. |
| OSM data / OpenMapTiles | [OSM copyright and licence](https://www.openstreetmap.org/copyright): ODbL data obligations and visible attribution; public OSM data does not grant unrestricted use of every third-party server. Preserve OpenMapTiles credit required by the tile provider. Verify attribution visible/accessible on native and browser maps in all viewport/theme states; don't infer compliance from the presence of a MapLibre library. |
| FOSSGIS Valhalla demo — `ValhallaPlanner.kt`, `valhalla1.openstreetmap.de/route` and legacy `/optimized_route` reference | [Valhalla official docs](https://valhalla.github.io/valhalla/start/introduction/) require fair use and ask publishers to notify maintainers in GitHub Discussions and send an identifying X-Client-Id header. [Related FOSSGIS OSRM policy](https://routing.openstreetmap.de/about.html) states one request/sec maximum and no heavy use; do not interpret this as a guaranteed Valhalla allocation. Actual source throttles process-local calls to one per two seconds, caches up to eight road plans for one hour, bounds successful responses at 3 MB, sets a User-Agent but no X-Client-Id. Error-body reading is not bounded before truncation; explicit 429/backoff and fleet-level capacity are not established. Ordered coordinates are sent to the provider. Obtain approval/capacity and retention details or use an approved replacement/omit network routing from public product; no endpoint expansion or production traffic now. Legacy flower-specific reference requires reachability/retirement review under #169/#171, not optimisation. |
| Photon — `PlaceLookup.kt`, `photon.komoot.io/reverse` | [Upstream](https://github.com/komoot/photon) Apache-2.0 software, but demo is best effort, may throttle/block high traffic and change without warning; no published numerical allowance. Actual requests send selected coordinates to six decimal places; source caches successful names for 30 days and limits response size (128 KiB). No global request budget or published capacity commitment. Require a provider agreement/capacity decision or retain manual naming without lookup; do not stress-test the public server. |
| TimeAPI — `PlaceLookup.kt`, `timeapi.io/api/timezone/coordinate` | [FAQ](https://timeapi.io/home/faqs) says free currently, potential paid/SLA plans later. [Privacy notice](https://www.timeapi.io/Home/Privacy) describes website processing but does not establish coordinate API retention, a commercial allocation or guaranteed availability. Actual coordinate query to six decimals, successful zone cache 30 days. The #172 local privacy patch removes a separate physical-location timezone query, not all saved-place lookups. Require verified API terms/retention/capacity, or use manual/unknown zone or a separately audited local solution. Do not confuse this provider with timezone.io. |
| Optional Google Maps Android SDK — `app/build.gradle` `play-services-maps:20.0.0` | [Android usage/billing](https://developers.google.com/maps/documentation/android-sdk/usage-and-billing) requires a billing-enabled project and authenticated key; [pricing table updated 2026-11-11](https://developers.google.com/maps/billing-and-pricing/pricing) lists the Maps SDK SKU with unlimited no-cost usage, not all Maps APIs. Verify actual enabled SKU, application/signing-certificate restrictions, attribution, SDK data collection, key restrictions, and owner-authorised billing controls before use; no account/key/billing changes in this audit. |

### Direct dependency and asset baseline (not a resolved transitives inventory)

`app/build.gradle`: Android MapLibre 13.7.1; Google Maps SDK 20.0.0; Compose BOM 2025.10.00 (UI, foundation, Material3, tooling preview); AndroidX Activity Compose 1.11.0, Lifecycle Runtime/Compose 2.9.4, Core KTX 1.17.0; Kotlin Coroutines Android 1.10.2. JUnit 4.13.2 and org.json 20250517 are test dependencies; Compose tooling is debug-only. No direct AdMob or Health Connect dependency in this base. Local web MapLibre version/hash/notice above. `app/src/main/assets/lan/flags/NOTICE.md` records svg-country-flags 1.2.10, 252 bundled PNGs, public-domain upstream claim; national flag usage law and exact asset rights remain separate. #180 new brand assets remain subject to owner review; no claim of trademark clearance. Need release runtime dependency resolution, native libraries/ABIs, bundled notices/asset-font inventory and final binary verification before G5 closure.

### Resolved release runtime dependency evidence (2026-11-27)

Local source `4f7c406` (base `d8170aa`), Gradle 9.3.1, Android Studio bundled JDK, offline `gradlew.bat --offline --console=plain :app:dependencies --configuration releaseRuntimeClasspath`: BUILD SUCCESSFUL in 25s, no FAILED entries. Deduplicated selected group:artifact:version from expanded non-constraint dependency rows (version substitutions applied): **111 module coordinates** including platforms and multiplatform metadata, not 111 packaged binaries. All 111 had one cached POM; licence names below are observed direct POM declarations (normalised Apache labels), not an independent licence clearance. No Health Connect or Google Ads module was observed in this configuration. No build, sign, package, install, or external provider traffic was performed.

**Apache 2.0 (POM declaration) — 104**

```text
androidx.activity:activity-compose:1.11.0
androidx.activity:activity-ktx:1.11.0
androidx.activity:activity:1.11.0
androidx.annotation:annotation-experimental:1.4.1
androidx.annotation:annotation-jvm:1.9.1
androidx.annotation:annotation:1.9.1
androidx.arch.core:core-common:2.2.0
androidx.arch.core:core-runtime:2.2.0
androidx.autofill:autofill:1.0.0
androidx.collection:collection-jvm:1.5.0
androidx.collection:collection-ktx:1.5.0
androidx.collection:collection:1.5.0
androidx.compose.animation:animation-android:1.9.3
androidx.compose.animation:animation-core-android:1.9.3
androidx.compose.animation:animation-core:1.9.3
androidx.compose.animation:animation:1.9.3
androidx.compose.foundation:foundation-android:1.9.3
androidx.compose.foundation:foundation-layout-android:1.9.3
androidx.compose.foundation:foundation-layout:1.9.3
androidx.compose.foundation:foundation:1.9.3
androidx.compose.material3:material3-android:1.4.0
androidx.compose.material3:material3:1.4.0
androidx.compose.material:material-ripple-android:1.9.3
androidx.compose.material:material-ripple:1.9.3
androidx.compose.runtime:runtime-android:1.9.3
androidx.compose.runtime:runtime-annotation-android:1.9.3
androidx.compose.runtime:runtime-annotation:1.9.3
androidx.compose.runtime:runtime-saveable-android:1.9.3
androidx.compose.runtime:runtime-saveable:1.9.3
androidx.compose.runtime:runtime:1.9.3
androidx.compose.ui:ui-android:1.9.3
androidx.compose.ui:ui-geometry-android:1.9.3
androidx.compose.ui:ui-geometry:1.9.3
androidx.compose.ui:ui-graphics-android:1.9.3
androidx.compose.ui:ui-graphics:1.9.3
androidx.compose.ui:ui-text-android:1.9.3
androidx.compose.ui:ui-text:1.9.3
androidx.compose.ui:ui-tooling-preview-android:1.9.3
androidx.compose.ui:ui-tooling-preview:1.9.3
androidx.compose.ui:ui-unit-android:1.9.3
androidx.compose.ui:ui-unit:1.9.3
androidx.compose.ui:ui-util-android:1.9.3
androidx.compose.ui:ui-util:1.9.3
androidx.compose.ui:ui:1.9.3
androidx.compose:compose-bom:2025.10.00
androidx.concurrent:concurrent-futures:1.1.0
androidx.core:core-ktx:1.17.0
androidx.core:core-viewtree:1.0.0
androidx.core:core:1.17.0
androidx.customview:customview-poolingcontainer:1.0.0
androidx.customview:customview:1.0.0
androidx.emoji2:emoji2:1.4.0
androidx.fragment:fragment:1.8.9
androidx.graphics:graphics-path:1.0.1
androidx.interpolator:interpolator:1.0.0
androidx.lifecycle:lifecycle-common-java8:2.9.4
androidx.lifecycle:lifecycle-common-jvm:2.9.4
androidx.lifecycle:lifecycle-common:2.9.4
androidx.lifecycle:lifecycle-livedata-core-ktx:2.9.4
androidx.lifecycle:lifecycle-livedata-core:2.9.4
androidx.lifecycle:lifecycle-livedata:2.9.4
androidx.lifecycle:lifecycle-process:2.9.4
androidx.lifecycle:lifecycle-runtime-android:2.9.4
androidx.lifecycle:lifecycle-runtime-compose-android:2.9.4
androidx.lifecycle:lifecycle-runtime-compose:2.9.4
androidx.lifecycle:lifecycle-runtime-ktx-android:2.9.4
androidx.lifecycle:lifecycle-runtime-ktx:2.9.4
androidx.lifecycle:lifecycle-runtime:2.9.4
androidx.lifecycle:lifecycle-viewmodel-android:2.9.4
androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4
androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.9.4
androidx.lifecycle:lifecycle-viewmodel-savedstate:2.9.4
androidx.lifecycle:lifecycle-viewmodel:2.9.4
androidx.loader:loader:1.0.0
androidx.profileinstaller:profileinstaller:1.4.0
androidx.savedstate:savedstate-android:1.3.3
androidx.savedstate:savedstate-compose-android:1.3.3
androidx.savedstate:savedstate-compose:1.3.3
androidx.savedstate:savedstate-ktx:1.3.3
androidx.savedstate:savedstate:1.3.3
androidx.startup:startup-runtime:1.1.1
androidx.tracing:tracing:1.2.0
androidx.versionedparcelable:versionedparcelable:1.1.1
androidx.viewpager:viewpager:1.0.0
com.google.code.gson:gson:2.10.1
com.jakewharton.timber:timber:5.0.1
com.squareup.okhttp3:okhttp:4.12.0
com.squareup.okio:okio-jvm:3.6.0
com.squareup.okio:okio:3.6.0
org.jetbrains.kotlin:kotlin-stdlib-common:2.2.10
org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.10
org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.10
org.jetbrains.kotlin:kotlin-stdlib:2.2.10
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2
org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.10.2
org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2
org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2
org.jetbrains.kotlinx:kotlinx-serialization-bom:1.7.3
org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3
org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3
org.jetbrains:annotations:23.0.0
org.jspecify:jspecify:1.0.0
org.maplibre.gl:android-sdk-geojson:6.0.1
org.maplibre.gl:android-sdk-turf:6.0.1
```

**Android Software Development Kit License (POM declaration; separate Google service terms) — 4**

```text
com.google.android.gms:play-services-base:18.5.0
com.google.android.gms:play-services-basement:18.9.0
com.google.android.gms:play-services-maps:20.0.0
com.google.android.gms:play-services-tasks:18.2.0
```

**BSD (POM label only; exact licence and bundled native notices not yet verified) — 2**

```text
org.maplibre.gl:android-sdk:13.7.1
org.maplibre.gl:maplibre-android-gestures:0.0.4
```

**Missing direct POM licence declaration; review upstream and bundled notice — 1**

```text
com.google.guava:listenablefuture:1.0
```

Cached AAR inspection (ZIP entry names, not final APK content) found these JNI libraries for arm64-v8a, armeabi-v7a, x86 and x86_64: `androidx.graphics:graphics-path:1.0.1` → `libandroidx.graphics.path.so`; `org.maplibre.gl:android-sdk:13.7.1` → `libmaplibre.so`. Neither native library's embedded dependency licences nor ELF alignment have been validated. Google Play services Maps, Base, Basement and Tasks are **not** classified as Apache 2.0 merely because AndroidX is. The Google SDK licence and Maps terms, MapLibre native bundled notices, and Guava's exact upstream/JAR notice must be examined. Reconcile embedded/transitive licences, assets/fonts, rendered attribution, merged manifest, final package contents and ABI/16 KB checks (#177) against the integrated develop SHA before closing G5. This snapshot is not an SBOM or final legal sign-off; no runtime dependency was changed.

### Capacity model for decision, not observed traffic

Assume 100 / 1,000 / 10,000 daily active users, 30 days, two uncached route queries per active user/day, two uncached place saves/day (one Photon + one TimeAPI request each), and two map sessions/day. These are stress-planning assumptions before measuring real adoption/cache hit rate, not permission to send requests.

| Monthly units | Low | Medium | High |
| --- | ---: | ---: | ---: |
| Route provider requests | 6,000 | 60,000 | 600,000 |
| Photon requests | 6,000 | 60,000 | 600,000 |
| TimeAPI requests | 6,000 | 60,000 | 600,000 |
| Map sessions (not tile requests or billable loads) | 6,000 | 60,000 | 600,000 |

For any approved metered replacement, model each service separately as fixed monthly charge + billable calls (after included quota) multiplied by the applicable tier unit price, plus infrastructure/storage/egress and tax. Actual tile requests per session and peak concurrency need measurements on authorised fixtures. Current OpenFreeMap public online display price and Google's Maps SDK-only SKU price are zero under current published conditions; they do not make the total product cost zero. Route, geocoding and timezone commercial capacity/costs remain unknown until provider approval/quotations; don't promise $0/month or treat local throttling as fleet-wide compliance.

### Acceptance and release boundary

- Keep offline/manual map/GPX workflows usable when external providers are unavailable; surface explicit failures, never fake successful lookups, and do not silently switch to a different data processor.
- For an approved provider, validate rate/budget controls, 429/Retry-After, timeout, malformed and oversized responses, cache expiry, concurrent requests, offline fallback and data minimisation with local synthetic fixtures. No load tests against public demos. Budget thresholds proposed for later owner-approved implementation: warn at 70% and 90% of a verified allowance and stop optional paid calls at the approved cap; no paid subscription or new backend is authorised.
- Record current licence evidence, actual app attribution, privacy disclosures and measured request volume against the integrated source and release artifact. #173 remains In Progress; this is a documented partial audit, not an operational quota/cost approval, completed legal review, or release permission.
