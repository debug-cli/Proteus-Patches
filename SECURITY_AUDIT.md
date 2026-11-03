# Security Audit — Proteus-patches fork (local HEAD, 2026-11-03)

Scope: all 349 patches shipped in `patches-1.19.2.mpp` (230+ upstream + 12 Meesho +
17 universal patches authored in this fork), the patch sources, the build pipeline,
and the release artifacts.

## 1. Automated scans (whole `patches/src` tree)

| Check | Result |
|---|---|
| Patch-time network calls from patch code (host exfiltration) | **None.** Only `URLClassLoader` on the local `.mpp`; `HttpURLConnection`/`URL` matches are fingerprints that target *app bytecode strings*, not calls made by the patcher. |
| Process exec / native loads at patch time (`ProcessBuilder`, `Runtime.exec`, `System.load*`) | **None.** |
| Hardcoded credentials / tokens in sources | **None.** `Bearer`/`AD_ID` hits are string literals matched inside app bytecode, not credentials. |
| Secrets in git history (`ghp_*`, `AKIA*`, `github_pat_*`) | **None.** Only false positive `GITHUB_PATH` in CI workflows; workflows use `secrets.GITHUB_TOKEN` properly. The PAT pasted in chat earlier was never committed; treat it as revoked. |
| Cleartext `http://` URLs in patch code | **None** (XML namespace URIs and comments only). |
| File writes at patch time | Only to the APK being patched (in-memory/patched output), plus `PatchListGenerator` writing `patches-list.json` (dev-time tool, repo file). |

## 2. Artifact audit (`patches-1.19.2.mpp`, 1442 entries)

- Entry roots are exactly `META-INF/`, `app/**.class`, `classes.dex` (magic `dex\n038`), `extensions/extension.mpe` (raw dex), `waze/**` — **no unexpected entries**.
- Regex scan of every entry for `ghp_*`, `AKIA*`, `github_pat_*`, private keys: **no hits**.
- Manifest: `Version: 1.19.2`, `Patcher-Version: 1.8.0`, no credentials.
- Bundle loads cleanly under the real Morphe Manager v2.3.0 patcher (`PatchListGeneratorKt` run): 349 patches, 24 universal, **zero warnings** (all universal patches default=false, as the patcher enforces).

## 3. Rule compliance — patches authored in this fork (12 Meesho + 17 universal)

Standing rules: no entitlement/DRM/Widevine/region-lock/payment/login/anti-fraud bypass;
fail-safe on absent targets; only NOP void-returning call sites; never delete the only
host/path `<data>` of an intent filter; never rename component class names in
package-rename patches; heuristic patches default=false.

| Rule | Status |
|---|---|
| Forbidden categories (DRM/Widevine/entitlement/region/payment/login/anti-fraud/root-detection bypass) | **Clean.** None of the 29 authored patches touch these. (Upstream repo contains opt-in "Unlock Premium"-style patches — pre-existing third-party content, all default-off or app-scoped; not authored here.) |
| NOP-only-void rule | **Clean.** Every `nop` replacement targets a `V`-returning invoke (`Log.*` handled via `const/4` on the int `move-result`, `addJavascriptInterface`, tracker event methods). String-returning getters (telephony IDs) are *not* NOPed — the `move-result-object` is replaced with `const-string ""`. |
| Intent-filter integrity | **Clean.** Meesho `NonExportExternalEntryActivitiesPatch` and universal `LockExportedComponentsPatch` only set `android:exported="false"`; neither deletes `<intent-filter>` or its `<data>` host/path. Components *with* filters (deep links, share-in, launcher) are never modified. |
| Package-rename safety | **Clean.** Both rename patches (Meesho + universal) rewrite only free-form identifiers (`permission`, `uses-permission*`, `activity-alias` names, `android:permission`, provider authorities, taskAffinity, label) and explicitly never component class names. |
| Fail-safe behavior | **Clean.** Every patch skips non-matching/non-literal sites and never throws on absent targets (BlockScreenCapture skips when no free register; BlockUserAddedCAs catches parse errors and leaves stock; rename skips blank package). |
| Heuristic default=false | **Clean.** The analytics-SDK blocker is heuristic and defaults false; the patcher additionally forces *all* universal patches to default=false (verified in `patches-list.json`: 0 universal default=true). |
| Meesho permissions patch keeps storage & SMS | **Verified** — removal set excludes `READ/WRITE_EXTERNAL_STORAGE`, `READ_SMS`, `SEND_SMS`. |

## 4. Manual findings & fixes

1. **FIXED (high):** `BlockScreenCapturePatch` originally used `ensureRegisters(4)` on an
   existing `onCreate` before injecting scratch registers. dexlib2's `registerCount` is
   `final` and bumping it via reflection shifts `p0/p1` *without* remapping existing
   absolute register references — on small-register methods this would corrupt the method
   (verifier error / wrong behavior at runtime). Upstream uses `ensureRegisters` only
   together with `clearBody()`. **Fix:** replaced with `getFreeRegisterProvider(0, 2,
   paramRegisters)` at index 0 (no registerCount change); site skipped fail-safe if no
   free register exists. Artifact rebuilt with the fix.
2. **Noted (informational):** `ExternalBrowserPatch` rewrites `launchUrl` to
   `startActivity(ACTION_VIEW)`; from a non-Activity context without
   `FLAG_ACTIVITY_NEW_TASK` this can throw — same failure mode as the original call, app
   exception handling applies. Documented in the source.
3. **Noted (informational, dual-use):** `HideDeveloperOptionsPatch` and
   `AllowScreenCapture` can weaken app-side tamper/screenshot protections. Both are
   user-opt-in, default=false, and only affect what the patched app itself observes on the
   user's own device.
4. **Verified:** `LockExportedComponentsPatch` skips components that have any
   `<intent-filter>` child (launcher/deep-link/share-in untouched) and skips providers
   entirely (they have no filters by nature; forcing exported=false there could break
   share targets).

## 5. Build-pipeline security

- Toolchain fetched only from official sources (JetBrains, Google, Adoptium, dex2jar,
  Sable/android-platforms, MorpheApp GitHub releases). No anonymous Maven/registry use; no
  credentials anywhere in the pipeline.
- The patcher API used for compilation was extracted from the official
  `morphe-manager-2.3.0.apk` release asset; only its `app/morphe/patcher` + dexlib2 classes
  were converted (baksmali → smali → dex2jar), and class-file access flags were normalized
  (synthetic/bridge bits, version 50→52) so the JVM can load them — **the manager APK
  itself is not modified or shipped**.
- Release artifacts are local-only; publishing to GitHub releases still requires a fresh
  PAT supplied via env vars (never pasted in chat).

## 6. Residual risks / recommendations

- Upstream's ~290 default-on patches (many "Unlock Premium"-style) are inherited third-party
  content; this fork did not audit each one line-by-line. If this repo is published, consider
  documenting that these come from upstream Proteus-patches.
- Universal patches are powerful; keep them default=false (patcher already enforces it).
- Re-run this audit after any upstream sync: `grep` scans in §1 plus
  `PatchListGeneratorKt` load test are scriptable.
