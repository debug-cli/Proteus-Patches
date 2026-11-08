#!/usr/bin/env python3
"""Reject JVM-only or corrupt patch bundles before publishing them to Android users."""

import argparse
import io
import json
import re
import struct
import sys
import zipfile
from pathlib import Path


# Every extension class injected from smali at patch time MUST survive the
# extension build. The inline-smali snippets reference these descriptor
# strings but are invisible to the extension module's compile-time classpath,
# so a silent strip here ships an APK that dies with NoClassDefFoundError
# (this is the exact failure reported in issue #5 for SignatureHookApp).
EXPECTED_EXTENSION_CLASSES = (
    "AmazonHelper",
    "AmazonUrls",
    "GmsCoreSupportPatch",
    "InstallSourceHelper",
    "MereframeDiagHook",
    "MereframeIntegrityHook",
    "PhotosDcimFolderPatch",
    "ProteusListMemo",
    "RedditContentFilterHelper",
    "RedditDeletedCommentHelper",
    "RedditDiagHook",
    "RedditFeedHelper",
    "SignatureHookApp",
)

EXTENSION_DESCRIPTOR_PREFIX = b"Lapp/template/extension/extension/"
MAX_NEST_DEPTH = 3


def collect_dex_blobs(names_and_bytes: list[tuple[str, bytes]], depth: int = 0) -> list[tuple[str, bytes]]:
    """Find .dex entries, raw-Dex .mpe payloads, and DEX in nested archives."""
    blobs: list[tuple[str, bytes]] = []
    for name, data in names_and_bytes:
        if name.endswith(".dex") or (name.endswith(".mpe") and data.startswith(b"dex\n")):
            blobs.append((name, data))
            continue
        if depth >= MAX_NEST_DEPTH:
            continue
        if not data.startswith(b"PK\x03\x04"):
            continue
        try:
            with io.BytesIO(data) as stream, zipfile.ZipFile(stream) as inner:
                members = [(f"{name}!{i.filename}", inner.read(i)) for i in inner.infolist()]
            blobs.extend(collect_dex_blobs(members, depth + 1))
        except zipfile.BadZipFile:
            continue
    return blobs


