#!/usr/bin/env python3
"""Verify the v2.2.9 patch catalog generated from the built MPP, not hand-edited JSON."""

import argparse
import json
import sys
from collections import Counter
from pathlib import Path

WHATSAPP_PLACEHOLDERS = {
    "WhatsApp: Keep deleted messages",
    "WhatsApp: Read receipts (no blue ticks)",
    "WhatsApp: Typing indicator (hide when typing)",
    "WhatsApp: View once media (allow reopen & save)",
    "WhatsApp: App icon (classic or premium style)",
    "WhatsApp: Mods settings entry",
    "WhatsApp: Mods settings resources",
}
WHATSAPP_IMPLEMENTED = {
    "WhatsApp: Block analytics & tracking SDKs",
    "WhatsApp: Block ads & ad SDKs",
    "WhatsApp: Prefer external browser over Custom Tabs",
    "WhatsApp: Allow screenshots & screen recording",
    "WhatsApp: Remove dangerous permissions",
    "WhatsApp: Disable video autoplay (Status)",
    "WhatsApp: Strip logging (android.util.Log)",
    "WhatsApp: Block screenshots & screen recording",
    "WhatsApp: Empty telephony identifiers",
    "WhatsApp: Limit usage data (diagnostics & analytics)",
}
PROTON_PLACEHOLDERS = {
    "Proton Pass: Remove upgrade nag UI",
    "Proton Pass: Disable auto-lock / extend timeout",
    "Proton Pass: Disable clipboard auto-clear",
    "Proton Pass: Unlock multiple vaults",
    "Proton Pass: Force biometric always-on",
    "Proton Pass: Unlock password-generator options",
    "Proton Pass: Dark web monitoring local-only (cosmetic)",
}
PROTON_IMPLEMENTED = {
    "Proton Pass: Allow screenshots & screen recording",
    "Proton Pass: Block telemetry & crash reporting",
    "Unlock Unlimited Plan",
}
TABLE_NOTES = {
    "Table Notes: Block analytics & tracking SDKs",
    "Table Notes: Block ads & ad SDKs",
    "Table Notes: Prefer external browser over Custom Tabs",
}
EXPECTED = {
    "com.whatsapp": WHATSAPP_PLACEHOLDERS | WHATSAPP_IMPLEMENTED,
    "proton.android.pass": PROTON_PLACEHOLDERS | PROTON_IMPLEMENTED,
    "vivekagarwal.playwithdb": TABLE_NOTES,
}
PLACEHOLDERS = WHATSAPP_PLACEHOLDERS | PROTON_PLACEHOLDERS
HEURISTICS = WHATSAPP_IMPLEMENTED | PROTON_IMPLEMENTED | TABLE_NOTES

# Package variants of the same app count once; Telegram Plus remains separate.
APP_FAMILY_ALIASES = {
    "com.amazon.mShop.android.shopping": "family:amazon-shopping",
    "in.amazon.mShop.android.shopping": "family:amazon-shopping",
    "app.simple.inure": "family:inure",
    "app.simple.inure.play": "family:inure",
    "org.telegram.messenger": "family:telegram",
    "org.telegram.messenger.web": "family:telegram",
}


def verify_catalog(data: dict, version: str = "2.2.9", *, require_proton_1415: bool = False) -> dict[str, int]:
    if data.get("version") != version:
        raise ValueError(f"Catalog version {data.get('version')!r} does not match {version}")
    patches = data.get("patches")
    if not isinstance(patches, list) or not patches:
        raise ValueError("Catalog has no patches")

    for package, names in EXPECTED.items():
        entries = [p for p in patches if any(c.get("packageName") == package for c in p.get("compatiblePackages") or [])]
        found = Counter(p.get("name") for p in entries)
        missing, unexpected = names - found.keys(), found.keys() - names
        duplicates = {name for name, count in found.items() if count != 1}
        if missing or unexpected or duplicates:
            raise ValueError(f"{package}: missing={sorted(missing)}, unexpected={sorted(unexpected)}, duplicates={sorted(duplicates)}")
        for entry in entries:
            name = entry["name"]
            compatibility = entry.get("compatiblePackages") or []
            if len(compatibility) != 1 or compatibility[0].get("packageName") != package:
                raise ValueError(f"{name} is not scoped exclusively to {package}")
            if compatibility[0].get("apkFileType") != "XAPK" or not compatibility[0].get("targets"):
                raise ValueError(f"{name} lacks its XAPK/version compatibility metadata")
            if package == "proton.android.pass" and require_proton_1415:
                targets = compatibility[0]["targets"]
                new_targets = [target for target in targets if target.get("version") == "1.41.5"]
                if len(new_targets) != 1 or new_targets[0].get("isExperimental") is not True:
                    raise ValueError(f"{name} must list Proton Pass 1.41.5 as experimental")
                if new_targets[0].get("versionCodes") is not None or new_targets[0].get("minSdk") != 27:
                    raise ValueError(f"{name} has unexpected Proton Pass 1.41.5 variant metadata")
                if not any(target.get("version") == "1.41.3" for target in targets):
                    raise ValueError(f"{name} must retain the 1.41.3 target")
            if entry.get("default") is not False:
                raise ValueError(f"{name} must be off by default")
            if name in PLACEHOLDERS:
                description = entry.get("description") or ""
                if "UNIMPLEMENTED PLACEHOLDER" not in description or "fails" not in description:
                    raise ValueError(f"{name} must clearly state it is a failing placeholder")
                if entry.get("dependencies"):
                    raise ValueError(f"{name} placeholder must not execute dependencies")
            elif name in HEURISTICS and not entry.get("dependencies"):
                raise ValueError(f"{name} is missing its implementation dependency")

    universal_ad = [p for p in patches if p.get("name") == "Block ads & ad SDKs" and not p.get("compatiblePackages")]
    if len(universal_ad) != 1 or universal_ad[0].get("default") is not False:
        raise ValueError("Exactly one opt-in universal Block ads & ad SDKs patch is required")

    packages = {c["packageName"] for p in patches for c in p.get("compatiblePackages") or []}
    universal = [p for p in patches if not p.get("compatiblePackages")]
    return {
        "entries": len(patches),
        "app_families": len({APP_FAMILY_ALIASES.get(package, package) for package in packages}),
        "package_targets": len(packages),
        "universal_entries": len(universal),
        "appearances": len(universal) + sum(len(p.get("compatiblePackages") or []) for p in patches),
        "placeholders": len(PLACEHOLDERS),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("catalog", type=Path)
    parser.add_argument("version", nargs="?", default="2.2.9")
    parser.add_argument("--require-proton-1415", action="store_true", help="Check the experimental 1.41.5 target")
    args = parser.parse_args()
    try:
        summary = verify_catalog(
            json.loads(args.catalog.read_text(encoding="utf-8")),
            args.version,
            require_proton_1415=args.require_proton_1415,
        )
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as error:
        print(f"v2.2.9 catalog verification failed: {error}", file=sys.stderr)
        return 1
    print("Verified v2.2.9 catalog: " + ", ".join(f"{key}={value}" for key, value in summary.items()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
