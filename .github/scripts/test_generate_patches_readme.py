import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


class GeneratePatchesReadmeTest(unittest.TestCase):
    def test_badges_distinguish_entries_and_package_targets(self):
        script = Path(__file__).with_name("generate_patches_readme.py")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            catalog = root / "patches.json"
            readme = root / "README.md"
            catalog.write_text(json.dumps({
                "version": "2.2.9",
                "patches": [
                    {"name": "One", "compatiblePackages": [
                        {"packageName": "a", "name": "App A", "targets": []},
                        {"packageName": "b", "name": "App B", "targets": []},
                    ]},
                    {"name": "Universal", "compatiblePackages": None},
                ],
            }))
            readme.write_text(
                "badge/patch%20entries-999 badge/universal-999 badge/package%20targets-999\n"
                "<!-- PATCHES_START -->\nold\n<!-- PATCHES_END -->\n"
            )
            subprocess.run([sys.executable, str(script), "owner/repo", "branch", str(catalog), str(readme)], check=True)
            text = readme.read_text()
            self.assertIn("badge/patch%20entries-2", text)
            self.assertIn("badge/universal-1", text)
            self.assertIn("badge/package%20targets-2", text)
            self.assertIn("3 patches total", text)  # two package appearances plus one universal


if __name__ == "__main__":
    unittest.main()
