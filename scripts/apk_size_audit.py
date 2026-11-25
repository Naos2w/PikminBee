#!/usr/bin/env python3
"""Measure the actual APK file, not its enclosing Actions artifact ZIP."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

SUPPORTED_ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}


def audit(apk, source_sha="", expected_abi=None):
    apk = Path(apk)
    digest = hashlib.sha256()
    with apk.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    result = {
        "apk": apk.name,
        "source_sha": source_sha,
        "apk_bytes": apk.stat().st_size,
        "apk_sha256": digest.hexdigest(),
        "abi_bytes": {},
        "category_bytes": {},
        "category_uncompressed_bytes": {},
        "dex_count": 0,
    }
    compressed = 0
    with zipfile.ZipFile(apk) as archive:
        for entry in archive.infolist():
            if entry.is_dir():
                continue
            name = entry.filename
            size = entry.compress_size
            compressed += size
            native = re.fullmatch(r"lib/([^/]+)/[^/]+\.so", name)
            if native:
                category = "native"
                abi = native.group(1)
                result["abi_bytes"][abi] = result["abi_bytes"].get(abi, 0) + size
            elif re.fullmatch(r"classes(?:\d+)?\.dex", name):
                category = "dex"
                result["dex_count"] += 1
            elif name.startswith("assets/lan/"):
                category = "web"
            elif name.startswith("assets/"):
                category = "other_assets"
            elif name.startswith("res/") or name == "resources.arsc":
                category = "resources"
            else:
                category = "other"
            for key, amount in (("category_bytes", size),
                                ("category_uncompressed_bytes", entry.file_size)):
                counts = result[key]
                counts[category] = counts.get(category, 0) + amount
    result["archive_overhead_bytes"] = result["apk_bytes"] - compressed
    result["abis"] = sorted(result["abi_bytes"])
    if expected_abi:
        if expected_abi not in SUPPORTED_ABIS | {"universal"}:
            raise ValueError(f"Unknown expected ABI: {expected_abi}")
        expected = SUPPORTED_ABIS if expected_abi == "universal" else {expected_abi}
        actual = set(result["abis"])
        if actual != expected:
            raise ValueError(f"Expected ABI set {sorted(expected)}, got {sorted(actual)}")
        if not result["dex_count"]:
            raise ValueError("APK contains no DEX")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--source-sha", default="")
    parser.add_argument("--expected-abi", choices=sorted(SUPPORTED_ABIS | {"universal"}))
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = json.dumps(audit(args.apk, args.source_sha, args.expected_abi),
                        indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    print(report, end="")


if __name__ == "__main__":
    main()
