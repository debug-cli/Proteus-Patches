#!/usr/bin/env bash
# One-time, explicitly requested replacement of the existing v2.2.9 assets.
# Publishes the exact previously reviewed candidate, never moves the tag, and
# restores the old assets and notes if an upload or verification fails.
set -euo pipefail

: "${GITHUB_REPOSITORY:?}" "${GITHUB_REF_NAME:?}" "${GITHUB_RUN_ID:?}" "${RUNNER_TEMP:?}" "${GH_TOKEN:?}"
[[ "$GITHUB_REPOSITORY" == "ineednotitle/Proteus-Patches" ]]
[[ "$GITHUB_REF_NAME" == "arena/823830aa-proteus-patches" ]]

TAG=v2.2.9
BUILD_SHA=193b2c67e00a2d379159640fbe0918a0b794a462
BUILD_RUN=42511348019
CATALOG_COMMIT=0afbdfc369927ee972bdbf9bbb66728a5fdc7a1c
BUNDLE_BLOB=4aebda665376bfce353a54188933418a2eca3c32
BUNDLE_SHA256=ffa98d5d59adfbd3f3021539ff58966811178a7d9820ad975340cadd28795a3a
CATALOG_SHA256=39f78b6c96d017f32a84ded123557157a5b8d14112d87335f03d45554713bbe3
TAG_OBJECT=17a4ee41cd5ceb999353b0207746a184c40ecaf6
WORK="$RUNNER_TEMP/v229-proton1415"
NEW="$WORK/new"
BACKUP="$WORK/backup"
VERIFY="$WORK/verify"
mkdir -p "$NEW" "$BACKUP" "$VERIFY"

# Only docs/workflow may differ from the exact candidate source; the catalog
# must be the one synced from that build's generated metadata.
git cat-file -e "$BUILD_SHA^{commit}"
git cat-file -e "$CATALOG_COMMIT^{commit}"
git diff --quiet "$BUILD_SHA" HEAD -- patches extensions gradle settings.gradle.kts gradle.properties
git diff --quiet "$CATALOG_COMMIT" HEAD -- patches-list.json
[[ "$(sha256sum patches-list.json | cut -d' ' -f1)" == "$CATALOG_SHA256" ]]

# The build, bytecode tests, metadata verification, and catalog sync all have
# to have completed successfully on the commit that produced this candidate.
gh api "repos/$GITHUB_REPOSITORY/actions/runs/$BUILD_RUN" > "$WORK/run.json"
gh api "repos/$GITHUB_REPOSITORY/actions/runs/$BUILD_RUN/jobs" > "$WORK/jobs.json"
python3 - "$WORK/run.json" "$WORK/jobs.json" "$BUILD_SHA" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    run = json.load(f)
with open(sys.argv[2], encoding="utf-8") as f:
    jobs = json.load(f)["jobs"]
if run["head_sha"] != sys.argv[3] or run["status"] != "completed" or run["conclusion"] != "success":
    raise SystemExit("The expected candidate build did not complete successfully")
build = next((j for j in jobs if j["name"] == "Build .mpp"), None)
sync = next((j for j in jobs if j["name"] == "Sync generated catalog to branch"), None)
required = {"Test permission, Brave and release safety helpers", "Generate patch metadata",
            "Verify Android bundle and remote source metadata"}
if not build or build["conclusion"] != "success" or not sync or sync["conclusion"] != "success":
    raise SystemExit("The candidate build or catalog sync failed")
passed = {step["name"] for step in build["steps"] if step["conclusion"] == "success"}
if not required <= passed:
    raise SystemExit(f"Candidate verification steps missing: {sorted(required - passed)}")
PY

# Retrieve the exact binary the user reviewed, not a rebuild with a different hash.
gh api "repos/$GITHUB_REPOSITORY/git/blobs/$BUNDLE_BLOB" > "$WORK/blob.json"
python3 - "$WORK/blob.json" "$NEW/patches-2.2.9.mpp" "$BUNDLE_BLOB" "$BUNDLE_SHA256" <<'PY'
import base64, hashlib, json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    blob = json.load(f)
if blob["sha"] != sys.argv[3] or blob["encoding"] != "base64":
    raise SystemExit("Unexpected candidate Git blob")
