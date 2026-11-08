import copy
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("verify_v229_catalog", Path(__file__).with_name("verify_v229_catalog.py"))
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


def valid_catalog():
    patches = []
    for package, names in verifier.EXPECTED.items():
        for name in sorted(names):
            placeholder = name in verifier.PLACEHOLDERS
            patches.append({
                "name": name,
                "description": "UNIMPLEMENTED PLACEHOLDER. Selecting this entry fails." if placeholder else "heuristic",
                "default": False,
                "dependencies": [] if placeholder else ["BytecodePatch"],
                "compatiblePackages": [{"packageName": package, "apkFileType": "XAPK", "targets": [{"version": "test"}]}],
            })
    patches.append({"name": "Block ads & ad SDKs", "default": False, "compatiblePackages": None})
    return {"version": "2.2.9", "patches": patches}


class VerifyV229CatalogTest(unittest.TestCase):
    def setUp(self):
        self.catalog = valid_catalog()

    def entry(self, name):
        return next(p for p in self.catalog["patches"] if p["name"] == name)

    def test_valid(self):
        summary = verifier.verify_catalog(self.catalog)
        self.assertEqual(summary["entries"], 31)
        self.assertEqual(summary["app_families"], 3)
        self.assertEqual(summary["placeholders"], 14)
        self.assertEqual(summary["universal_entries"], 1)

    def test_app_family_variants_are_grouped(self):
        for package in verifier.APP_FAMILY_ALIASES:
            self.catalog["patches"].append({"name": package, "compatiblePackages": [{"packageName": package}]})
        summary = verifier.verify_catalog(self.catalog)
        self.assertEqual(summary["package_targets"], 9)
        self.assertEqual(summary["app_families"], 6)

    def test_missing_or_duplicate_entry(self):
        p = self.entry("WhatsApp: Keep deleted messages")
        self.catalog["patches"].remove(p)
        with self.assertRaisesRegex(ValueError, "missing"):
            verifier.verify_catalog(self.catalog)
        self.catalog["patches"].extend([p, copy.deepcopy(p)])
        with self.assertRaisesRegex(ValueError, "duplicates"):
            verifier.verify_catalog(self.catalog)

    def test_unsafe_or_misleading_placeholder(self):
        p = self.entry("Proton Pass: Dark web monitoring local-only (cosmetic)")
        p["description"] = "Fully implemented"
        with self.assertRaisesRegex(ValueError, "placeholder"):
            verifier.verify_catalog(self.catalog)
        p["description"] = "UNIMPLEMENTED PLACEHOLDER. Selecting this entry fails."
        p["default"] = True
        with self.assertRaisesRegex(ValueError, "off by default"):
            verifier.verify_catalog(self.catalog)
        p["default"] = False
        p["dependencies"] = ["BytecodePatch"]
        with self.assertRaisesRegex(ValueError, "must not execute dependencies"):
            verifier.verify_catalog(self.catalog)

    def test_experimental_proton_1415_target(self):
        proton_patches = [p for p in self.catalog["patches"] if any(
            c.get("packageName") == "proton.android.pass" for c in p.get("compatiblePackages") or []
        )]
        for patch in proton_patches:
            patch["compatiblePackages"][0]["targets"] = [
                {"version": "1.41.3"},
                {"version": "1.41.5", "isExperimental": True, "versionCodes": None, "minSdk": 27},
            ]
        verifier.verify_catalog(self.catalog, require_proton_1415=True)
        target = proton_patches[0]["compatiblePackages"][0]["targets"][1]
        target["isExperimental"] = False
        with self.assertRaisesRegex(ValueError, "experimental"):
            verifier.verify_catalog(self.catalog, require_proton_1415=True)
        target["isExperimental"] = True
        target["versionCodes"] = {"ARM64_V8A": 123}
        with self.assertRaisesRegex(ValueError, "variant metadata"):
            verifier.verify_catalog(self.catalog, require_proton_1415=True)
        target["versionCodes"] = None
        proton_patches[0]["compatiblePackages"][0]["targets"].pop(0)
        with self.assertRaisesRegex(ValueError, "retain the 1.41.3"):
            verifier.verify_catalog(self.catalog, require_proton_1415=True)

    def test_missing_universal_ad_patch(self):
        self.catalog["patches"].remove(self.entry("Block ads & ad SDKs"))
        with self.assertRaisesRegex(ValueError, "universal"):
            verifier.verify_catalog(self.catalog)

    def test_missing_dependency_or_wrong_scope(self):
        p = self.entry("Table Notes: Block ads & ad SDKs")
        p["dependencies"] = []
        with self.assertRaisesRegex(ValueError, "dependency"):
            verifier.verify_catalog(self.catalog)
        p["dependencies"] = ["BytecodePatch"]
        p["compatiblePackages"].append({"packageName": "unintended"})
        with self.assertRaisesRegex(ValueError, "exclusively"):
            verifier.verify_catalog(self.catalog)

    def test_version_and_target_checked(self):
        with self.assertRaisesRegex(ValueError, "version"):
            verifier.verify_catalog(self.catalog, "2.2.8")
        self.entry("WhatsApp: Block ads & ad SDKs")["compatiblePackages"][0]["apkFileType"] = "APK"
        with self.assertRaisesRegex(ValueError, "XAPK"):
            verifier.verify_catalog(self.catalog)


if __name__ == "__main__":
    unittest.main()
