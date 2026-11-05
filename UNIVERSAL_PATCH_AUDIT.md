# Universal patches — correctness & UX audit

Scope: the 24 patches under `patches/src/main/kotlin/app/template/patches/shared/`
that appear in the **Universal** section of the patch list. These patches are offered
for every app, so a bug here affects every user who enables them.

This pass covered two things:

1. **Correctness** — invalid DEX, register clobbering, wrong constants, broken
   register matching, missing manifest rewrites, and patch-time crashes.
2. **Usability** — option validators that reject blank/expected input, misleading
   error text, and documentation that no longer matched the implemented behaviour.

> Verification status: **static only**. No JDK, Android SDK, Gradle cache or
> `read:packages` token was available in the audit environment, so
> `./gradlew buildAndroid` could not be run. Every change was reviewed against the
> smali encoding rules and the patcher APIs used elsewhere in the repo. Run the
> normal CI build before release.

## Fixes by patch

| # | Patch | File | Result |
|---|-------|------|--------|
| 1 | Allow screenshots & screen recording | `privacy/AllowScreenCaptureUniversalPatch.kt` | Flag literal rewrite now uses a register-width-aware constant. |
| 2 | Block analytics & tracking SDKs | `privacy/BlockAnalyticsSdksPatch.kt` | Register-width-aware constants; tracker calls that return a value are still skipped. |
| 3 | Block cleartext (HTTP) traffic | `manifest/BlockCleartextTrafficPatch.kt` | Reviewed — no defect. |
| 4 | Block screenshots & screen recording | `privacy/BlockScreenCapturePatch.kt` | **Fixed:** the Window object register was overwritten with `FLAG_SECURE` before `setFlags`, so the call was invalid. `FLAG_SECURE` was also emitted in decimal (`0x8192`) instead of hex (`0x2000`). Both corrected. |
| 5 | Block user-added certificate authorities | `manifest/BlockUserAddedCAsPatch.kt` | Reviewed — missing config / missing XML is reported and left stock. |
| 6 | Change package name (clone app) | `manifest/UniversalChangePackageNamePatch.kt` | `uses-permission-sdk-m` is now renamed alongside `uses-permission`/`-sdk-23`. |
| 7 | Disable PairIP license check | `PairIp.kt` | **Rewritten:** type-correct `neutralize()` (the old code wrote `return-void`/`return-object v0` into methods with other return types), missing classes/fields/enums are skipped, `performLocalInstallerCheck` returns `true`, and only void `VMRunner.invoke` call sites are cleared. |
| 8 | Disable app backup | `manifest/DisableAllowBackupPatch.kt` | Reviewed — no defect. |
| 9 | Empty telephony identifiers | `privacy/EmptyTelephonyIdsPatch.kt` | Reviewed — only `MOVE_RESULT_OBJECT` after `TelephonyManager` getters is rewritten. |
| 10 | Fix Firebase after re-signing | `firebase/SpoofFirebaseCertHashPatch.kt` | String-match lookup no longer throws when absent; unexpected instruction format produces a clear error; error text now points at the certificate patch instead of a removed option. |
| 11 | Force not debuggable | `manifest/ForceNotDebuggablePatch.kt` | Reviewed — no defect. |
| 12 | GmsCore support (MicroG) | `gms/GmsCoreSupportPatch.kt` | **Fixed:** `<queries>` is inserted through the DOM whenever it is missing (previously only appended to an existing `<queries>` and only as text); text rewrites now run after the DOM is serialised; vendor-group metadata keys are correct for full package names; c2dm prefix matches the DEX rewrite; blank optional values validate; activity suffix is normalised. |
| 13 | Hide developer options & USB debugging | `privacy/HideDeveloperOptionsPatch.kt` | **Rewritten:** the old code patched `Settings.get*` calls near an ADB key even when they read a different setting. It now checks that the call actually consumes the key register and only patches real replacements. |
| 14 | Lock down exported components | `manifest/LockExportedComponentsPatch.kt` | Reviewed — components with intent filters (launcher, deep links) are never touched. |
| 15 | Prefer external browser over Custom Tabs | `misc/ExternalBrowserPatch.kt` | **Fixed:** the old rewrite clobbered the `CustomTabsIntent` receiver, reused the URL register for the parsed `Uri`, and patched while iterating a shifting instruction list. Now allocates fresh scratch registers, skips the site when none are free, and applies replacements bottom-up. |
| 16 | Provide Original app certificate | `cert/CertExtractor.kt` | **Fixed:** manual values and a user-supplied original APK now take precedence over auto-extraction (the common “APK is already re-signed” case). Base64-only input works, whitespace is tolerated, blank manual fields validate, and SHA-only input is no longer ignored. |
| 17 | Remove dangerous permissions | `manifest/RemoveDangerousPermissionsPatch.kt` | `uses-permission-sdk-m` is removed too; a blank custom list falls back to the defaults instead of removing nothing. |
| 18 | Spoof Widevine / DRM level | `drm/SpoofDrmPatch.kt` | **Rewritten:** the property name is resolved from the actual argument register (the old fallback spoofed every `getPropertyString` call, including unrelated properties); unused fingerprint byte-arrays removed; security-level fingerprint restricted to accessor methods. |
| 19 | Spoof app signature | `signature/SpoofSignatureVerificationPatch.kt` | **Fixed:** the described `signature` (Base64 DER) option was missing from the patch; added, validated, whitespace-tolerant, and resolved before auto-extraction. Package-name validator accepts blank. |
| 20 | Spoof install source | `installer/SpoofInstallSourcePatch.kt` | Register-width-aware constant for `getPackageSource()`. |
| 21 | Strip logging | `misc/StripLoggingPatch.kt` | **Fixed:** the reported count was computed with a wrong formula; it now counts each removed `Log` call once. The `move-result` rewrite is register-width-aware. |
| 22 | WebView: block mixed content | `web/WebViewMixedContentPatch.kt` | **Fixed:** `MIXED_CONTENT_NEVER_ALLOW` is `1`, not `2` (`2` selects compatibility mode). Every non-`NEVER_ALLOW` literal is rewritten, not only `0`. |
| 23 | WebView: disable local file access | `web/WebViewFileAccessPatch.kt` | Register-width-aware constant. |
| 24 | WebView: remove JavaScript interface objects | `web/RemoveJsInterfacesPatch.kt` | Reviewed — only void `addJavascriptInterface` calls are NOPed. |

