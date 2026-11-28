# Cold-start comparison (#160)

Run `cold_start_baseline.py` separately for the baseline and optimized builds (see `PERFORMANCE_BASELINE.md`), redirecting their JSON outputs to `baseline.json` and `optimized.json`. Then run:

```sh
python scripts/compare_cold_start_baselines.py baseline.json optimized.json
python -m unittest discover -s scripts -p 'test_compare_cold_start_baselines.py'
```

The comparator requires matching package, source SHA, ABI, environment category, metric and percentile method, plus distinct declared 64-character APK SHA-256 values and at least 20 positive integer samples per build. Reports without APK hashes or with identical hashes are rejected; regenerate the reports using `--apk-sha256`. It recomputes the recorded statistics from raw sample values, rejecting inconsistent reports. Negative delta means a lower measured latency; positive delta means a higher latency. Deltas are descriptive, not a significance test or an acceptance decision.

Independently verify both APK hashes, device/OS, power and thermal conditions, map/network state, sample independence and identical launch procedure before interpreting any change. An environment label is not proof of device provenance. The output explicitly marks provenance, conditions and acceptance as unverified; synthetic measurements must never be reported as device measurements. No device, network or app operation is performed by this helper. Do not include private device identifiers or raw captures in Issue comments. Map readiness, jank, CPU/RAM and LAN performance require separate measurements.
