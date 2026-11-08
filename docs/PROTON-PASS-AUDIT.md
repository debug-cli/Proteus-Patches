# Proton Pass — patch status and security limitations

The catalog targets `proton.android.pass` 1.41.3 (versionCode 14103401, XAPK).
No Proton Pass APK was analyzed or patched on a device for this release. All
new entries are off by default.

## Available entries

- **Allow screenshots & screen recording** and **Block telemetry & crash
  reporting** are wrappers over shared heuristics. They can match zero sites;
  they do not guarantee capture access or complete telemetry/crash blocking.
  Allowing screen capture can expose vault contents to other software.
- **Unlock Unlimited Plan** is the existing local plan-model patch. It does not
  grant server-side entitlements. Its documented bytecode layout came from
  1.40.3, not the configured 1.41.3 target. A structural guard now rejects an
  unexpected constructor instead of injecting into an unknown layout, but it
  does not establish that 1.41.3 works. It remains off by default and uses
  signature, installer and Firebase certificate spoofing dependencies.

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

Compare the actual 1.41.3 constructor, class descriptors, register layout and
superclass chain with `ProtonPassConstructorGuard.kt` and
`ProtonPassUnlockPatch.kt`, then patch and test on a disposable account/device.
The synthetic DEX tests verify the guard mechanics only. Server-validated
features and account entitlements are outside the scope of a local patch.
