#!/usr/bin/env python3
"""Validate a signed production APK before copying public release assets."""

import argparse
import json
import re
import shutil
from pathlib import Path

from apk_size_audit import audit, SUPPORTED_ABIS
from bump_version import Version, production_version

PACKAGE = "com.naos.pikminbee.prod"
UAT_CERTIFICATE = "7a34ec161ae557f0c2db222b54696e0ef574bd3f95d135e6597a7bff7e96f807"


def version_code(version: Version) -> int:
    if version.major < 1:
        raise ValueError("Production versions start at 1.0.0")
    return version.major * 1_000_000 + version.minor * 1_000 + version.patch


def validate_metadata(data: dict, version: Version) -> str:
    if data.get("applicationId") != PACKAGE:
        raise ValueError("Unexpected production application ID")
    elements = data.get("elements")
    if not isinstance(elements, list) or len(elements) != 1:
        raise ValueError("Expected exactly one production APK")
    element = elements[0]
    if element.get("versionName") != str(version) or element.get("versionCode") != version_code(version):
        raise ValueError("Unexpected production version or versionCode")
    filename = element.get("outputFile")
    if not isinstance(filename, str) or not re.fullmatch(r"[A-Za-z0-9._-]+\.apk", filename):
        raise ValueError("Invalid Android output filename")
    return filename


def validate_badging(text: str, version: Version) -> None:
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", text, re.M)
    if not package or package.groups() != (PACKAGE, str(version_code(version)), str(version)):
        raise ValueError("Packaged manifest does not match production identity")
    if re.search(r"^application-debuggable\b", text, re.M):
        raise ValueError("Production APK must not be debuggable")


def validate_certificate(report: str, expected: str) -> str:
    expected = expected.strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected) or expected == UAT_CERTIFICATE:
        raise ValueError("Missing or invalid independent production certificate pin")
    actual = {x.lower() for x in re.findall(
        r"certificate SHA-256 digest:\s*([0-9a-fA-F]{64})(?:\s|$)", report)}
    if actual != {expected}:
        raise ValueError("Unexpected, missing or ambiguous APK signing certificate")
    return expected


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--properties", type=Path, default=Path("gradle.properties"))
    parser.add_argument("--version", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--abi", choices=sorted(SUPPORTED_ABIS | {"universal"}), required=True)
    parser.add_argument("--badging", type=Path, required=True)
    parser.add_argument("--certificate-report", type=Path, required=True)
    parser.add_argument("--expected-certificate", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    version = production_version(args.properties.read_text(encoding="utf-8"))
    if str(version) != args.version:
        raise ValueError("Requested release version does not match committed version")
    if not re.fullmatch(r"[0-9a-f]{40}", args.source_sha):
        raise ValueError("Expected a full source commit SHA")
    filename = validate_metadata(json.loads(args.metadata.read_text(encoding="utf-8")), version)
    if args.apk.name != filename:
        raise ValueError("APK does not match Android output metadata")
    validate_badging(args.badging.read_text(encoding="utf-8"), version)
    fingerprint = validate_certificate(args.certificate_report.read_text(encoding="utf-8"), args.expected_certificate)
    report = audit(args.apk, source_sha=args.source_sha, expected_abi=args.abi)
    report.update(application_id=PACKAGE, version_name=str(version), version_code=version_code(version),
                  signer_certificate_sha256=fingerprint, debuggable=False)
    args.output.mkdir(parents=True, exist_ok=False)
    suffix = "" if args.abi == "arm64-v8a" else "-" + args.abi
    output_apk = args.output / f"PikminBee-{version}{suffix}.apk"
    shutil.copyfile(args.apk, output_apk)
    copied = audit(output_apk, expected_abi=args.abi)
    if copied["apk_sha256"] != report["apk_sha256"]:
        raise ValueError("APK changed while copying release assets")
    report["apk"] = output_apk.name
    (args.output / "apk-size-report.json").write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
