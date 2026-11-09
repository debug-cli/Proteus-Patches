# Mereframe — patch status, Pro investigation and crash triage

Mereframe (`app.unnamed.rawrecording`, pinned target **0.8.4 / vc 118**) contains a
self-defense layer that rejects re-signed builds, plus a native integrity check
inside the camera native pipeline (`libunnamed_raw.so`). This document tracks the
Pro report separately from the camera crash investigation.

**Release status:** the Pro safety checks and entitlement trace described below
are changes to this source branch, not the existing v2.2.9 release assets. The
published bundle still contains the older Pro patch and its older description;
no new bundle has been published as a result of this investigation.

## Patch inventory (published bundle and source branch)

| Patch | Default | Role |
|---|---|---|
| Allow re-signed/sideloaded installs | **on** | Neutralizes the startup install-legality verdict → app starts at all |
| Bypass native integrity checks | **on** | NEW (v2.2.9 re-cut): routes the camera session JNI call through a real JVM try/catch in the extension and substitutes {0L,1L} on failure |
| Diagnostic crash log | off | Writes the full stack to `Downloads/mereframe-crash-*.txt`, then chains to the normal crash path |
| Unlock Pro features | off | Attempts to drop one branch in `nv.e`; Pro access is **not verified**, and issue #5 reports a purchase prompt. Capture also reportedly crashed with this patch on one realme/Android 16 device |
| Mereframe: Trace Pro entitlement (diagnostic) | off | **Source branch only.** Read-only static DEX trace in patching output; it does not unlock Pro or collect runtime data |
| Fix Firebase after re-signing (universal) | off | Needed if Firebase services break after re-signing |

## Pro features — issue #5 remains unresolved

