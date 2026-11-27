# PikminBee — Route favourites and GPX (source prototype)

## Using the Android app

1. Open **路徑** for ordinary walking stops or **尋花** for manually marked flower stops.
2. Tap the map to add ordered stops (maximum 15 per draft).
3. Tap **收藏路線／GPX**, enter a name, then **收藏這條路線**. Up to 100 named route favourites are saved locally.
4. From the saved list, **載入** restores the stops to the selected draft without starting GPS movement. **改名** changes only the route label. **刪除** asks for explicit confirmation and never deletes the current draft.
5. **匯入 GPX** opens Android's Storage Access Framework document picker; no broad storage permissions are required. A valid GPX will become a newly named saved route and load into the corresponding map mode.
6. **GPX** beside a saved route opens Android's Create Document picker; choose your destination folder to export it.

## GPX compatibility and safety

- GPX **1.1** with ordered `<rtept>` route points is supported; `<trk><trkseg><trkpt>` from one continuous segment is also supported. Standalone `<wpt>` points work when there is no route/track. Import creates one saved route only. Multiple routes/tracks or a mixture of routes, tracks and standalone waypoints are rejected before saving, rather than silently selecting the first object. Split such files in a GPX editor before import. GPX is a route interchange format, not a complete backup of favourites, metadata or app settings.
- Exports are standards-based GPX 1.1 `<rte>` documents. PikminBee's own `<type>` marker distinguishes ROUTE from FLOWERS; for other people's GPX the current editor mode is used.
- **A GPX track is not identical to a freshly calculated pedestrian route.** Track imports with more than 15 points are simplified to up to 15 significant bends, preserving the start and end. The app shows the original and reduced counts and requires confirmation **before** saving. This never silently switches to straight-line GPS movement: future walking requires a new Valhalla pedestrian road query.
- Up to **2 MB / 5,000 GPX track points** can be read; routes with more than 15 explicit `<rtept>` points are rejected rather than silently truncated. GPX with disconnected track segments, invalid WGS84 coordinates, malformed XML or external/DOCTYPE entities is rejected with an error.
- Read and write use Android's document chooser and a one-time document URI. No storage, cloud, location or network data is uploaded by the GPX import/export feature.
- Named favourites stay inside the app's SharedPreferences; this source prototype has not yet migrated them to Room. A separate GPX export is recommended before uninstalling the app or clearing its data.

## UAT checklist

- [ ] Save, load, rename and delete road and flower routes, including app restart.
- [ ] Import external GPX 1.1 with `rtept` and `trkpt` on POCO/HyperOS.
- [ ] Verify a long track prompts for confirmation and cancelling leaves drafts/saved routes unchanged.
- [ ] Export GPX to local files and verify another GPX reader can open it.
- [ ] Verify invalid XML, oversized tracks, denied storage provider requests and invalid coordinates show an error.
- [ ] Confirm loading/importing a route never starts walking or forcibly restores the real GPS.

Build guidance: use local `testDebugUnitTest` and `lintDebug` during development. Make **one manually requested APK** when the UAT batch is ready; keep Draft PR GitHub Actions skipped until review.
