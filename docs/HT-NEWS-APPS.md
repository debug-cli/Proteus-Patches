# HT News apps (Mint & Hindustan Times) — patch analysis

Status: **analysis only — no fingerprints written, nothing shipped yet.**
This document is the honest feasibility map for the two HT Media Ltd apps,
`com.htmedia.mint` (Mint: Stock & Business News) and `com.ht.news`
(Hindustan Times: News & ePaper), both Android 8.0+.

No APK was available in this workspace and the sandbox cannot fetch one, so
every candidate below is marked with a confidence level and the exact thing the
binary must confirm before any patch is written. **Do not ship fingerprints
guessed from web listings** — they compile in CI but fail at patch time on a
real install.

## App facts (public store listings, 2026-11-06)

| App | Package | Developer | Latest seen | Notes |
|---|---|---|---|---|
| Mint | `com.htmedia.mint` | HT Media Ltd | 5.7.5 / 5.8.4 | Business & markets focus |
| Hindustan Times | `com.ht.news` | HT Media Ltd | 4.9.16–4.9.17 (Oct 2026) | News + ePaper, English, target API 36 |

Both are first-party HT Media apps, very likely sharing UI stack, analytics
and ad rails — one decompile probably covers both.

## Already works today (no new code)

- **Remove dangerous permissions** (universal, app-agnostic): POST_NOTIFICATIONS,
  AD_ID, LOCATION, MIC, CAMERA, STORAGE, SMS, etc. — works on both apps as-is.
- **Block analytics & tracking SDKs** (universal, heuristic; v2.2.8 extends the
  marker list to `com/webengage`, `com/clevertap`, `com/flurry`, `io/branch`,
  `com/kochava` on top of Firebase Analytics / AppsFlyer / Adjust / Facebook /
  Segment / Amplitude / Mixpanel / Localytics / Countly / UrbanAirship). Off by
  default; verify with `adb logcat` that `logEvent` calls disappear on first run.
- **Fix Firebase after re-signing** (universal): needed because these apps will
  be re-signed by the patcher.

## Candidate patches (blocked on an APK unless marked otherwise)

| # | Patch | Type | Confidence | Binary must confirm |
|---|---|---|---|---|
| 1 | Disable video autoplay in feeds | bytecode (ExoPlayer/Media3 `setPlayWhenReady(true)` sites) or resource | HIGH | Player creation sites, ad-vs-content video tagging |
| 2 | Block/neutralize in-feed sponsored articles & native ads | bytecode heuristic (ad SDK call sites + promoted-flag heuristic à la RedditFeedHelper) | MEDIUM | Which ad SDK (GAM/AdMob/in-house) renders rows; any `promoted`/`sponsored` flag on row models |
| 3 | Open article links in external browser | bytecode (WebView path / intent builder) | HIGH | Whether articles render in in-app WebView or native views |
| 4 | AMOLED black reading surface | resource (night/dark themes) + optional WebView CSS, à la Amazon dark patch | HIGH (WebView confirmed) / MEDIUM (native themes) | WebView usage for article bodies; dark theme resources |
| 5 | Remove rating / in-app review nag (`ReviewManager`) | bytecode (invocation sites) | MEDIUM-HIGH | `com/google/android/play/core/review` usage |
| 6 | Disable push permission nag | bytecode (POST_NOTIFICATIONS rationale UI) | MEDIUM | Custom nag vs system dialog |
| 7 | Mute "breaking news" notification categories | resource/bytecode (notification channel defaults) | MEDIUM | Channel definitions in manifest `<meta-data>` / channels in code |
| 8 | ePaper ad cleanup | WebView/resource | LOW-MEDIUM | ePaper rendering path (page viewer? web pages? images?) |

Explicitly **out of scope**: "Mint Premium" / "HT Premium". Mod-APK builds
circulate in search results, but the premium text itself is served behind
**server-side** entitlement. A client patch can bypass the local UI gate at
best — not the paywall — and will likely just fail. Not attempted, not promised.

## Verification once the APK lands

```bash
# On the device (both apps installed), get the APK paths incl. splits:
adb shell pm path com.htmedia.mint
adb shell pm path com.ht.news
adb pull /data/app/<...>/base.apk mint-base.apk      # and any split_config.*.apk
adb pull /data/app/<...>/base.apk ht-base.apk

# Then, in the repo:
python3 - <<'EOF'
import zipfile
for apk in ["mint-base.apk", "ht-base.apk"]:
    z = zipfile.ZipFile(apk)
    print(apk, "->", z.read("AndroidManifest.xml") and "manifest ok")
    print(z.namelist())
EOF
# SDK + string probe (no JDK needed): pip install androguard
# androguard axml AndroidManifest.xml; androguard dx --list classes*.dex for packages
# grep -a -o 'ca-app-pub-[0-9]*' classes*.dex | sort | uniq -c   # AdMob IDs
# grep -a -oE '(app-measurement|firebase|webengage|clevertap|exoplayer|play/core/review)' classes*.dex | sort | uniq -c
```

Only after this dump do fingerprints get written, via the usual repo QC:
CI build + `:patches:test*` + the same review the Brave patches received.

## Recommended v2.2.8 contents

1. Universal Block-analytics marker extension (shipped — no app work needed).
2. This analysis.
3. NOT included: any app-specific fingerprint for either app (needs the APK).

If an APK is provided, targets 1–5 above are the first batch for v2.2.9+.
