# MUBI patch plan

_Research note + implementation record — 3 November 2026. **Implemented:** 16 patches
under `patches/src/main/kotlin/app/template/patches/mubi/`; see
[mubi/README.md](patches/src/main/kotlin/app/template/patches/mubi/README.md) for the
per-patch table and caveats. Sections below keep the original research and rationale._

Target: **MUBI: Curated Cinema** (`com.mubi`), latest observed build **100.0
(29145546)**, uploaded 29 Oct 2026.

---

## 0. TL;DR

1. **Do not build an "Unlock Premium" patch for MUBI.** MUBI's subscription gate is
   server-side: the API only returns a playable manifest and a Widevine licence to an
   entitled account. A local `isSubscriber -> true` patch changes pixels, not playback.
   Every "MUBI Premium Unlocked" mod APK you can find online is cosmetic or fake.
2. **The valuable MUBI patches are player/UX/privacy patches**, not billing patches:
   removing the pre-roll promo, allowing screenshots/screen recording, enabling PiP,
   blocking Braze/analytics, hiding upgrade & sale nags, forcing the TV UI, and porting
   the existing universal DRM/signature/installer patches.
3. **MUBI ships every 1–3 weeks** (96.0 → 96.1 → 96.2 → 97.0 → 97.1 → 99.0 → 99.1 →
   100.0 between Aug and Oct 2026). Single-version `AppTarget` pinning — the norm in this
   repo — will rot within a month. MUBI should be a **multi-version target** like Reddit
   (6 targets) or Yatri (5 targets), with fingerprints anchored on stable artefacts
   (activity names, deep-link hosts, SDK class names, resource strings), never on
   obfuscated `Ll2/d;`-style classes.
4. **Shipped:** the full set — privacy (screenshots, analytics, Widevine), promo/upsell
   removal, PiP, quality, forced-update bypass, TV UI/dialog, pre-roll/autoplay,
   Custom Tabs preference, package rename and the packaging toolkit. See
   [mubi/README.md](patches/src/main/kotlin/app/template/patches/mubi/README.md).
5. The repo rename is a separate, low-risk operation — see
   [REBRAND_OPTIONS.md](REBRAND_OPTIONS.md).

---

## 1. What this repository is today (and what that means for MUBI)

### 1.1 Shape of the bundle

| Metric | Value (main @ v1.17.0) |
|---|---|
| App source directories | 229 (`patches/src/main/kotlin/app/template/patches/*`) |
| Distinct packages in `patches-list.json` | 236 |
| Patch entries | 304 (source declares ~296 `bytecodePatch` + 18 `resourcePatch`) |
| Compatibility definitions | 237 in `shared/Constants.kt` |
| Shared helper code | ~5,900 LOC (`shared/`) |
| Runtime extension | `extensions/extension` (`.mpe`, Java helpers) |
| Release plumbing | semantic-release + `app.morphe.patches` Gradle plugin |
| Generated artefacts | `patches-*.mpp`, `patches-bundle.json`, `patches-list.json`, `README.md` |

Patch mix is dominated by local licence flags: 105 patches literally named
**"Unlock Premium"**, 36 "Unlock Pro", plus a long tail of `isPro` / `isPremium` /
`checkLicense` hooks. That recipe works for apps whose entitlement is a boolean in
SharedPreferences. **MUBI is not that kind of app**, so this is a chance to add a
different, more durable playbook to the bundle.

### 1.2 House conventions a MUBI PR must follow

- **One `Compatibility` entry** in `shared/Constants.kt`; every patch calls
  `compatibleWith(MUBI_COMPATIBILITY)`.
- **One folder per app**, normally `mubi/<feature>/` containing `Fingerprints.kt` plus
  `<Name>Patch.kt` (see `messenger/privacy/`, `amazon/disableautoplay/`). Simpler apps
  keep files flat (see `googlephotos/`); MUBI has enough surface for subfolders.
