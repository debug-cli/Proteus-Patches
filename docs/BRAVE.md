# Brave patches (experimental and incomplete)

These local, opt-in patches target `com.brave.browser` version **1.98.63**,
the version targeted by the reference project. This is a provisional target,
not a claim that this bundle has been tested with that APK. We could not access
the official release APK in this environment and have not patched it or tested
on an Android device. Do not use this on a primary browser installation until
it has been validated. Back up data first; installation of a re-signed APK may
require uninstalling Brave and will not preserve its app data automatically.
The reference project supports the standalone ARM64 and ARM32 APKs, not universal
builds. Release assets:
[ARM64](https://github.com/brave/brave-browser/releases/download/v1.98.63/BraveMonoarm64.apk),
[ARM32](https://github.com/brave/brave-browser/releases/download/v1.98.63/BraveMonoarm.apk).

The reference project also reports a potential native-library alignment issue on
16 KB page-size devices when APKs are repacked. This implementation does **not**
automatically alter `extractNativeLibs`; installation and startup on those
devices are unverified.

## Implemented so far

The app target is marked experimental; Morphe may require enabling experimental
app versions (and, for a remote source, its pre-release patches setting) before
it is shown. This affects source channel selection, so return the toggle to stable
if you do not want pre-releases.

All patches below default **off**. The bytecode patches throw when their required
fingerprint (or every optional alternative in a group) is absent. A successful
patch operation does not prove that the modified path is exercised or that it
covers every equivalent path.

- **Block Motion Sensors**: disables known Chromium sensor availability/creation
  entry points. May also disable ambient sensors; a surviving alternative path
  could still expose sensors. The provider object itself is not replaced with null.
- **Stop Text Sharing with System**: disables the known Chromium onscreen content
  capture gate, not clipboard, accessibility or all other text-sharing paths.
- **Block Popups & Ratings**: disables the identified rating and Ads signup
  eligibility gates if present; other promos and dialogs can remain.
- **Stop Promo Notifications**: disables identified tips scheduling and retention
  receiver entry points if present. Other promo notification paths can remain.
- **Skip Welcome Screens**: forces the identified first-run completion check.
  Setup, consent or search-provider choices may be skipped; use with caution.
- **Stop Background Wakeups**: forces the identified background-sync disable
  check; this can break site background sync and cannot stop all wakeups.
- **Preserve last active tab**: disables the identified inactivity opening-screen
  override. It is deliberately *not* called “Keep All Tabs Loaded”: that hook
  does not stop Android/Chromium from unloading tabs under memory pressure.
- **Remove Extra Languages**: option `Languages to keep`; English is always kept.
  A selected language retains all its regions. Only larger, non-gender-variant
  `assets/locales/*.pak` files are replaced with the English PAK, not deleted;
  Android resources, small PAKs and gender variants remain. Missing/empty English
  fallback fails the patch before rewriting files. Runtime locale fallback is
  unverified.

## Not implemented

Block Telemetry and its offers option; Brave Origin; Clean New Tab and its tiles
option; Close Tab In Group Order; Custom Brave theme and its options; Custom NTP
wallpaper; Keep All Tabs Loaded; Remove Unused AI & VPN and its VPN option.
Those require additional work and actual-APK validation. The supplied bloat
request mentions “AI” in the title but AR/XR in the description; these are
not interchangeable, and removing native libraries may break browser features.

## Validation and licensing

`./gradlew :patches:testBravePrivacy` runs synthetic DEX round-trip checks for
the injected return helpers and locale-selection tests. It does not run the
fingerprints against Brave or establish ART/runtime behavior.

The Brave target fingerprints and locale fallback approach were adapted from
[kveld9/kveld-morphe-patches](https://github.com/kveld9/kveld-morphe-patches),
revision `8afd78f0b57964b9ae9a0a387bdd4ae157345707`, GPL-3.0. The Brave patch
source files and the project as a whole are GPL-3.0-only; see
`LICENSES/GPL-3.0.txt`. This note is not a substitute for the full license or
the third-party attribution notices.
