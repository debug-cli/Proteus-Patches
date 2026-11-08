import importlib.util
import io
import json
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

spec = importlib.util.spec_from_file_location("verify_android_bundle", Path(__file__).with_name("verify_android_bundle.py"))
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


class VerifyAndroidBundleTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.bundle = self.root / "patches-2.2.3.mpp"
        self.patch_list = self.root / "patches-list.json"
        self.patch_list.write_text(json.dumps({"version": "2.2.3", "patches": [{"name": "example"}]}))

    def make_extension_dex(self, classes=None, unreferenced=()):
        classes = verifier.EXPECTED_EXTENSION_CLASSES if classes is None else classes
        descriptors = [verifier.EXTENSION_DESCRIPTOR_PREFIX + cls.encode() + b";" for cls in classes]
        count = len(descriptors)
        strings_off = 112
        types_off = strings_off + 4 * count
        class_defs_off = types_off + 4 * count
        data = bytearray(class_defs_off + 32 * count)
        data[:8] = b"dex\n035\0"
        struct.pack_into("<II", data, 36, 112, 0x12345678)
        for offset, table_off in ((56, strings_off), (64, types_off), (96, class_defs_off)):
            struct.pack_into("<II", data, offset, count, table_off if count else 0)
        for index, descriptor in enumerate(descriptors):
            struct.pack_into("<I", data, strings_off + 4 * index, len(data))
            struct.pack_into("<I", data, types_off + 4 * index, index)
            struct.pack_into("<I", data, class_defs_off + 32 * index, index)
            self.assertLess(len(descriptor), 128)  # one-byte ULEB128 in this fixture
            data.extend(bytes([len(descriptor)]) + descriptor + b"\0")
        for cls in unreferenced:
            data.extend(verifier.EXTENSION_DESCRIPTOR_PREFIX + cls.encode() + b";\0")
        struct.pack_into("<I", data, 32, len(data))
        return bytes(data)

    def make_extension_mpe(self, classes=None, raw=False, extension_bytes=None):
        dex = extension_bytes if extension_bytes is not None else self.make_extension_dex(classes)
        if raw:
            return dex
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as inner:
            inner.writestr("classes.dex", dex)
        return buffer.getvalue()

    def make_bundle(self, dex=b"dex\n035\0" + bytes(104), version="2.2.3", with_extension=True,
                    extension_classes=None, raw_extension=False, extension_bytes=None):
        with zipfile.ZipFile(self.bundle, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", f"Manifest-Version: 1.0\r\nVersion: {version}\r\n\r\n")
            if dex is not None:
                archive.writestr("classes.dex", dex)
            if with_extension:
                archive.writestr("extensions/extension.mpe", self.make_extension_mpe(
                    extension_classes, raw=raw_extension, extension_bytes=extension_bytes))

    def verify(self):
        verifier.verify_bundle(self.bundle, "2.2.3", self.patch_list)

    def test_valid_bundle(self):
        self.make_bundle()
        self.verify()

    def test_raw_mpe_dex_verified(self):
        self.make_bundle(raw_extension=True)
        self.verify()

    def test_raw_mpe_missing_class_rejected_even_if_descriptor_is_referenced(self):
        missing = tuple(cls for cls in verifier.EXPECTED_EXTENSION_CLASSES if cls != "SignatureHookApp")
        dex = self.make_extension_dex(missing, unreferenced=("SignatureHookApp",))
        self.make_bundle(raw_extension=True, extension_bytes=dex)
        with self.assertRaisesRegex(ValueError, "SignatureHookApp"):
            self.verify()

    def test_raw_mpe_bad_class_index_rejected(self):
        dex = bytearray(self.make_extension_dex())
        class_off = struct.unpack_from("<I", dex, 100)[0]
        struct.pack_into("<I", dex, class_off, 99999)
        self.make_bundle(raw_extension=True, extension_bytes=bytes(dex))
        with self.assertRaisesRegex(ValueError, "class type index"):
            self.verify()

    def test_unrecognized_mpe_rejected(self):
        self.make_bundle(raw_extension=True, extension_bytes=b"not a dex")
        with self.assertRaisesRegex(ValueError, "no recognizable DEX"):
            self.verify()

    def test_jvm_only_archive_rejected(self):
        self.make_bundle(dex=None)
        with self.assertRaisesRegex(ValueError, "missing classes.dex"):
            self.verify()

    def test_empty_or_invalid_dex_rejected(self):
        for dex in (b"", b"not dex" + bytes(112)):
            with self.subTest(dex=dex):
                self.make_bundle(dex=dex)
                with self.assertRaisesRegex(ValueError, "Invalid or empty DEX"):
                    self.verify()

    def test_wrong_manifest_version_rejected(self):
        self.make_bundle(version="2.2.2")
        with self.assertRaisesRegex(ValueError, "manifest version"):
            self.verify()

    def test_empty_patch_list_rejected(self):
        self.make_bundle()
        self.patch_list.write_text(json.dumps({"version": "2.2.3", "patches": []}))
        with self.assertRaisesRegex(ValueError, "Patch list is empty"):
            self.verify()

    def test_wrong_patch_list_version_rejected(self):
        self.make_bundle()
        self.patch_list.write_text(json.dumps({"version": "2.2.2", "patches": [{"name": "example"}]}))
        with self.assertRaisesRegex(ValueError, "Patch list version"):
            self.verify()

    def test_missing_extension_classes_rejected(self):
        self.make_bundle(extension_classes=())
        with self.assertRaisesRegex(ValueError, "Extension classes missing"):
            self.verify()

    def test_extension_payload_absent_is_advisory_not_fatal(self):
        self.make_bundle(with_extension=False)
        self.verify()  # advisory only — must not raise

    def test_single_missing_extension_class_rejected(self):
        missing_one = tuple(
            cls for cls in verifier.EXPECTED_EXTENSION_CLASSES if cls != "SignatureHookApp"
        )
        self.make_bundle(extension_classes=missing_one)
        with self.assertRaisesRegex(ValueError, "SignatureHookApp"):
            self.verify()


if __name__ == "__main__":
    unittest.main()