- **Fingerprints are documented evidence**, not guesses: target class, method, DEX file,
  exact app version, and the smali/behaviour that justifies the anchor. Look at
  `dramabox/membership/Fingerprints.kt` for the expected level of detail.
- **Defaults:** user-facing privacy patches default to `true` (e.g. District, Pinterest
  analytics); patches that weaken a security property default to `false` (e.g.
  `Spoof Widevine / DRM level`, `Spoof app signature`). Screenshot unlocking and DRM
  spoofing should stay opt-in.
- **Reuse `shared/` rather than re-implement**: `shared/drm/SpoofDrmPatch.kt`,
  `shared/signature/`, `shared/installer/`, `shared/gms/`,
  `shared/firebase/SpoofFirebaseCertHashPatch.kt`.
- **Version pinning:** 233 of 237 compatibilities are single-version. Only Reddit (6),
  Yatri (5), Strava (2) and Amazon India (2) are multi-version. MUBI belongs in the
  second group.

### 1.3 Gaps this plan fills

- No `MUBI_COMPATIBILITY` and no `mubi/` folder.
- No player-level patterns beyond `amazon/disableautoplay/` — MUBI needs pre-roll,
  PiP, quality-floor and FLAG_SECURE handling.
- No TV/leanback-specific helper, even though many patched apps ship TV builds; MUBI's
  UI is split phone/TV inside one package, so a TV patch is genuinely testable.
- Repo identity is still partly vestigial (`package.json` says
  `doom-reddit-patches`, `settings.gradle.kts` mentions "Holopatch Patches bundle"),
  which is worth cleaning up during the rename.
- The README headline says "320+ apps", while `patches-list.json` and the generated
  per-app sections cover 236 packages. Worth reconciling in the same pass — an accurate
  number is a credibility feature for a security-adjacent project.

---

## 2. MUBI dossier

### 2.1 Facts

| Item | Value | Confidence |
|---|---|---|
| Package | `com.mubi` | confirmed |
| App name | MUBI: Curated Cinema | confirmed |
| Latest build (3 Nov 2026) | 100.0, versionCode **29145546** (29 Oct 2026) | confirmed |
| Previous builds | 99.1 = 29145545, 99.0, 97.1, 97.0, 96.2 … (versionCode observed to increment by 1 between 99.1 and 100.0) | high |
| Distribution | Split APK bundle (APKM), universal + per-ABI variants | confirmed |
| Min SDK by variant | Android 7.0+ universal, Android 12L+ universal, Android 10+ arm64 | confirmed |
| Target SDK | 36 on recent builds (34 in the Dec-2024 sample) | high |
| TV support | Same package ships `TvMainActivity`, `TvPlayerActivity`, `TvTrailerActivity` | confirmed |
| Other build | Amazon Appstore Fire TV / Tablet build published separately (97.0) | confirmed |
| Obfuscation | R8; app classes such as `com.mubi.ui.*` are readable, internals are not (`Ll2/d;`) | confirmed |
| Ads model | Marketed as ad-free; users report a skippable MUBI self-promo/trailer before films | medium — verify on 100.0 |
| Streaming | Up to 1080p, some 4K; adaptive bitrate with a user "max resolution" setting | medium |
| Downloads | Offline downloads in supported regions; DRM licence window, titles require re-download after expiry | medium |
| MUBI GO | Separate companion app `com.mubi.go`; main app queries for it | confirmed |

### 2.2 Component map (from static analysis of a merged `com.mubi` APK)