The original [issue #5](https://github.com/ineednotitle/Proteus-Patches/issues/5)
patching log is for Mereframe **0.8.4 / vc 118** and Proteus **2.1.2**. It says
`Unlock Pro features` succeeded, but that only proves a patch ran. A later comment
reports a purchase prompt when trying a Pro feature; it does not identify the
exact feature or establish the bundle used for that later attempt. The issue's
native-integrity camera crash is a separate symptom. Neither the app's APK nor
a device was available for this investigation.

The existing patch replaces one `if-eqz` after a `contains(Object)Z` call in
`Lnv;->e(Context, Sv, List)Set`; the original analysis called it an ownership
check. It does **not** change the separate `Ru.a()Z` availability check described
in that analysis. Whether that check, another path, or something else explains
the prompt is unknown. No entitlement fix or actual Pro feature has been
verified. Hardware limitations can also affect some camera features independently
of purchase status.

The source branch now requires exactly one documented `ArrayList.contains`
call, checks the `move-result` and `if-eqz` register match, and fails on an
ambiguous or unexpected layout. This is a **safety improvement, not a Pro
unlock fix**: the checks do not establish what the selected branch means.
Only synthetic DEX tests cover the new guard; compatibility with the real
0.8.4 APK and device still needs verification.

### Optional static trace without sharing the APK

When an original 0.8.4 / vc 118 APK or split set is available locally, a bundle
built from this source branch can collect leads without uploading the APK:

1. Start from the **original, unmodified** target. Select **Mereframe: Trace Pro
   entitlement (diagnostic)**. Leave **Unlock Pro features** and other modifying
   patches off for this diagnostic run, so the trace describes the input rather
   than a potentially modified method. The diagnostic does not need the output
   APK to be installed or opened.
2. Run the patcher and save its patching log. Review and share only lines
   containing `MEREFRAME_PRO_TRACE`, plus the app version/versionCode, bundle
   version, and the name of the specific Pro feature that shows the prompt.
   Do not share accounts, purchases, personal photos, or the full device log.
3. The trace records bounded instruction/branch information and DEX method,
   field and type identifiers. String literals, most numeric literals, runtime
   entitlement state and user content are not collected. The surrounding patcher
   log can contain device or file-path details, so review it separately.

The trace is a static direct-reference search; it does not cover reflection,
native code or execution at the paywall. A missing or ambiguous resolver is
reported explicitly. The output gives leads, not proof of a gate or a fix. The
released v2.2.9 bundle does **not** contain this diagnostic.

## 2026-11-07 (later same day) — issue: v2.2.9 re-cut #2 fails at PATCH time (FIXED in v2.2.9 re-cut #3)

On-device Manager 2.5.0 / Patcher 2.3.0 report:
`app.morphe.patcher.patch.PatchException: Collection is empty`, caused by
`NoSuchElementException` at `InlineSmaliCompiler.compile` via
`addInstructions` in `BypassNativeIntegrityPatch.kt`. Root cause: the injected
`invoke-static {p0, p1, p2, p3, p4, p5}` exceeds the five-register limit of
the regular invoke-static opcode form; the inline compiler's dummy method
failed to build, `methods` came back empty, `.first()` threw. Fix: the call
now uses `invoke-static/range {p0 .. p5}` (six consecutive registers), the
same escape hatch the original wrapper used. No other patch in the bundle
injects more than five source registers.

## 2026-11-07 — issue #5: the bypass never caught anything (FIXED in v2.2.9 re-cut #2)

Field report ("debug-cli"): patch reports success but the
`SecurityException("app integrity check failed")` still kills the app at
`B1e6ad8077f6880e309d8da4b.d(ZZZZFZ)[J`.

Root cause: the inline smali tried to express a try/catch across the JNI
invoke with `:try_start_0 / :try_end_0 / .catch Ljava/lang/SecurityException;`.
Morphe's inline-smali compiler compiles such snippets against a dummy method
and splices back only the instruction list — the `.catch` lives in the code
item's exception table, not in the instruction list, so it was silently
dropped. No handler → the native throw escaped → crash at `d(SourceFile:8)`.

Fix (this build): the whole call site now lives inside
`MereframeIntegrityHook.openSessionWithFallback(ZZZZFZ)[J` in the extension —
a real JVM try/catch. The injected smali is `invoke-static` →
`move-result-object` → `return-object` with no extra registers. The hook
resolves the bridge class/singleton/native method reflectively (one place to
change per build shape), validates the native verdict's domain `(0,4)` exactly
like the previous iot fallback, degrades to `long[]{0L,1L}` on ANY
mismatch/throw, and logs once per process.

Two related defects from the same report were fixed as well:

- **Spoof install source, Layer 2 never found the app.** The fingerprint
  required a DIRECT subclass of `android.app.Application`; modern apps extend
  an androidx parent (e.g. `androidx.lifecycle.LifecycleProcess`) → silent
  skip. The patch now walks each candidate class' superclass chain inside the
  merged DEX (bounded, null-safe). Loud log only when truly absent.
- **Spoof app signature reported success while hooking nothing.** The failure
  mode is now impossible to ship silently: the bundle verifier
  (`.github/scripts/verify_android_bundle.py`) asserts all 13 extension
  classes survive in every DEX payload of `patches-2.2.9.mpp` —
  `SignatureHookApp` included — and fails the build otherwise.

24-hour validity of the fix: compile-time only (no device log yet for the
new code path). If the crash reappears at this exact frame, check the patching
log for the new "session wrapper routed through MereframeIntegrityHook" line:
if missing, that build lacks this patch.

## Reading the 2026-11-07 crash report (realme RMX5110, Android 16)

```
java.lang.SecurityException: app integrity check failed
  at app.unnamed.rawrecording.b.B1e6ad8077f6880e309d8da4b.nd2cccb916f63efbcf8b5b19b(Native Method)
  at app.unnamed.rawrecording.b.B1e6ad8077f6880e309d8da4b.d(SourceFile:8)
  at Jm.m(SourceFile:27) …
```

This is **precisely the frame "Bypass native integrity checks" wraps**
(`Lapp/unnamed/rawrecording/b/B1e6ad8077f6880e309d8da4b;->d(ZZZZFZ)[J`). With the
bypass compiled in, the `SecurityException` is caught *inside* `d` and the
substitution result (`long[]{0L, 1L}`) is returned — the JVM never unwinds, the
log file never gets written. **The existence of any such log file therefore
proves the bypass was not effective in the crashing build.**

So the field outcome says one of these happened:

1. The installed Mereframe APK is **not 0.8.4 (vc 118)** → the manager treats
   all Mereframe patches as incompatible and excludes them (the app still
   starts because "Allow re-signed installs" would also be excluded → check
   whether the app even reached the camera, or crashed at first frame — if
   the refusal screen shows, the gate ran anyway).
   → Send the exact `versionName`/`versionCode` from Settings → Apps →
   Mereframe. The crash log now (after the 2026-11-07 re-cut) prints it as
   `app:` in the header line.
2. The bundle in the manager is **stale** (an older .mpp that predates the
   bypass). → Remove the bundle, re-add it, and confirm it reports **v2.2.9**.
3. The bypass did not apply or a different build was used.
   → Current patching output should contain `Bypass native integrity: session
   wrapper routed through MereframeIntegrityHook`. If that line is absent,
   inspect the patch selection and patching output; absence alone does not
   establish which failure occurred.
4. The user manually unchecked "Bypass native integrity checks".

## Required device checklist before reporting a crash again

1. Manager shows the published bundle as **v2.2.9**.
2. Mereframe target shows **0.8.4 (vc 118)** as supported. If not, STOP —
   no Mereframe patch will apply; send the version.
3. Patches checked: **Allow re-signed/sideloaded installs** + **Bypass
   native integrity checks** + (only if this is a diagnostic run) **Diagnostic
   crash log**. **Unlock Pro features stays OFF.**
4. Check the patching log contains: `Bypass native integrity: session wrapper
   routed through MereframeIntegrityHook`.
5. If it still crashes: attach the newest `Downloads/mereframe-crash-*.txt`
   (it now contains `app: versionName=… versionCode=…`) — that header tells
   us which build actually ran and which patches were in it.

## AMOLED resource fix (Amazon — same session)

The Amazon report (`Unknown attribute name: <item name="colorSurface">#ff000000</item>`)
was the AMOLED theme pass writing **Material library attribute names** into
`<item>` elements — the reandroid resource encoder cannot re-resolve those and
aborts the whole build. Theme-item writes are now restricted to `android:`
framework attributes (a hard guard also drops any later non-framework additions);
the Material surfaces go black via the `<color>`-token pass and the WebView CSS
instead. Amazon builds after the 2026-11-07 re-cut should now produce a valid
package. AMOLED on Amazon remains unverified on device.
