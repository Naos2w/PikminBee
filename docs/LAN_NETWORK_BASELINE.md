# LAN polling/network baseline (#160)

This offline helper does not contact any server, issue requests, pair a device, or change polling. Use a separately authorised isolated app/browser test session and synthetic data, with existing LAN pairing and permissions intact. Capture through a permitted profiler, then normalize and redact the data before analysis. Do not store or export URLs, request/response bodies, headers, addresses, cookies, credentials, tokens, PINs, real coordinates or user data.

The exact CSV header is `request_id,kind,start_ms,end_ms,outcome,status,request_body_bytes,response_body_bytes`. Assign each observed request a unique nonnegative numeric ID, `poll` or `other` (using the same documented classification on both builds), monotonic offsets in milliseconds relative to capture start, and outcome `http`, `timeout`, `transport_error`, `cancelled` or `incomplete`. HTTP requires a final status 200–599; other outcomes leave it blank. Include all requests initiated within the declared window, including failed/cancelled requests and requests still pending at its end (`incomplete`, end_ms equal to duration). Exclude requests already underway at the start; record their exclusion separately. Body bytes are observed application payload bytes (not headers, TCP, TLS or wire bytes); blank means unknown. Do not infer bytes from content length or assume no response bytes on timeout. Document whether body sizes are encoded or decoded; keep the convention identical across captures.

```sh
python scripts/lan_network_baseline.py capture.csv --duration-ms 300000 \
  --source-sha YOUR_FULL_SOURCE_SHA --apk-sha256 YOUR_APK_SHA256 \
  --abi arm64-v8a --variant baseline --environment synthetic --scenario idle
```

Hold device/OS, browser, network, workload, foreground state, capture instrumentation, cache, permissions, APK source and ABI constant. Use separate idle and foreground windows of at least five minutes; repeat and record collection conditions. Request rate uses the entire declared window, not the span between requests. Gap quantiles concern observed request start times, not timer configuration; overlapping requests are valid. Latency quantiles are restricted to completed HTTP 2xx responses; failures are reported separately. Byte sums cover known observed body bytes only and are not network bandwidth. An empty capture reports zero observed requests, not proof that networking was idle. Duration and APK metadata are declared, not independently verified; incomplete capture or omitted requests can bias results. JSON contains only aggregate statistics and declared build metadata. No raw rows, request IDs or secrets are emitted. This tool cannot establish physical-device acceptance or runtime performance improvement without actual controlled samples.

## Local Web idle-poll pilot (synthetic)

From the repository root on Windows, using Node 22+ and the existing Chrome installation:

```powershell
$env:PIKMINBEE_POLL_SAMPLE_SECONDS='300'
node scripts/test_lan_theme_favourites.mjs
Remove-Item Env:PIKMINBEE_POLL_SAMPLE_SECONDS
```

The optional 10–300 second capture runs the packaged Web UI against the loopback-only synthetic preview in a fresh temporary headless browser profile. It does not use an Android device. It reports `POLL_BASELINE` JSON containing completed same-origin `/api/state` Resource Timing entries, relative timing, HTTP status and decoded body/transfer sizes, together with separate attempt outcomes. P50 and P95 are nearest rank; `transferSize` is the browser's reported transfer size, not physical wire bytes. Resource Timing still counts only completed transfers. Separately, a temporary test-page wrapper counts every controller `fetch('/api/state')` invocation initiated in the window, distinguishes HTTP status, transport rejection, cancellation and pending fetch, and records `Response.json()` success, rejection or pending read. The JSON timings include body read and decoding, not isolated CPU parsing. Requests already underway at capture start and other endpoints are excluded. The wrapper is restored at the end; no production assets, polling settings or permissions change. Only numeric timings and outcome labels are emitted, never bodies or credentials. Use the full capture specification above for all-endpoint or physical LAN traffic. Record source SHA, browser version and host/test conditions alongside the report. This optional network-only run does not establish map rendering or UI acceptance; run the default visual smoke separately with the environment variable unset.
