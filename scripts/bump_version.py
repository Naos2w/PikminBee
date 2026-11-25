#!/usr/bin/env python3
"""Small explicit release-version helper; no Git, signing or deployment side effects."""

import argparse
from dataclasses import dataclass
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent.parent
VERSION_RE = re.compile(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)")


@dataclass(frozen=True, order=True)
class Version:
    major: int
    minor: int
    patch: int

    @classmethod
    def parse(cls, value: str) -> "Version":
        match = VERSION_RE.fullmatch(value)
        if match is None:
            raise ValueError(f"Invalid version {value!r}; expected X.Y.Z")
        result = cls(*(int(part) for part in match.groups()))
        if max(result.major, result.minor, result.patch) >= 1000:
            raise ValueError("Version components must be below 1000")
        return result

    def __str__(self) -> str:
        return f"{self.major}.{self.minor}.{self.patch}"


def property_value(contents: str, key: str) -> str:
    values = re.findall(rf"^{re.escape(key)}=([^\r\n]*)$", contents, re.MULTILINE)
    if len(values) != 1:
        raise ValueError(f"Expected exactly one {key} in gradle.properties")
    return values[0].strip()


def versions(contents: str) -> tuple[Version, Version]:
    dev = Version.parse(property_value(contents, "pikminbeeDevVersion"))
    uat = Version.parse(property_value(contents, "pikminbeeUatVersion"))
    if uat.patch != 1:
        raise ValueError("UAT patch must be 1; a new UAT increments minor")
    if (dev.major, dev.minor) != (uat.major, uat.minor) or dev.patch <= uat.patch:
        raise ValueError("DEV must be a later patch in the same major.minor as UAT")
    return dev, uat


def production_version(contents: str) -> Version:
    version = Version.parse(property_value(contents, "pikminbeeProductionVersion"))
    if version.major < 1:
        raise ValueError("Production starts at 1.0.0")
    return version


def update_production(contents: str, target: str) -> str:
    # DEV and UAT are independently managed, and must remain valid/unchanged.
    versions(contents)
    version = Version.parse(target)
    if version.major < 1:
        raise ValueError("Production starts at 1.0.0")
    key = "pikminbeeProductionVersion"
    if re.search(rf"^{key}=", contents, re.MULTILINE):
        old = production_version(contents)
        if version <= old:
            raise ValueError("The next production version must increase")
        return re.sub(rf"^{key}=[^\r\n]*$", f"{key}={version}", contents,
                      count=1, flags=re.MULTILINE)
    return contents.rstrip("\r\n") + f"\n\n# Independent production release version.\n{key}={version}\n"


def updated_versions(dev: Version, uat: Version, channel: str) -> tuple[Version, Version]:
    if channel == "dev":
        next_dev, next_uat = Version(dev.major, dev.minor, dev.patch + 1), uat
    elif channel == "uat":
        next_uat = Version(uat.major, uat.minor + 1, 1)
        next_dev = Version(next_uat.major, next_uat.minor, 2)
    else:
        raise ValueError(f"Unsupported channel: {channel}")
    versions(f"pikminbeeDevVersion={next_dev}\npikminbeeUatVersion={next_uat}\n")
    return next_dev, next_uat


def update(contents: str, channel: str) -> str:
    dev, uat = versions(contents)
    next_dev, next_uat = updated_versions(dev, uat, channel)
    for key, version in (
        ("pikminbeeDevVersion", next_dev),
        ("pikminbeeUatVersion", next_uat),
    ):
        contents, count = re.subn(
            rf"^{re.escape(key)}=[^\r\n]*$",
            f"{key}={version}",
            contents,
            count=1,
            flags=re.MULTILINE,
        )
        if count != 1:
            raise ValueError(f"Could not update {key}")
    return contents


def main() -> None:
    parser = argparse.ArgumentParser(description="Advance PikminBee semantic versions")
    parser.add_argument("channel", choices=("check", "dev", "uat", "production"))
    parser.add_argument("--set-version", help="Explicit next production version (X.Y.Z)")
    parser.add_argument("--file", type=Path, default=ROOT / "gradle.properties")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    original = args.file.read_text(encoding="utf-8")
    if args.channel == "production":
        if not args.set_version:
            parser.error("production requires --set-version X.Y.Z")
        new = update_production(original, args.set_version)
    else:
        if args.set_version:
            parser.error("--set-version is only valid with production")
        new = original if args.channel == "check" else update(original, args.channel)
    if args.channel == "production" or re.search(r"^pikminbeeProductionVersion=", new, re.MULTILINE):
        print(f"PROD {production_version(new)}")
    before_dev, before_uat = versions(original)
    after_dev, after_uat = versions(new)
    print(f"UAT {before_uat} -> {after_uat}; DEV {before_dev} -> {after_dev}")
    if new != original and not args.dry_run:
        args.file.write_text(new, encoding="utf-8")
        print(f"Updated {args.file}")


if __name__ == "__main__":
    main()
