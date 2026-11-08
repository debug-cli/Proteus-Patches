#!/usr/bin/env bash
# Run only in the explicitly dispatched publication job, after a successful build.
set -euo pipefail

: "${GITHUB_REPOSITORY:?}" "${GITHUB_SHA:?}" "${GITHUB_RUN_ID:?}" "${RUNNER_TEMP:?}" "${GH_TOKEN:?}"
VERSION=2.2.9
BUNDLE="$RUNNER_TEMP/mpp/patches-${VERSION}.mpp"
CATALOG="$RUNNER_TEMP/catalog/patches-list.json"
OUT="$RUNNER_TEMP/published-v229"
mkdir -p "$OUT"
test -f "$BUNDLE"
test -f "$CATALOG"

python3 .github/scripts/verify_android_bundle.py "$BUNDLE" "$VERSION" --patch-list "$CATALOG"
python3 .github/scripts/verify_v229_catalog.py "$CATALOG" "$VERSION"
python3 .github/scripts/verify_patch_source.py patches-bundle.json "$VERSION"

# The catalog checked into the exact build commit must describe this artifact.
git show HEAD:patches-list.json > "$OUT/tracked-catalog.json"
python3 - "$OUT/tracked-catalog.json" "$CATALOG" <<'PY'
import json, sys

def canonical(path):
    data = json.load(open(path, encoding="utf-8"))
    result = []
    for patch in data["patches"]:
        patch["dependencies"] = sorted(patch.get("dependencies") or [])
        if patch.get("compatiblePackages") is not None:
            patch["compatiblePackages"] = sorted(patch["compatiblePackages"], key=lambda c: c["packageName"])
        result.append(json.dumps(patch, sort_keys=True, ensure_ascii=False))
    return data["version"], sorted(result)

if canonical(sys.argv[1]) != canonical(sys.argv[2]):
    raise SystemExit("The checked-in patch catalog differs from the generated bundle catalog; sync it before publishing.")
PY

# Never replace an asset already present on the release without explicit review.
gh release view "v$VERSION" --repo "$GITHUB_REPOSITORY" --json tagName,isDraft,assets > "$OUT/release.json"
python3 - "$OUT/release.json" "$VERSION" <<'PY'
import json, sys
release = json.load(open(sys.argv[1], encoding="utf-8"))
version = sys.argv[2]
if release["tagName"] != "v" + version or release["isDraft"]:
    raise SystemExit("Expected an existing published v2.2.9 release")
reserved = {f"patches-{version}.mpp", f"patches-list-{version}.json", "SHA256SUMS"}
existing = reserved & {asset["name"] for asset in release["assets"]}
if existing:
    raise SystemExit("Refusing to overwrite existing release assets: " + ", ".join(sorted(existing)))
PY

cp "$BUNDLE" "$OUT/patches-${VERSION}.mpp"
cp "$CATALOG" "$OUT/patches-list-${VERSION}.json"
(cd "$OUT" && sha256sum "patches-${VERSION}.mpp" "patches-list-${VERSION}.json" > SHA256SUMS)
BUNDLE_SHA256=$(sha256sum "$BUNDLE" | cut -d' ' -f1)

# Store an unreferenced Git blob for retrieving the binary via the GitHub API
# when the sandbox cannot reach GitHub's release/artifact storage CDN. No binary
# is added to the branch or to the release source tree.
python3 - "$BUNDLE" "$OUT/blob-request.json" <<'PY'
import base64, json, sys
with open(sys.argv[1], "rb") as f:
    content = base64.b64encode(f.read()).decode("ascii")
with open(sys.argv[2], "w", encoding="utf-8") as f:
    json.dump({"content": content, "encoding": "base64"}, f)
PY
BLOB_SHA=$(gh api --method POST "repos/$GITHUB_REPOSITORY/git/blobs" --input "$OUT/blob-request.json" --jq .sha)
test "$BLOB_SHA" = "$(git hash-object "$BUNDLE")"
TAG_COMMIT=$(gh api "repos/$GITHUB_REPOSITORY/commits/v$VERSION" --jq .sha)

python3 - "$CATALOG" "$OUT/notes.md" "$BUNDLE_SHA256" "$BLOB_SHA" "$TAG_COMMIT" <<'PY'
import importlib.util, json, os, sys
from pathlib import Path
spec = importlib.util.spec_from_file_location("catalog", ".github/scripts/verify_v229_catalog.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
summary = module.verify_catalog(json.load(open(sys.argv[1], encoding="utf-8")))
notes = Path("docs/releases/v2.2.9.md").read_text(encoding="utf-8")
repo = os.environ["GITHUB_REPOSITORY"]
sha = os.environ["GITHUB_SHA"]
run = os.environ["GITHUB_RUN_ID"]
notes += (
    f"\n### Build and catalog\n\n"
    f"- Build source: [{sha}](https://github.com/{repo}/tree/{sha}) "
    f"([CI run](https://github.com/{repo}/actions/runs/{run})).\n"
    f"- Existing tag source: `{sys.argv[5]}`; the release source archives therefore do not contain the build fixes.\n"
    f"- Catalog: {summary['entries']} unique entries, {summary['package_targets']} package targets, "
    f"{summary['universal_entries']} universal entries and {summary['placeholders']} placeholders.\n"
    f"- Bundle SHA-256: `{sys.argv[3]}`.\n"
    f"- Bundle Git blob SHA-1 (temporary API retrieval): `{sys.argv[4]}`.\n"
)
Path(sys.argv[2]).write_text(notes, encoding="utf-8")
PY

# Correct the release notes before exposing the artifact. Do not move the tag.
gh release edit "v$VERSION" --repo "$GITHUB_REPOSITORY" --notes-file "$OUT/notes.md"
gh release upload "v$VERSION" --repo "$GITHUB_REPOSITORY" \
    "$OUT/patches-${VERSION}.mpp" "$OUT/patches-list-${VERSION}.json" "$OUT/SHA256SUMS"
