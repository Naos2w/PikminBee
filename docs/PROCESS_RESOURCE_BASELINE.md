# Process CPU and memory baseline (#160)

The offline helper reads a normalized UTF-8 CSV collected in a separately approved isolated test session. It never calls ADB, reads app data or modifies a device. The exact header is `elapsed_ms,package,pid,start_ticks,cpu_ticks,pss_kib,rss_kib`.

Use one main app process and one continuous monotonic clock per capture. From a permitted profiler or diagnostic collection record monotonic elapsed milliseconds, exact package/process name, PID, process start identity in clock ticks, cumulative user + system CPU ticks for that process, and matching PSS/RSS in KiB. On Linux/Android `/proc/PID/stat` fields 14, 15 and 22 are user ticks, system ticks and start ticks respectively; obtain the actual clock tick frequency from the capture environment, not an assumed constant. Access restrictions or unavailable metrics mean no valid sample; do not bypass them or substitute zero. PSS/RSS must be collected for the same process at the corresponding sample; do not mix process and system totals, unrelated processes or units. Preserve the raw evidence privately. Record capture overhead and timestamp alignment; this helper cannot validate collector correctness.

Use the same device/OS, display, network/cache, synthetic workload, thermal/power conditions, ABI and source SHA for the two APKs. Capture idle and foreground separately, preferably at a fixed documented cadence for at least five minutes each. Choose the maximum gap before collecting (e.g. 2,000 ms for a nominal one-second cadence). Process changes, counter resets, duplicate/reversed times and gaps exceeding the configured maximum invalidate the entire capture, rather than silently stitching intervals. No real user data or private location traces are required.

```sh
python scripts/process_resource_baseline.py capture.csv --package com.naos.pikminbee \
  --source-sha YOUR_FULL_SOURCE_SHA --apk-sha256 YOUR_APK_SHA256 \
  --abi arm64-v8a --variant baseline --environment synthetic --scenario idle \
  --clock-hz 100 --max-gap-ms 2000
```

The frequency in this example is illustrative; substitute the measured value. CPU percent is relative to one logical core (100% = one core busy; values over 100% are valid). The weighted mean uses total CPU-time delta divided by total elapsed time; interval quantiles are unweighted and depend on cadence. Memory quantiles are discrete snapshots, not a time-weighted estimate or continuous peak. PSS and RSS are different measures and should not be combined. Reported window coverage does not establish representativeness or physical-device acceptance. Main process only: this does not measure child processes, GPU, device-wide CPU, power, native map frame rate or battery use. No PID, filenames, raw rows or timestamps are emitted. Declared APK/source metadata and collection conditions require independent verification; synthetic tests are not device measurements.
