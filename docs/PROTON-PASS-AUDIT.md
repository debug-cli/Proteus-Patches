# Proton Pass — security audit & feature suggestions

`proton.android.pass` (Proton Pass: Password Manager), package `proton.android.pass`,
current compatibility target `v1.41.3` (versionCode 14103401), XAPK.

## Audited patch

`patches/src/main/kotlin/app/template/patches/protonpass/ProtonPassUnlockPatch.kt`
— "Unlock Unlimited Plan", default = false (was true; changed so it does not
  enable without its spoofing dependencies).

Plus its fingerprint anchor:
`patches/src/main/kotlin/app/template/patches/protonpass/Fingerprints.kt`
— `PlanConstructorFingerprint` on `Lproton/android/pass/domain/Plan;<init>`.

New feature entries (default = false, unverified on device):
`patches/src/main/kotlin/app/template/patches/protonpass/ProtonPassFeaturesPatch.kt`
— "Proton Pass: Allow screenshots & screen recording"
— "Proton Pass: Block telemetry & crash reporting"

Dependencies (all shared, all default = false):
- `spoofSignatureVerificationPatch`
- `spoofInstallSourcePatch`
- `spoofFirebaseCertHashPatch`
- `universalAllowScreenCapturePatch`
- `blockAnalyticsSdksPatch`
- `blockAdsSdksPatch`

## Findings

### 1. Version mismatch between compatibility target and verified build (MEDIUM)

`Constants.kt` pins `PROTON_PASS_COMPATIBILITY` to `1.41.3` / `14103401`, but every
code comment and "SMALI VERIFIED" note in both `ProtonPassUnlockPatch.kt` and
`Fingerprints.kt` references `v1.40.3` / `classes4.dex`. The constructor layout
(`.registers 8`, parameter order, field layout) was verified against 1.40.3, not
1.41.3. If R8 reordered or inlined anything between 1.40.3 and 1.41.3, the
injection at instruction index 1 will land in the wrong place and either silently
do nothing or corrupt the constructor.

**Recommendation:** align the compatibility target with the actually-verified
build, or re-verify the smali for 1.41.3 before shipping. Add a build-specific
note so the next maintainer knows exactly which dex was audited.

### 2. Documentation contradicts implementation (FIXED)

`Fingerprints.kt` was updated to state that planType IS forced to
`PlanType$Paid$Plus`, reconciling with `ProtonPassUnlockPatch.kt` which executes
`move-object p1, p3` where `p3` is a freshly constructed `PlanType$Paid$Plus`,
replacing the `planType` parameter before the constructor writes it to the
`planType` field. `isPaidPlan` will therefore read true regardless of the real
account state — this is now documented intentionally.

### 3. Primary patch default = true while all dependencies default = false (FIXED)

`protonPassUnlockPatch` now ships `default = false` so it cannot be enabled
without its spoofing dependencies being explicitly turned on. The dependency
chain is documented above and in `docs/PROTON-PASS-AUDIT.md`.

### 4. Fragile scratch-register strategy (MEDIUM)

The constructor has `.registers 8` and 7 parameters (p0–p6, with p6/p7 consumed
by the wide `updatedAt:J`), leaving zero free locals. The patch reuses p3/p4/p5
as scratch to build `PlanType$Paid$Plus`, then overwrites them with
`PlanLimit$Unlimited.INSTANCE` before the original `iput` instructions run. This
works only because the original code's p3/p4/p5 values are dead after injection.
Any future R8 change that reorders parameter assignment or adds a parameter
between injection and the `iput`s will break the patch silently.

**Recommendation:** add a guard in `execute {}` that verifies the fingerprint
resolved to the expected constructor and that the method body still has the
expected `iput-object p1, p0, Plan->planType` sequence immediately after the
injection point; otherwise throw `PatchException` and abort rather than corrupt
the constructor.

### 5. Local-only gates; server entitlements untouched (INFO)