| Component | Role / patch relevance |
|---|---|
| `com.mubi.ui.MainActivity` | Launcher + `autoVerify` App Links entry point. Prime anchor for promo/UI patches. |
| `com.mubi.ui.player.PlayerActivity` | Film playback. Pre-roll, FLAG_SECURE, PiP, ABR. |
| `com.mubi.ui.player.tv.TvPlayerActivity` | TV playback. Must be patched alongside the phone player. |
| `com.mubi.ui.player.trailer.TrailerActivity` | Trailer playback; likely the pre-roll surface. |
| `com.mubi.ui.player.trailer.TvTrailerActivity` | TV pre-roll surface. |
| `com.mubi.ui.player.cast.ExpandedControlsActivity` | Google Cast expanded controls; Cast SDK is present. |
| `com.mubi.ui.subscriptions.UpgradeSubscriptionsActivity` | Upgrade flow. Hiding its entry points is a legitimate UX patch (not an unlock). |
| `com.mubi.ui.TvMainActivity` | Leanback home. Enables a "force TV UI" patch. |
| `com.mubi.ui.utils.tv.TvConfirmationDialogActivity` | TV-only dialog host. |
| `com.mubi.ui.onboarding.OnboardingActivity` | Onboarding / sign-in wall. Browsing without an account is already allowed, so low value. |
| `com.braze.*` / `com.appboy.*` activities | Braze content cards, in-app messages, web views, push trampoline. Analytics/marketing patch targets. |
| `com.android.billingclient.api.ProxyBillingActivity(V2)` | Play Billing. Breaks on re-signed builds; do not promise purchase flows. |
| `com.google.android.play.core.common.PlayCoreDialogWrapperActivity` | Play In-App Updates. Anchor for a forced-update bypass. |

### 2.3 Deep links (observed)

- Custom scheme `mubi://` — hosts: `subscribe`, `upgrade-yearly`, `blackfriday`,
  `subscription-settings`, `retrospective`, `sign-in`.
- App Links on `mubi.com` / `*.mubi.com` — paths include `/showing`, `/films/*`,
  `/films/*/trailer`, `/films/*/player`, `/films/*/cast`, `/cast/*`, `/specials/*`,
  `/collections/*`, `/retrospective`, plus promo paths `/app_flash_sale2024`,
  `/summer_sale_2024`.

These are excellent, stable anchors for "hide promo" patches: the sale deep links are
hard-coded, and `UpgradeSubscriptionsActivity` is exported.

### 2.4 Where the gates actually live

| Gate | Where | Patchable? |
|---|---|---|
| Subscription entitlement (`is_subscriber`, plan tier) | MUBI API + account | **No** |
| Playable stream manifest | API, entitlement-checked | **No** |
| Widevine licence / HDCP / L1 vs L3 | Licence server + CDM | **No** (spoofing local `securityLevel` only changes client checks) |
| Region catalogue | Account country + IP + `Client-Country` | No (and not appropriate) |
| MUBI GO tickets | Server-issued QR | No |
| Download expiry | Widevine licence duration | No |
| Pre-roll promo ordering | Client player / API playlist | **Yes, usually** |
| FLAG_SECURE, PiP, TV UI choice, autoplay, promo banners, Braze cards, analytics | Client | **Yes** |
| Update gate | Client + Play Core | **Yes** |

Rule of thumb for this app: **if the patch changes what MUBI *shows*, it can work; if it
changes what MUBI *serves*, it cannot.** Test by patching, signing, installing on a
logged-out / lapsed account, and watching the network: `api.mubi.com` will still return
401/402/403 and the licence request will fail.

---

## 3. Patch portfolio

Status legend: **P0** ship first · **P1** next · **P2** spike · **✗** reject.

