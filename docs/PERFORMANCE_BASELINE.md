# Cold-start baseline collection (#160)

The offline helper does not connect to Android, start or stop applications, install packages, or access device data. It reads explicitly collected `am start -W` text captures and emits only timing values and supplied build metadata. Use an approved test session and an isolated, non-personal test device or profile; do not interrupt a user's running app or background work.

For a comparison, keep the APK source SHA, ABI, device/OS, power and thermal conditions, map provider, network condition, test data, and launch procedure the same. Record device model/OS, test conditions, app version and APK hash separately in the private evidence. Collect at least 20 independently verified cold launches per variant, each in its own UTF-8 file. Do not relabel warm/hot or unknown launch state as cold. Capture filenames and raw Android output are not included in the JSON. Use only synthetic input for script tests.

Example (replace the placeholder SHA and explicitly list the individual capture files):

```sh
python scripts/cold_start_baseline.py --package com.naos.pikminbee \
  --source-sha YOUR_40_CHARACTER_SOURCE_SHA --variant baseline \
  --environment device --abi arm64-v8a sample01.txt sample02.txt
```

Repeat for the optimized build with `--variant optimized`. Use `--environment emulator` or `synthetic` when appropriate. The helper validates capture status, launch state, package and TotalTime, not the provenance of the APK or actual device; supplied metadata must be checked independently. Reports with fewer than 20 samples explicitly flag the shortfall; duplicate file paths are rejected. P95 uses nearest rank, median uses the conventional midpoint for even counts.

`TotalTime` is the Android activity-launch timing, not time until map interaction, network tiles loaded, or all content displayed. Record those separately with controlled interaction and profiling. Do not infer frame pacing, Compose recomposition, CPU/RAM or energy from this metric. Keep issue acceptance open until the actual controlled measurements and device regression evidence are available.
