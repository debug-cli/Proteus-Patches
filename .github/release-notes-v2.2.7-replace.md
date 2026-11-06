Proteus Patches v2.2.7 (pre-release)

This v2.2.7 build supersedes the previous v2.2.7 .mpp. It is built from current main and adds the Amazon AMOLED changes below. Same version number, same pre-release status; v2.2.3 remains the stable Latest release.

What this pre-release adds and fixes:

**Amazon — Dark mode patch is now AMOLED black** (Shopping + India, same patch).
- WebView fixups paint `html`/`body` plus `header`/`footer`, the stable `#nav-belt`/`#nav-main` ids and standard `.a-cardui` cards in pure `#000000`; asin image containers stay white on purpose.
- The resource pass now also blackens neutral dark-surface `<color>` tokens (night/dark files, excluding text/icon/accent-ish names) to `#ff000000` and forces pure-black window/navigation/statusBar/`colorSurface*`/`colorBackgroundFloating` items on dark and daynight `<style>` themes (plus forceDarkAllowed). It is fail-safe on resource-layout drift: any mismatch is a logged no-op, never a build failure, and the WebView CSS half still applies. Amazon builds ship different resource layouts, so results vary by app version — watch for the `AMOLED dark theme: blackened N color(s), forced M theme item(s).` line in the patch log.

**Brave — six new opt-in patches** (ported from kveld9/kveld-morphe-patches, GPL-3.0, revision 8afd78f0b57964b9ae9a0a387bdd4ae157345707, targeting Brave v1.98.63). All default **off**; all depend on the `extractNativeLibs=true` compatibility patch from v2.2.6 (required to install patched Brave on 16 KB page-size devices). **Experimental and unverified on device** — no Brave APK was available to the author for testing; the targeted builds are the standalone `BraveMonoarm64.apk` / `BraveMonoarm.apk` v1.98.63.

- **Remove Unused AI & VPN** — stubs the unused AR/XR native libraries (Impress renderer, androidx.xr OpenXR, ARCore) to shrink the APK; option `Remove VPN library` additionally stubs `libwg-go.so` and disables Brave VPN.
- **Block Telemetry** — redirects the identified analytics, crash-upload and experiment endpoints (P3A `star-randsrv`/`collector.bsg`, `usage-ping.brave.com`, WDP `patterns`/`collector`/`star`/`quorum`, `cr.brave.com`, `crashpad.chromium.org`, `variations.brave.com`) to `0.0.0.0` by patching `libchrome.so` at fixed, pre-validated byte offsets (the patch aborts before writing anything on mismatch), strips the Play install-referrer permission, and rewrites the shopping-offers host literals (option `Block offers server`, on by default).
- **Clean New Tab** — forces the sponsored Brave news/stats preference defaults off at runtime (NTP gate preference getters/listeners and layout initialization) so sponsored surfaces do not render; option `Hide shortcut tiles` additionally forces the most-visited tiles list off.
- **Close Tab In Group Order** — preselects the previous tab in the same tab group when the active tab is closed; bulk closes, non-selected tabs, single-tab groups and session restores fall back to stock behavior.
- **Brave Origin** — turns the Brave Origin feature switches (Rewards, News, Wallet, VPN, Leo AI) on by default at the Android resource layer and neutralizes the identified policy gatekeepers and the Leo AI feature flag in bytecode so the switches stick.
- **Custom Brave theme** — rewrites the dark palette, Android system Material You role colors (`values-v31+`), tonal `lStar` luminance selectors and vector drawable background fills; defaults to pure OLED black with slate ink and Brave purple-blue accent; 13 options, colors are validated hex strings (this patcher has no color-picker option).

**Reddit**

- **Block AutoMod comments** — broader detection (plain `getKind()`/`getType()`, a wider author getter set) plus a canonical AutoModerator bot-footer fallback when the author getter cannot be resolved; a one-time `filter is live` probe line and throttled drop counters in logcat (`[Proteus][Block AutoMod]`) make the hook observable on-device.
- **Show deleted comments** — outcome logging for every path (rows found, archive ids returned, restored vs. model-rewrite-failed vs. no-archived-copy), so a silent `[deleted]` is diagnosable with `adb logcat | grep -i proteus`. Very recent deletions (hours–days) are often not indexed by the archives yet and will stay `[deleted]`; see the manual coverage check in `docs/REDDIT-DELETED-COMMENTS.md`.
- **Performance** — the three `Listing.children` helpers no longer redo a full reflective traversal of the row list on every `getChildren()` read: results are memoized per list instance (identity-keyed, size-guarded LRU, bit-identical output). Fixes the animation/scroll lag reported on patched builds. See `docs/REDDIT-PERFORMANCE.md`. Patch behavior, fingerprints and default-off states are unchanged.

**Verification**

- Built and verified with the Morphe Manager v2.3.0 patcher; APK zip integrity and patch-source metadata verified (`verify_android_bundle.py`, `verify_patch_source.py`); `patches-list.json` (161 patches total); `:patches:testBravePrivacy` and the Pinterest video URL tests pass on CI. Main is green on both CI workflows.
- Nothing in this release has been exercised on a real device; treat every patch as unverified until you test it. The Brave fingerprints and `libchrome.so` offsets are pinned to v1.98.63 standalone APKs.
- v2.2.3 remains the stable Latest release; this is a pre-release.

Known limitations: the Reddit comment-thread patches only cover rows rendered through the `Listing.children` choke point; the Amazon resource AMOLED pass is best-effort on resource-layout drift (fail-safe to the previous behavior plus black CSS); Custom Brave theme rewrites the dark palette only. See `docs/BRAVE.md`, `docs/REDDIT-DELETED-COMMENTS.md` and `docs/REDDIT-PERFORMANCE.md`.