| # | Patch name | What it changes | Effort | Risk | Default |
|---|---|---|---|---|---|
| 1 | **Remove pre-roll promo** | Starts the film immediately; skips MUBI's own trailer/promo | M | Low | `true` |
| 2 | **Allow screenshots & screen recording** | Drops `FLAG_SECURE` from player windows (also fixes mirroring black-outs) | S | Low | `false` |
| 3 | **Enable picture-in-picture** | Manifest + PiP params for phone/tablet player | M | Low | `true` |
| 4 | **Block analytics & tracking** | No-ops Braze events, Firebase Analytics, attribution SDKs | M | Low | `true` |
| 5 | **Hide upgrade & sale prompts** | Removes upgrade banners, flash-sale deep links, Braze promo cards | M | Medium | `true` |
| 6 | **Spoof Widevine / DRM level** | Reuses `shared/drm`; reports L1/HDCP 2.2 locally | S | Medium | `false` |
| 7 | **Change package name / custom branding** | Side-by-side install with official MUBI and MUBI GO | M | Medium | `false` |
| 8 | **Spoof install source / app signature** | Reuses `shared/installer` + `shared/signature` for re-signed builds | S | Medium | `false` |
| 9 | **GmsCore support** | Google sign-in/Cast on degoogled devices | S | Low | `false` |
| 10 | **Bypass forced update** | Neutralizes Play Core update block | S–M | Low | `true` |
| 11 | **Force TV / leanback UI** | Sends TV boxes that misreport as phones into `TvMainActivity` | M | Medium | `false` |
| 12 | **Force highest video quality** | Disables ABR downshift / picks top rendition | L | Medium–High | `false` |
| 13 | **Downloads QoL** | Highest default download quality, allow cellular | S–M | Low | `false` |
| 14 | **Disable home trailer autoplay** | Stops carousel trailer autoplay (data/battery/motion) | S–M | Low | `true` |
| 15 | **Hide MUBI GO cross-promo** | Suppresses GO upsell where the GO app is absent | S | Low | `true` |
| ✗ | "Unlock Premium" / "Premium unlocked" | Nothing real | — | — | — |
| ✗ | Free MUBI GO tickets / region unlock / download-expiry bypass | Server/DRM | — | — | — |

---

### 3.1 Tier 1 — ship these first

#### 1. Remove pre-roll promo (P0, the hero patch)

**Why:** MUBI markets itself as ad-free, yet users have long complained about an
unskippable-except-by-one-click self-promo/trailer before the film. No setting exists to
disable it. This is exactly the kind of annoyance the bundle already fixes for other
apps (`Remove ads`, `Disable video autoplay`).

**How to find it (triage order):**

1. `jadx`/`apktool` the 100.0 bundle, then trace `com.mubi.ui.player.PlayerActivity`
   and `TrailerActivity` launch sites.
2. Grep for `preroll`, `pre_roll`, `promo`, `trailer`, `featured`, `spotlight`,
   `MediaItem`, `setMediaItems`, `addMediaItem`, `ConcatenatingMediaSource`.
3. Check whether the pre-roll is a **second media item** in a player playlist (drop it)
   or a **separate activity launch** (skip the `startActivity`), or an **ad marker** in
   the manifest (skip to content start).
4. Confirm in-app: play a film on a clean install and watch which activity appears first.

**Implementation sketch:** inject a branch that removes the first non-film
`MediaItem`, or no-op the pre-roll launch path; keep a fallback that does nothing if the
model changes. Must cover both `PlayerActivity` and `TvPlayerActivity`.

**Verify:** film starts on first frame with no promo; resume-from-middle still works;
Cast still starts at the film; no crash when the promo list is empty.

#### 2. Allow screenshots & screen recording (P0)

**Why:** Android viewers report that MUBI blocks screen mirroring/capture during
playback. Even if the motive is DRM hygiene, Widevine already protects the decoded
surface; `FLAG_SECURE` mostly punishes people who want a still of a frame.

**How to find it:** grep for `FLAG_SECURE`, `0x2000`, `setFlags`, `addFlags`,
`WindowManager.LayoutParams`, and `setRecentsScreenshotEnabled`. Expect it in
`PlayerActivity`/`TvPlayerActivity` and possibly a shared player fragment.

**Implementation:** port the shape of
`messenger/privacy/AllowScreenCapturePatch.kt`: remove/replace the `addFlags` call and
silence any screenshot observer. If MUBI sets FLAG_SECURE in a base player class, patch
there so both phone and TV are covered.

**Caveats:** DRM-protected surfaces may still render black on some devices regardless of
FLAG_SECURE — say so in the patch description. Default `false`, since it weakens a
content-protection property.

