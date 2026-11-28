# HWUI frame-timing baseline (#160)

This offline tool reads one explicitly collected UTF-8 `dumpsys gfxinfo PACKAGE framestats` capture. It never calls ADB or interacts with a device. Use an approved isolated test device/profile and synthetic map data. Keep source SHA, ABI, APK identity, device/OS, display mode, temperature, power, map provider, viewport, gesture procedure and network/cache conditions controlled. Record the actual collection procedure separately. Do not collect while the owner is using the device.

```sh
python scripts/frame_timing_baseline.py --package com.naos.pikminbee \
  --source-sha YOUR_FULL_SOURCE_SHA --apk-sha256 YOUR_APK_SHA256 \
  --abi arm64-v8a --variant baseline --environment device capture.txt
```

The package process header must match exactly. Select one window explicitly with `--section` (zero-based) if there are multiple profile sections. Do not merge different windows, overlapping ring-buffer snapshots or unrelated interactions. Record window selection privately. All nonzero-flag rows are excluded and counted (including first-window frames); unflagged malformed, duplicate or incomplete rows fail closed. A missing deadline column means no deadline metric, not zero late frames. A present invalid deadline rejects the capture. Median and nearest-rank P95 describe only the captured completed unflagged frames; no minimum sample count or representativeness is assumed.

The metric is `FrameCompleted - IntendedVsync`, not actual display-present timing, FPS, Android's official jank metric or the native map surface's rendering time. When available, `FrameCompleted > FrameDeadline` is reported only as a completion-after-deadline proxy, not jank. Use Perfetto/FrameTimeline and controlled map interaction tracing to validate native surfaces, presentation and time to interactive map independently. See [Android CTS framestats format](https://android.googlesource.com/platform/cts/+/master/hostsidetests/dumpsys/src/android/dumpsys/cts/GfxInfoDumpsysTest.java).

JSON includes declared build/environment metadata and aggregate timing only, not raw capture text, PIDs, window names, filenames or timestamps. APK provenance, device conditions, map rendering and acceptance remain unverified. Use `synthetic` or `emulator` when appropriate; never promote these to physical-device results. Test with synthetic fixtures only.