def dex_defined_types(data: bytes) -> set[bytes]:
    """Read class_def descriptors so mere string references do not pass as definitions."""
    if len(data) < 112 or not re.fullmatch(rb"dex\n\d{3}\x00", data[:8]):
        raise ValueError("invalid DEX header")

    def u32(offset: int) -> int:
        return struct.unpack_from("<I", data, offset)[0]

    if u32(32) != len(data) or u32(36) != 112 or u32(40) != 0x12345678:
        raise ValueError("invalid DEX size, header size, or endianness")

    def table(size_offset: int, item_size: int) -> tuple[int, int]:
        count, offset = u32(size_offset), u32(size_offset + 4)
        if count and (offset < 112 or offset > len(data) or count > (len(data) - offset) // item_size):
            raise ValueError("DEX table lies outside the file")
        return count, offset

    string_count, strings_off = table(56, 4)
    type_count, types_off = table(64, 4)
    class_count, classes_off = table(96, 32)
    result: set[bytes] = set()
    for index in range(class_count):
        class_index = u32(classes_off + 32 * index)
        if class_index >= type_count:
            raise ValueError("DEX class type index out of range")
        string_index = u32(types_off + 4 * class_index)
        if string_index >= string_count:
            raise ValueError("DEX descriptor string index out of range")
        cursor = u32(strings_off + 4 * string_index)
        if cursor < 112 or cursor >= len(data):
            raise ValueError("DEX string data offset out of range")
        # Skip the string's ULEB128 UTF-16 length; class descriptors are ASCII.
        for _ in range(5):
            if cursor >= len(data):
                raise ValueError("truncated DEX string length")
            byte = data[cursor]
            cursor += 1
            if byte & 0x80 == 0:
                break
        else:
            raise ValueError("invalid DEX string length")
        end = data.find(b"\x00", cursor)
        if end < 0:
            raise ValueError("unterminated DEX descriptor")
        result.add(data[cursor:end])
    return result


def verify_extension_classes(archive: zipfile.ZipFile) -> None:
    members = [(i.filename, archive.read(i)) for i in archive.infolist()]
    dex_blobs = collect_dex_blobs(members)
    # Morphe's extension.mpe is a raw DEX in current builds; older layouts can
    # also put a DEX inside a nested archive. Root classes*.dex belong to the
    # patch bundle itself and cannot establish that the extension is present.
    candidates = [
        (name, data)
        for name, data in dex_blobs
        if "!" in name or name.endswith(".mpe")
    ]
    print(
        f"Bundle contains {len(dex_blobs)} DEX payload(s) "
        f"({len(candidates)} extension payload(s)): "
        + ", ".join(sorted(name for name, _ in dex_blobs))
    )

    if candidates:
        defined = set()
        for name, data in candidates:
            try:
                defined.update(dex_defined_types(data))
            except (ValueError, struct.error) as error:
                raise ValueError(f"Invalid extension DEX {name}: {error}") from error
        missing = [
            cls for cls in EXPECTED_EXTENSION_CLASSES
            if EXTENSION_DESCRIPTOR_PREFIX + cls.encode() + b";" not in defined
        ]
        if missing:
            raise ValueError(
                "Extension classes missing from the DEX payload(s): "
                + ", ".join(sorted(missing))
                + " — the extension build stripped or failed to compile a class that patches inject from smali"
            )
        print(f"Verified {len(EXPECTED_EXTENSION_CLASSES)} extension class definitions across {len(candidates)} DEX file(s)")
        return

    if any(name.endswith(".mpe") for name, _ in members):
        raise ValueError("Extension MPE is present but contains no recognizable DEX payload")
    print(
        "ADVISORY: no extension DEX payload detected in the .mpp — extension classes could not be "
        "verified. Patches that require them may fail at patch time."
    )


def verify_bundle(bundle: Path, version: str, patch_list: Path | None = None) -> None:
    if bundle.name != f"patches-{version}.mpp":
        raise ValueError(f"Unexpected bundle filename: {bundle.name}")

    with zipfile.ZipFile(bundle) as archive:
        bad_entry = archive.testzip()
        if bad_entry:
            raise ValueError(f"Corrupt archive entry: {bad_entry}")
        dex_entries = [entry for entry in archive.infolist() if re.fullmatch(r"classes(?:[2-9]|[1-9][0-9]+)?\.dex", entry.filename)]
        if not any(entry.filename == "classes.dex" for entry in dex_entries):
            raise ValueError("Android bundle is missing classes.dex (it may have been overwritten by the JVM jar task)")
        for entry in dex_entries:
            with archive.open(entry) as dex:
                header = dex.read(8)
            if entry.file_size < 112 or not re.fullmatch(rb"dex\n\d{3}\x00", header):
                raise ValueError(f"Invalid or empty DEX entry: {entry.filename}")

        verify_extension_classes(archive)

        # Manifest lines may be folded per the JAR specification.
        manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8").replace("\r\n ", "").replace("\n ", "")
        attributes = dict(line.split(": ", 1) for line in manifest.splitlines() if ": " in line)
        if attributes.get("Version") != version:
            raise ValueError(f"Bundle manifest version is {attributes.get('Version')!r}, expected {version!r}")

    if patch_list is not None:
        metadata = json.loads(patch_list.read_text(encoding="utf-8"))
        if metadata.get("version") != version:
            raise ValueError("Patch list version does not match the bundle")
        if not isinstance(metadata.get("patches"), list) or not metadata["patches"]:
            raise ValueError("Patch list is empty or invalid")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    parser.add_argument("version")
    parser.add_argument("--patch-list", type=Path)
    args = parser.parse_args()
    try:
        verify_bundle(args.bundle, args.version, args.patch_list)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        print(f"Bundle verification failed: {error}", file=sys.stderr)
        return 1
    print(f"Verified Android bundle {args.bundle} (version {args.version})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