#### 3. Enable picture-in-picture (P0)

**Why:** MUBI's iOS app shipped PiP years ago; the Android player has no PiP affordance.
A floating-window option is a real feature gain, not a gate bypass.

**Implementation:** `resourcePatch` on `AndroidManifest.xml`:
add `android:supportsPictureInPicture="true"` and the usual
`configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden"`
to `PlayerActivity` and `TvPlayerActivity` (TV can be skipped or made an option).
For Android 12+ add `PictureInPictureParams.Builder().setAutoEnterEnabled(true)` at the
player's `onCreate`/`onUserLeaveHint` path; for older APIs hook `onUserLeaveHint` to call
`enterPictureInPictureMode()`.

**Verify:** swipe home during playback → video continues in PiP; back/close PiP stops
playback; subtitles/controls don't crash; audio focus behaves.

#### 4. Block analytics & tracking (P1)

**Why:** MUBI embeds **Braze** (events, content cards, in-app messages, push trampoline)
and, like most streaming apps, Firebase/attribution SDKs. This is the bundle's most
repeated privacy pattern and the safest high-value patch.

**Implementation:** follow `pinterest/BlockAnalyticsPatch.kt` and
`district/privacy/DisableAnalyticsPatch.kt`: no-op the concrete SDK entry points
(`com.braze.Braze.logCustomEvent`, `logPurchase`, `requestContentCardsRefresh`,
`FirebaseAnalytics.logEvent`/`setUserProperty`, attribution `onAppOpenAttribution`,
Crashlytics/Sentry init). Keep transactional push working unless the user opts into
"disable Braze completely".

**Verify:** no Braze/Firebase events in logcat or mitm; app still logs in, plays, downloads,
and receives normal push; no first-launch crash from a missing SDK singleton.

#### 5. Hide upgrade & sale prompts (P1)

**Why:** MUBI's promo surface is explicit in the manifest: `UpgradeSubscriptionsActivity`
is exported, the `mubi://` scheme hard-codes `upgrade-yearly` and `blackfriday`, and the
App Links include `/app_flash_sale2024` and `/summer_sale_2024`. Subscribers should not
be nagged by flash-sale banners.

**Implementation:** hide/neutralize entry points to `UpgradeSubscriptionsActivity` and
Braze content cards; drop promo banner cells; optionally rewrite the sale deep-link
handling to open the normal home. Do **not** claim entitlement changes — the film still
requires a real subscription.

**Verify:** launch via each deep link (`adb shell am start -a android.intent.action.VIEW
-d "mubi://upgrade-yearly"`), browse home, open a film as a subscriber → no upgrade
banner, playback unaffected.

#### 6. Port the universal compatibility set (P1)

These are cheap wins because the code already exists; MUBI just needs a compatibility
entry and testing:

| Shared patch | MUBI-specific concern |
|---|---|
| `Spoof Widevine / DRM level` | Only affects local `securityLevel`/HDCP checks; may raise the player's chosen rendition on L3 devices, may do nothing. Say so. |
| `Spoof install source` | MUBI queries `com.android.vending`; a re-signed build may look non-Play. |
| `Spoof app signature` | Play Billing will still fail on re-signed builds; document that purchases require the official app. |
| `GmsCore support` | Needed for Google sign-in/Cast on degoogled ROMs. |
| `Spoof Firebase cert hash` | Only if MUBI uses Firebase Auth with SHA-1 pinning; verify first. |
| `Change package name` | Useful to keep official MUBI + patched build side by side; MUBI has providers/billing, so test carefully (copy the Google Photos resource-patch pattern, not a blind manifest rename). |

---

### 3.2 Tier 2 — spikes worth scheduling