data = base64.b64decode(blob["content"])
git_sha = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
if len(data) != blob["size"] or git_sha != sys.argv[3] or hashlib.sha256(data).hexdigest() != sys.argv[4]:
    raise SystemExit("Candidate bundle hash/size mismatch")
with open(sys.argv[2], "wb") as f:
    f.write(data)
PY
cp patches-list.json "$NEW/patches-list-2.2.9.json"
python3 .github/scripts/verify_android_bundle.py "$NEW/patches-2.2.9.mpp" 2.2.9 --patch-list "$NEW/patches-list-2.2.9.json"
python3 .github/scripts/verify_v229_catalog.py "$NEW/patches-list-2.2.9.json" 2.2.9 --require-proton-1415
python3 .github/scripts/verify_patch_source.py patches-bundle.json 2.2.9
(cd "$NEW" && sha256sum patches-2.2.9.mpp patches-list-2.2.9.json > SHA256SUMS)

check_original_release() {
    gh api "repos/$GITHUB_REPOSITORY/releases/tags/$TAG" > "$WORK/original-release.json"
    python3 - "$WORK/original-release.json" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    release = json.load(f)
expected = {
    "patches-2.2.9.mpp": (724464650, 2232102, "c4a1ca03317c1319caaec6fbc1dfb404bc28182196ece19e7f26b933c622447d"),
    "patches-list-2.2.9.json": (724464648, 237863, "d749bfb3601d1b402d3317effa823d839bfde465eae80fa7b9652b8ea0a90c02"),
    "SHA256SUMS": (724464649, 174, "d0918721a59f9bcb873828d7bd66c0d37458e2263d020c19f0fae8441dd123e8"),
}
if release["id"] != 438216215 or release["tag_name"] != "v2.2.9" or release["draft"]:
    raise SystemExit("The expected published v2.2.9 release was not found")
assets = {a["name"]: a for a in release["assets"]}
for name, (asset_id, size, digest) in expected.items():
    asset = assets.get(name)
    if not asset or (asset["id"], asset["size"], asset["digest"], asset["state"]) != (
        asset_id, size, "sha256:" + digest, "uploaded"
    ):
        raise SystemExit(f"The existing {name} changed since approval; refusing to replace it")
PY
    [[ "$(gh api "repos/$GITHUB_REPOSITORY/git/ref/tags/$TAG" --jq .object.sha)" == "$TAG_OBJECT" ]]
}
check_original_release

# Download and verify every current asset and the release body before touching
# them. These files are also used by the rollback trap.
gh release download "$TAG" --repo "$GITHUB_REPOSITORY" --dir "$BACKUP" \
    -p patches-2.2.9.mpp -p patches-list-2.2.9.json -p SHA256SUMS
(cd "$BACKUP" && sha256sum --check SHA256SUMS)
[[ "$(sha256sum "$BACKUP/patches-2.2.9.mpp" | cut -d' ' -f1)" == "c4a1ca03317c1319caaec6fbc1dfb404bc28182196ece19e7f26b933c622447d" ]]
[[ "$(sha256sum "$BACKUP/patches-list-2.2.9.json" | cut -d' ' -f1)" == "d749bfb3601d1b402d3317effa823d839bfde465eae80fa7b9652b8ea0a90c02" ]]
[[ "$(sha256sum "$BACKUP/SHA256SUMS" | cut -d' ' -f1)" == "d0918721a59f9bcb873828d7bd66c0d37458e2263d020c19f0fae8441dd123e8" ]]
gh release view "$TAG" --repo "$GITHUB_REPOSITORY" --json body > "$WORK/notes-original.json"
python3 - "$WORK/notes-original.json" "$BACKUP/notes.md" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    body = json.load(f)["body"]
with open(sys.argv[2], "w", encoding="utf-8", newline="") as f:
    f.write(body)
PY
check_original_release
gh release view "$TAG" --repo "$GITHUB_REPOSITORY" --json body > "$WORK/notes-recheck.json"
python3 - "$WORK/notes-recheck.json" "$BACKUP/notes.md" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    body = json.load(f)["body"]
with open(sys.argv[2], encoding="utf-8", newline="") as f:
    if body != f.read():
        raise SystemExit("Release notes changed during preflight; refusing to overwrite them")
