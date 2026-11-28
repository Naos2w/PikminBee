# Cold-start baseline collection (#160)

The offline helper does not connect to Android, start or stop applications, install packages, or access device data. It reads explicitly collected `am start -W` text captures and emits only timing values and supplied build metadata. Use an approved test session and an isolated, non-personal test device or profile; do not interrupt a user's running app or background work.

For a comparison, keep the APK source SHA, ABI, device/OS, power and thermal conditions, map provider, network condition, test data, and launch procedure the same. Record device model/OS, test conditions, app version and APK hash separately in the private evidence. Collect at least 20 independently verified cold launches per variant, each in its own UTF-8 file. Do not relabel warm/hot or unknown launch state as cold. Capture filenames and raw Android output are not included in the JSON. Use only synthetic input for script tests.

Example (replace the placeholder SHA and explicitly list the individual capture files):

```sh
python scripts/cold_start_baseline.py --package com.naos.pikminbee \
  --source-sha YOUR_40_CHARACTER_SOURCE_SHA --variant baseline \
  --apk-sha256 YOUR_64_CHARACTER_APK_SHA256 \
  --environment device --abi arm64-v8a sample01.txt sample02.txt
```

Repeat for the optimized build with `--variant optimized` and its own independently verified APK SHA-256. The optional `--apk-sha256` is declared metadata, not proof of installation; reports without it remain usable alone but cannot be compared. Use `--environment emulator` or `synthetic` when appropriate. The helper validates capture status, launch state, package and TotalTime, not the provenance of the APK or actual device; supplied metadata must be checked independently. Reports with fewer than 20 samples explicitly flag the shortfall; duplicate resolved paths and repeated file identities (where the filesystem reports a nonzero inode) are rejected; captures must be regular files. Independent files with identical contents are allowed, so collectors must still verify that each capture represents a separate launch. P95 uses nearest rank, median uses the conventional midpoint for even counts.

`TotalTime` is the Android activity-launch timing, not time until map interaction, network tiles loaded, or all content displayed. Record those separately with controlled interaction and profiling. Do not infer frame pacing, Compose recomposition, CPU/RAM or energy from this metric. Keep issue acceptance open until the actual controlled measurements and device regression evidence are available.

## Related offline reports and verification

- [Cold-start comparison](COLD_START_COMPARISON.md): compare independently collected reports for the two APKs.
- [HWUI frame timing](FRAME_TIMING_BASELINE.md): analyse completed Android UI frames; not native map FPS.
- [Process CPU and memory](PROCESS_RESOURCE_BASELINE.md): analyse normalised process samples.
- [LAN request timing](LAN_NETWORK_BASELINE.md): analyse redacted request metadata; not a packet capture.

The `Verify offline performance analysis` CI step runs the five synthetic test suites. This is a software regression gate only. For an actual baseline, retain the collection conditions and APK identity privately and collect each scenario on approved test devices. Do not compare different device conditions merely because the declared source SHA matches. Compose recomposition, native map presentation, build time and device acceptance require their own evidence; no result from these scripts establishes those measurements.