- **Force highest video quality / no ABR downshift.** Users complain MUBI drops to
  ~480p on good connections and there is no minimum-quality setting. The player is
  adaptive; a DEX patch could pin the top rendition (or raise the ABR floor). This
  overlaps with the DRM patch: if the cap is licence/HDCP-driven, no client patch helps.
  Budget a spike, not a promise. High user value if it works.
- **Force TV / leanback UI.** Many Android TV boxes and Fire TV sticks misreport UI mode,
  and MUBI then serves the phone layout. `TvMainActivity` is shipped in the same package,
  so a launcher/`UiModeManager` patch can route those devices to leanback. Needs testing
  on at least one real TV box and one phone (must not force TV UI on phones).
- **Disable carousel trailer autoplay.** Separate from the pre-roll: home rows autoplay
  trailers. Reuse `amazon/disableautoplay/` approach.
- **Bypass forced update.** Play Core is present. Verify whether 100.0 actually blocks
  old versions before building this; if it does, it is a small, popular patch.
- **Downloads QoL.** Highest default download quality and "allow cellular" default.
  Modest value, low risk — but region/licence limits still apply.
- **Hide MUBI GO cross-promo.** The main app queries `com.mubi.go`; suppress the GO
  upsell on devices where GO isn't installed/available (notably most of India).

---

### 3.3 Rejected / do not ship

- **"Unlock Premium" / "Premium Unlocked".** MUBI is entitlement-gated at API + DRM
  licence level. Shipping this would produce a patch that visibly "unlocks" UI while
  every film fails to play — the fastest way to burn the bundle's credibility.
- **Free MUBI GO tickets.** QR codes are server-issued per membership.
- **Region/catalogue unlock.** Server + IP + account country; circumventing it is a ToS
  problem, not a patch problem.
- **Download-expiry bypass.** Widevine licence duration is enforced by the CDM.
- **"Remove ads".** MUBI has no third-party ads; name the pre-roll patch precisely
  instead, or users will expect an ad-free tier that already exists.

---

### 3.4 Reuse map (copy these files, don't invent new patterns)

| Need | Existing source to adapt |
|---|---|
| FLAG_SECURE removal | `messenger/privacy/AllowScreenCapturePatch.kt` + its fingerprints |
| Analytics no-ops | `pinterest/BlockAnalyticsPatch.kt`, `district/privacy/DisableAnalyticsPatch.kt` |
| Autoplay/pre-roll control | `amazon/disableautoplay/DisableVideoAutoplayPatch.kt` |
| Package rename / branding | `googlephotos/ChangePackageNamePatch.kt`, `googlephotos/CustomBrandingPatch.kt` |
| DRM/HDCP spoof | `shared/drm/SpoofDrmPatch.kt` |
| Installer/signature spoof | `shared/installer/`, `shared/signature/` |
| GmsCore | `shared/gms/GmsCoreSupportPatch.kt` (see `googlephotos/GmsCoreSupportPatch.kt` wrapper) |
| Forced update | `shared/` + any app already patching Play Core (search `PlayCoreDialogWrapperActivity`, `AppUpdateInfo`) |
| TV/leanback | none yet — this would be the first, put helpers in `shared/` if reused |

---

## 4. Compatibility & fingerprint strategy for a fast-moving app

### 4.1 Compatibility entry

```kotlin
val MUBI_COMPATIBILITY = Compatibility(
    name = "MUBI",
    packageName = "com.mubi",
    apkFileType = ApkFileType.APKM,   // Play delivers split bundles
    appIconColor = 0x1A1A1A,          // MUBI's black/white mark; adjust to the icon
    targets = listOf(
        AppTarget(version = "100.0", versionCode = 29145546),
        AppTarget(version = "99.1",  versionCode = 29145545),
        // Add 99.0 / 97.1 / 97.0 after verifying fingerprints on each.
    ),
)
```

VersionCodes increment by one per release, so maintaining a rolling 3–5 version window
is cheap — closer to Reddit's 6 targets than to the usual single pin.

### 4.2 Anchor bank (stable → brittle)

Preferred anchors, in order:

