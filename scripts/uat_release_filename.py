#!/usr/bin/env python3
"""Read a verified AGP UAT metadata file and return its safe semantic version.

The APK itself stays byte-for-byte unchanged; release workflow renames a copy
*after* package identity and stable signer verification.
"""

import json
import sys
from pathlib import Path

from bump_version import Version


def verified_uat_version(metadata: dict) -> str:
    if metadata.get("applicationId") != "com.naos.pikminbee.uat":
        raise ValueError("Refusing versioned UAT name for unexpected applicationId")
    elements = metadata.get("elements")
    if not isinstance(elements, list) or len(elements) != 1:
        raise ValueError("Expected exactly one Android APK output")
    raw = elements[0].get("versionName")
    if not isinstance(raw, str):
        raise ValueError("Missing Android versionName")
    version = Version.parse(raw)
    return str(version)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: uat_release_filename.py <output-metadata.json>")
    data = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    print(verified_uat_version(data))


if __name__ == "__main__":
    main()