## Cross-cutting changes

- **`shared/SmaliConstants.kt` (new)** — `constValue(register, value)` emits
  `const/4`, `const/16` or `const` depending on the register number and literal.
  Several patches previously emitted `const/4 vX, …` for arbitrary registers;
  `const/4` only encodes `v0`–`v15`, so a large method could make the smali
  assembler reject the whole patch.
- **Certificates** — `extractApkCertificatePatch` is the single source used by the
  GmsCore, Firebase and signature patches. Manual values and an explicitly provided
  original APK are now authoritative.
- **README** — universal table updated for the new `Spoof app signature`
  certificate option and the corrected `Disable PairIP license check` description.
  The table is regenerated from `patches-list.json` at release time; this edit keeps
  the repository view in sync until the next release.

## Known limitations (unchanged)

- Heuristic patches (analytics, exported components, external browser, DRM) still
  depend on recognisable SDK / call-site shapes. They are fail-safe: unrecognised
  sites are skipped rather than rewritten.
- `Spoof Widevine` does not defeat server-side attestation.
- GmsCore support does not cover Android 14+ Credential Manager sign-in or Play
  Integrity.
- Cloned/re-signed apps can still be rejected by signature-bound services.

## Recommended verification before release

1. `./gradlew buildAndroid` with a valid `GITHUB_TOKEN` (`read:packages`).
2. Patch one small APK with each bytecode universal patch enabled and launch it
   (`Block screenshots`, `Hide developer options`, `Empty telephony IDs`,
   `Strip logging`, `WebView` set, `Spoof DRM`, `External browser`).
3. Patch an app that uses PairIP and confirm it starts.
4. Patch an already re-signed APK and confirm manual certificate values are used by
   the GmsCore/Firebase/signature patches.