1. **Activity class names** — `com.mubi.ui.player.PlayerActivity`,
   `com.mubi.ui.TvMainActivity`, `com.mubi.ui.subscriptions.UpgradeSubscriptionsActivity`.
2. **Deep-link hosts/paths** — `upgrade-yearly`, `blackfriday`, `/app_flash_sale2024`,
   `/films/.*/player`.
3. **SDK class names** — `com.braze.*`, `com.appboy.*`, Play Billing, Play Core, Cast.
4. **Resource names/strings** — settings labels ("Download quality", "Wi-Fi only",
   "Max resolution", "Subtitles"), and promo copy. Use `ResourceMappingPatch` helpers.
5. **API/GraphQL operation names** — once discovered, but they change more often.
6. **Obfuscated classes** (`Ll2/d;`, `Lxe/h;`) — **never** as the primary anchor; only as
   a secondary filter after a stable string/activity match.

### 4.3 Test matrix

| Dimension | Cases |
|---|---|
| Device class | Phone (Android 7/10/14/16), tablet, Android TV, Fire TV |
| Build format | Universal APK, arm64 split, APKM bundle |
| Account | Logged out (browse), free trial, active subscriber, lapsed |
| DRM | L1 device, L3 device, root/Magisk device |
| Network | Wi-Fi, cellular (downloads), offline playback |
| Player | Phone player, TV player, trailer player, Cast |

A patch is not done until it has been exercised on at least: one phone, one TV build,
and both a subscriber and a logged-out account.

---

## 5. Suggested milestones

| Milestone | Contents | Rough effort |
|---|---|---|
| **M0 — Triage** | Add `MUBI_COMPATIBILITY` (100.0 only); decompile; write an evidence note per candidate anchor; confirm pre-roll/FLAG_SECURE/Play-Core behaviour on 100.0 | 0.5–1 day |
| **M1 — Compat + quick wins** | Screenshot allow, PiP, DRM/installer/signature compat, GmsCore wrapper, package rename | 2–3 days |
| **M2 — Hero patches** | Remove pre-roll (phone + TV), hide upgrade/sale prompts, analytics/Braze blocking | 3–5 days |
| **M3 — Larger spikes** | Quality floor, forced-update bypass, TV UI, downloads QoL, GO promo hiding | 1–2 weeks |

Release with conventional commits so semantic-release does the rest:

```
feat(mubi): add pre-roll promo skip patch
feat(mubi): enable picture-in-picture in phone player
feat(mubi): block Braze and Firebase analytics
feat(mubi): hide upgrade and flash-sale prompts
bump(mubi): support 100.0 and 99.1
```

---

## 6. Risks and guardrails

- **Play Billing on re-signed builds.** Purchases and subscription management may fail
  once the APK is re-signed. State this in the README and patch description; never imply
  a patched build can buy or restore a subscription.
- **Cast and downloads** are entitlement-sensitive. Test both; expect failures that no
  DEX patch can fix.
- **Promo hiding vs entitlement.** Hiding an upgrade prompt is cosmetic. Make the
  description explicit: "does not unlock playback".
- **Screenshot unlocking** weakens a content-protection property. Keep it opt-in and
  document the DRM caveat.
- **Version churn.** MUBI's release cadence is the biggest maintenance cost. Budget a
  "bump(mubi)" pass every release; use multi-version targets and stable anchors to avoid
  a full re-fingerprint each time.
- **Naming and claims.** "Remove ads" is misleading for MUBI, which has no third-party
  ads; "Skip pre-roll promo" is precise.
- **Legal/ethical.** Keep the bundle's existing stance: no DRM circumvention, no
  entitlement bypass, no piracy features. The README's security section already sets this
  bar; MUBI should not be the exception.

---

## 7. First commands for the triage session

