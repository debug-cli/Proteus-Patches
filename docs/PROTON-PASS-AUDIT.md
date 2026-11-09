# Proton Pass — patch status and security limitations

The source catalog lists `proton.android.pass` 1.41.3 (versionCode 14103401)
and, experimentally, **1.41.5** (version-name only, XAPK). The 1.41.5 target
is based on the upstream source, not on an inspected APK; no Proton Pass APK
was analyzed or patched on a device for these versions. Manager compatibility
metadata does not establish that any patch works. All new entries are off by
default. The v2.2.9 release notes identify the exact bundle build; earlier
v2.2.9 downloads do not include the experimental 1.41.5 target.

## Available entries

- **Allow screenshots & screen recording** and **Block telemetry & crash
  reporting** are wrappers over shared heuristics. They can match zero sites;
  they do not guarantee capture access or complete telemetry/crash blocking.
  Allowing screen capture can expose vault contents to other software.
- **Unlock Unlimited Plan** is the existing local plan-model patch. It does not
  grant server-side entitlements. Its documented bytecode layout came from
  1.40.3. A source-derived alternative fingerprint recognizes the 1.41.5
  source's `PlanType` parameter, but the actual APK may differ. Structural
  guards reject unexpected constructor layouts or missing/incompatible injected
  classes and members; they do not establish that 1.41.3 or 1.41.5 works. It
  remains off by default and uses signature, installer and Firebase certificate
  spoofing dependencies.

## Unimplemented entries

The following seven entries are **placeholders**. They do not implement the
stated behaviors, and selecting any of them deliberately fails rather than
reporting success. App-specific bytecode analysis and testing are required.

- Remove upgrade nag UI
- Disable auto-lock / extend timeout
- Disable clipboard auto-clear
- Unlock multiple vaults
- Force biometric always-on
- Unlock password-generator options
- Dark web monitoring local-only (cosmetic)

The dark-web placeholder performs **no breach monitoring** and must not be
interpreted as evidence that an account is safe. Any future cosmetic display
must not claim that an account has no breaches without an actual check.

For reference, the upstream 1.41.3 source already includes a **Never** clipboard
clearing option; availability may vary by app configuration. Disabling clipboard
clearing can expose copied credentials. See the
[upstream preference definition](https://github.com/protonpass/android-pass/blob/1.41.3/pass/preferences/api/src/main/java/proton/android/pass/preferences/ClearClipboardPreference.kt).

## Verification required

Compare the actual 1.41.5 constructor, class descriptors, register layout and
superclass chain with `ProtonPassConstructorGuard.kt` and
`ProtonPassUnlockPatch.kt`, then patch and test on a disposable account/device.
The synthetic DEX tests verify the guard mechanics only. Server-validated
features and account entitlements are outside the scope of a local patch.