PY

cp docs/releases/v2.2.9.md "$NEW/notes.md"
cat >> "$NEW/notes.md" <<EOF

### Build and catalog

- Bundle build source: [$BUILD_SHA](https://github.com/$GITHUB_REPOSITORY/tree/$BUILD_SHA) ([successful CI run](https://github.com/$GITHUB_REPOSITORY/actions/runs/$BUILD_RUN)).
- Generated catalog source: [$CATALOG_COMMIT](https://github.com/$GITHUB_REPOSITORY/tree/$CATALOG_COMMIT).
- Existing tag source: \`6b065cc60d0a7d93e8fb7607062df65145b50c67\`; the release source archives do not contain the build fixes.
- Catalog: 167 unique entries, 30 app families (33 package targets), 26 universal entries and 14 placeholders.
- Bundle SHA-256: \`$BUNDLE_SHA256\`.
- Catalog SHA-256: \`$CATALOG_SHA256\`.
- The candidate's two DEX payloads, including all 13 required extension class definitions, were verified. This is not device testing.
EOF

release_modified=0
publication_complete=0
rollback_on_exit() {
    status=$?
    trap - EXIT INT TERM
    if [[ "$release_modified" == 1 && "$publication_complete" != 1 ]]; then
        echo "::warning::Publication failed; restoring the original v2.2.9 assets and notes."
        if ! gh release upload "$TAG" --repo "$GITHUB_REPOSITORY" --clobber \
            "$BACKUP/patches-2.2.9.mpp" "$BACKUP/patches-list-2.2.9.json" "$BACKUP/SHA256SUMS"; then
            echo "::error::Asset rollback failed; original files remain in the workflow backup artifact."
        fi
        if ! gh release edit "$TAG" --repo "$GITHUB_REPOSITORY" --notes-file "$BACKUP/notes.md"; then
            echo "::error::Release-note rollback failed; original notes remain in the workflow backup artifact."
        fi
    fi
    exit "$status"
}
trap rollback_on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# The user explicitly approved replacing all three named assets. GitHub does
# not provide an atomic multi-asset update, so verify immediately and roll back
# from the checked backup if any step fails.
release_modified=1
gh release upload "$TAG" --repo "$GITHUB_REPOSITORY" --clobber \
    "$NEW/patches-2.2.9.mpp" "$NEW/patches-list-2.2.9.json" "$NEW/SHA256SUMS"
gh release download "$TAG" --repo "$GITHUB_REPOSITORY" --dir "$VERIFY" \
    -p patches-2.2.9.mpp -p patches-list-2.2.9.json -p SHA256SUMS
cmp "$NEW/patches-2.2.9.mpp" "$VERIFY/patches-2.2.9.mpp"
cmp "$NEW/patches-list-2.2.9.json" "$VERIFY/patches-list-2.2.9.json"
cmp "$NEW/SHA256SUMS" "$VERIFY/SHA256SUMS"
(cd "$VERIFY" && sha256sum --check SHA256SUMS)
[[ "$(gh api "repos/$GITHUB_REPOSITORY/git/ref/tags/$TAG" --jq .object.sha)" == "$TAG_OBJECT" ]]
gh release edit "$TAG" --repo "$GITHUB_REPOSITORY" --notes-file "$NEW/notes.md"
gh release view "$TAG" --repo "$GITHUB_REPOSITORY" --json body > "$VERIFY/notes.json"
python3 - "$VERIFY/notes.json" "$NEW/notes.md" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    body = json.load(f)["body"]
with open(sys.argv[2], encoding="utf-8", newline="") as f:
    if body != f.read():
        raise SystemExit("Published release notes do not match the approved text")
PY
[[ "$(gh api "repos/$GITHUB_REPOSITORY/git/ref/tags/$TAG" --jq .object.sha)" == "$TAG_OBJECT" ]]
publication_complete=1
trap - EXIT INT TERM
echo "Published the verified v2.2.9 candidate; bundle SHA-256: $BUNDLE_SHA256"