```bash
# 1. Pull the bundle (APKM) and unpack
apktool d mubi-100.0.apkm -o mubi100
# or: unzip the APKM/APKS, then apktool per split

# 2. Confirm the obvious surfaces
grep -rn "FLAG_SECURE\|0x2000\|setFlags\|addFlags" mubi100/smali* | grep -i player
grep -rn "supportsPictureInPicture\|PictureInPictureParams" mubi100/AndroidManifest.xml mubi100/smali*
grep -rn "UpgradeSubscriptionsActivity\|upgrade-yearly\|blackfriday\|flash_sale" mubi100

# 3. Find the pre-roll
grep -rni "preroll\|pre_roll\|promo\|spotlight\|featured" mubi100/smali*/com/mubi | head -50
grep -rn "setMediaItems\|addMediaItem\|ConcatenatingMediaSource" mubi100/smali* | grep -i player

# 4. SDK surface
grep -rn "com/braze\|com/appboy\|FirebaseAnalytics\|AppsFlyer\|Adjust" mubi100/smali* | head

# 5. Update / DRM / TV
grep -rn "AppUpdateInfo\|startUpdateFlow\|PlayCoreDialog" mubi100/smali*
grep -rn "MediaDrm\|Widevine\|securityLevel" mubi100/smali* | head
grep -rn "UiModeManager\|isTv\|leanback" mubi100/smali*/com/mubi | head
```

Keep every finding in the fingerprint's doc comment — DEX file, version, smali excerpt —
matching the standard used by `dramabox/` and `messenger/`.

---

## Appendix A — Constants entry

```kotlin
val MUBI_COMPATIBILITY = Compatibility(
    name = "MUBI",
    packageName = "com.mubi",
    apkFileType = ApkFileType.APKM,
    appIconColor = 0x1A1A1A,
    targets = listOf(
        AppTarget(version = "100.0", versionCode = 29145546),
        AppTarget(version = "99.1", versionCode = 29145545),
    ),
)
```

## Appendix B — patch skeleton

```kotlin
package app.template.patches.mubi.preRoll

import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.MUBI_COMPATIBILITY

@Suppress("unused")
val mubiRemovePreRollPatch = bytecodePatch(
    name = "Remove pre-roll promo",
    description = "Skips MUBI's own pre-roll trailer/promo and starts the film immediately. " +
        "Does not affect subscription entitlements.",
    default = true,
) {
    compatibleWith(MUBI_COMPATIBILITY)

    execute {
        // Replace with the verified fingerprint from PlayerActivity/TrailerActivity.
        // Keep the patch fail-safe: if the model is missing, do nothing.
    }
}
```

## Sources

- APKMirror — MUBI 100.0 release (versionCode 29145546, 29 Oct 2026):
  <https://www.apkmirror.com/apk/mubi/mubi-curated-cinema/mubi-curated-cinema-100-0-release/>
- APKMirror — MUBI all releases/version history:
  <https://www.apkmirror.com/apk/mubi/mubi-curated-cinema/>
- MediaHub deep-link registry (MUBI deep links, queried packages `com.mubi.go`,
  target SDK 36): <https://github.com/Armaldio/MediaHub/blob/master/DEEP_LINK_REGISTRY.md>
- taptrap static analysis of a merged `com.mubi` APK (activity map):
  <https://github.com/beerphilipp/taptrap/blob/main/vulnerable_app_detection/results/2025-05-20/output/com.mubi.json>
- MUBI supported devices (Android 7+, Android TV, Fire OS 6+):
  <https://mubi.com/en/devices>
- r/mubis — pre-roll promo complaint:
  <https://www.reddit.com/r/mubis/comments/10c49ax/>
- r/mubis — quality drops to ~480p / no minimum quality setting:
  <https://www.reddit.com/r/mubis/comments/1bshofb/>
- r/mubi — Android playback/mirroring issues:
  <https://www.reddit.com/r/mubi/comments/1ikj6xg/>
- App Store review noting downloaded films fail after ~48h (DRM licence window):
  <https://apps.apple.com/si/app/mubi-stream-great-cinema/id626148774>