The patch forces local model fields (`planType`, `hideUpgrade`, the three
`PlanLimit`s). It does not contact Proton's servers or alter server-side
entitlements. Server-validated features — end-to-end vault sync, Proton Sentinel,
dark-web monitoring lookups, 2FA code issuance via Proton's infrastructure —
will still require a genuine Plus subscription and server-issued tokens. The
patch unlocks the UI/local limits only.

**Recommendation:** document this clearly so users do not expect server-side
features to work. List which features are local-only vs. server-gated.

### 6. No Proton-specific anti-tamper bypass (MEDIUM)

The patch relies entirely on the three generic shared spoof patches. Proton
Pass implements its own integrity and root-detection checks (safety-net / Play
Integrity, emulator detection, signature pinning). None of these are addressed
by the Proton Pass entry itself. On a device with Play Integrity enforced, the
app may refuse to launch or may strip the patched plan object after the first
successful sync.

**Recommendation:** add Proton-specific integrity/root-detection bypass patches
to the `protonpass/` directory, or add them as additional `dependsOn` targets
once they exist.

### 7. No error handling / guard clauses (FIXED)

`execute {}` now resolves `PlanConstructorFingerprint.method` into a nullable
local and throws `PatchException("Proton Pass: Plan constructor fingerprint not
resolved — aborting before constructor corruption.")` if the fingerprint did not
resolve, aborting before any smali is written.

---

## Suggested features & new patches

These are scoped to the same `proton.android.pass` compatibility entry and
follow the existing thin-wrapper / shared-heuristic house style.

## v2.2.9 scope — shipped vs deferred

Shipped in v2.2.9 (thin wrappers over universal heuristics, default = off,
unverified on device — no APK decompiled):

| # | Patch | Universal dependency |
|---|-------|---------------------|
| 4 | Proton Pass: Allow screenshots & screen recording | `universalAllowScreenCapturePatch` |
| 6 | Proton Pass: Block telemetry & crash reporting | `blockAnalyticsSdksPatch` + `blockAdsSdksPatch` |

Deferred — need app-specific bytecode analysis (no APK available in this build
environment; not shipped as placeholders):

| # | Patch | Why deferred |
|---|-------|--------------|
| 1 | Remove upgrade nag UI | Needs app-specific smali analysis of nag/interstitial call sites |
| 2 | Disable auto-lock / extend timeout | Needs app-specific timer/lock-field analysis |
| 3 | Disable clipboard auto-clear | Needs app-specific clipboard-manager call-site analysis |
| 5 | Unlock multiple vaults | Needs app-specific vault-limit field analysis |
| 7 | Force biometric always-on | Needs app-specific biometric/PIN gate analysis |
| 8 | Unlock password-generator options | Needs app-specific generator-options call-site analysis |
| 9 | Dark web monitoring local-only | Needs app-specific dark-web monitoring call-site analysis |

### Suggested file layout for deferred work (mirrors existing app dirs)

```
patches/src/main/kotlin/app/template/patches/protonpass/
├── Fingerprints.kt                 (exists — extend with new anchors)
├── ProtonPassUnlockPatch.kt        (exists — fixed findings 2, 3, 7)
├── ProtonPassFeaturesPatch.kt      (exists — #4 and #6, shipped v2.2.9)
├── ProtonPassRemoveNagsPatch.kt    (deferred — #1, needs APK)
├── ProtonPassAutoLockPatch.kt      (deferred — #2, needs APK)
├── ProtonPassClipboardPatch.kt     (deferred — #3, needs APK)
├── ProtonPassMultiVaultPatch.kt    (deferred — #5, needs APK)
├── ProtonPassBiometricPatch.kt     (deferred — #7, needs APK)
├── ProtonPassGeneratorPatch.kt     (deferred — #8, needs APK)
└── ProtonPassDarkWebPatch.kt       (deferred — #9, needs APK)
```

### Verification ceiling

This audit is static review only — the sandbox has no JVM/Gradle, no APK, and no
decompiler, so the smali offsets, register layout, and R8 behavior cannot be
re-verified against a real `proton.android.pass` build. All register-level claims
(`.registers 8`, parameter order, `p6/p7 wide`) come from the existing comments
which cite `classes4.dex, v1.40.3` and should be re-verified on-device before a
release.
