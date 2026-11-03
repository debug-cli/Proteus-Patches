# MUBI patch set — acceptance report

Date: 2026-11-03. App: **MUBI: Curated Cinema** (`com.mubi`), APKM, minSdk 24,
targetSdk 36. Targets: **100.0 (versionCode 29145546)**, **99.1 (29145545)**.

## 1. Files added (all under `patches/src/main/kotlin/app/template/patches/mubi/`)

| File | Patch (name in bundle) | Default |
|---|---|---|
| `privacy/AllowScreenCapturePatch.kt` | Allow screenshots & screen recording | off |
| `privacy/BlockAnalyticsPatch.kt` | Block analytics & tracking | **on** |
| `privacy/SpoofDrmLevelPatch.kt` | Spoof Widevine level for MUBI | off |
| `ui/BlockUpgradeDeepLinksPatch.kt` | Block upgrade & sale deep links | **on** |
| `ui/BypassCustomTabsPatch.kt` | Prefer external browser over Custom Tabs | **on** |
| `ui/ForceTvUiPatch.kt` | Force TV (leanback) UI | off |
| `ui/SkipTvConfirmationDialogPatch.kt` | Skip TV confirmation dialog | off |
| `player/EnablePiPPatch.kt` | Enable picture-in-picture | **on** |
| `player/ForceHighestQualityPatch.kt` | Force highest video quality | off |
| `player/SkipPreRollPatch.kt` | Skip pre-roll promos | **on** |
| `player/DisableAutoplayPreviewsPatch.kt` | Disable autoplay previews | **on** |
| `ads/RemovePromosPatch.kt` | Remove promo cards & upsell banners | **on** |
| `premium/SuppressPaywallPatch.kt` | Suppress subscription paywall | off |
| `misc/BypassForcedUpdatePatch.kt` | Bypass forced update | off |
| `misc/ChangePackageNamePatch.kt` | Change package name | off |
| `misc/MubiToolkitPatch.kt` | Packaging toolkit (signature, install source, MicroG) | off |
| `shared/ActivityShortcuts.kt` | internal helper (`finishActivityOnCreate`) — not listed | — |
| `README.md` | per-patch table: name / default / mechanism / limitations | — |

Plus `MUBI_COMPATIBILITY` in `patches/src/main/kotlin/app/template/patches/shared/Constants.kt`
(`Compatibility(name = "MUBI", packageName = "com.mubi", apkFileType = ApkFileType.APKM, …)`
with both `AppTarget`s) and the generated `patches-list.json` entries.

`patches-list.json` was verified to contain exactly **16 entries** for `com.mubi`,
each with **both targets** (100.0 → 29145546, 99.1 → 29145545, all four ABIs) and
defaults matching the source. Two entries carry private dependency patches
(`ResourcePatch` for PiP and promo removal), three carry the shared
signature/installer/GmsCore patches and one the shared DRM patch — matching
`dependsOn` in the sources.

## 2. Build result

- **CI build (acceptance alternative): PASS.** Workflow `build.yml` runs
  `./gradlew buildAndroid --stacktrace`:
  - run [41585342366](https://github.com/ineednotitle/Proteus-Patches/actions/runs/41585342366)
    on branch `arena/e07470db-halo-patches` (the 16 MUBI patches + the
    `narrowLiteral` fix): **success** (2026-11-03 05:41Z) — two earlier runs on
    the same branch failed and were fixed by commit `693f2b8`.
  - run [41585599773](https://github.com/ineednotitle/Proteus-Patches/actions/runs/41585599773)
    on `main` after PR #1 merged: **success**; release **v1.18.0** published
    `patches-1.18.0.mpp` with the MUBI entries in `patches-list.json`.
- **Local `./gradlew buildAndroid`: blocked on credentials only.** Gradle 9.7 +
  JDK 17 (Temurin 17.0.21) run fine in this sandbox, but the
  `app.morphe.patches` plugin / `app.morphe:patcher` artifacts live only on
  `maven.pkg.github.com/MorpheApp/registry`, which answers **401** without a
  GitHub PAT (`read:packages`). Local run currently fails at plugin resolution:
  `Plugin [id: 'app.morphe.patches', version: '1.3.3'] was not found … GitHubPackages`.
  To run locally: `export GITHUB_ACTOR=<user> GITHUB_TOKEN=<pat read:packages>`
  (or `gpr.user`/`gpr.key` in `~/.gradle/gradle.properties` — never in the
  tracked `gradle.properties`), then `./gradlew buildAndroid`.

## 3. Anchors still needing verification against a real MUBI APK (100.0 / 29145546)

No MUBI APK was available in this environment; all class/manifest names come
from public static analysis. Verify with jadx/apktool on a merged APKM
(`apktool d` / `jadx -d`) and adjust only the constants at the top of each patch:

1. **Exact class anchors** (used with `mutableClassDefByOrNull`, must exist verbatim):
   - `com.mubi.ui.player.PlayerActivity`, `com.mubi.ui.player.trailer.TrailerActivity`
     (PiP; each must override `onUserLeaveHint()V` for the bytecode half)
   - `com.mubi.ui.subscriptions.UpgradeSubscriptionsActivity` (paywall finish +
     manifest `android:exported`; needs `onCreate(Landroid/os/Bundle;)V` with an
     `invoke-super … onCreate`)
   - `com.mubi.utils.tv.TvConfirmationDialogActivity` (same shape)
2. **Manifest anchors**: `mubi://` hosts `upgrade-yearly`, `blackfriday`;
   https paths `/app_flash_sale2024`, `/summer_sale_2024` (also check
   `pathPrefix`/`pathPattern` variants); the
   `android.support.customtabs.action.CustomTabsService` `<queries>` intent;
   `com.mubi.*` permissions, provider authorities, `taskAffinity`, application label.
3. **Analytics SDK owners + method names** actually present in 100.0:
   `com.braze.Braze` / `com.appboy.Appboy` (`logCustomEvent`, `logPurchase`,
   `requestContentCardsRefresh`, `requestImmediateDataFlush`),
   `com.google.firebase.analytics.FirebaseAnalytics` and
   `com.google.android.gms.measurement.AppMeasurement` (`logEvent`,
   `setUserProperty`, `setUserId`, `setCurrentScreen`),
   `com.appsflyer.AppsFlyerLib` (`logEvent`, `logPurchase`),
   `com.adjust.sdk.Adjust` (`trackEvent`, `trackRevenue`),
   `com.facebook.appevents.AppEventsLogger` (`logEvent`, `logPurchase`),
   `com.segment.analytics.Analytics` (`track`, `screen`, `identify`, `group`).
   All targeted call sites must return `V` — the patch checks this, but confirm
   the method sets exist.
4. **Braze promo UI**: manifest activities under `com.braze.ui.contentcards.*`,
   `com.braze.ui.feed.*` (and `com.appboy.ui.*` legacy); void
   `requestContentCardsRefresh` / `launchContentCards` call sites.
5. **Play Core**: call sites of
   `com.google.android.play.core.appupdate.AppUpdateInfo.updateAvailability()I`
   and `isUpdateTypeAllowed(I)Z`.
6. **Bandwidth meter**: `com.google.android.exoplayer2.upstream.DefaultBandwidthMeter`
   and/or `androidx.media3.exoplayer.upstream.DefaultBandwidthMeter` with
   `getBitrateEstimate()J` and ≥2 free local registers (patch skips otherwise).
7. **Structural call sites** (no MUBI names needed, but confirm they exist in
   player/browse classes): `Window.addFlags`/`setFlags` with literal `0x2000`
   bit, `SurfaceView.setSecure(true)`, `UiModeManager.getCurrentModeType()I`,
   `Player.setPlayWhenReady(Z)` with literal `true` in classes whose names
   contain home/browse/feed/discover/explore/landing.
8. **Pre-roll gate**: string literals `preroll` / `pre-roll` / `pre_roll` /
   `preRoll` and `Z`-returning gate methods named like pre-roll (or generic
   `shouldShow`/`canPlay`/… in a `*preroll*` class).

All patches are fail-safe by construction: an absent class, missing method,
non-literal argument or non-void invoke is left stock.

## 4. Guardrails respected

- No entitlement unlock, no Widevine licence bypass, no 4K/HDR/region/account
  bypass — `SuppressPaywallPatch` explicitly documents that it only hides the
  upgrade screen and does **not** unlock playback.
- Only void (`V`) call sites are `nop`ed; value-returning invokes get a
  constant `move-result` instead (forced update, TV UI).
- Promo deep-link hosts/paths are rewritten to a sentinel, never deleted (deleting
  the only host of a `mubi://` filter would broaden it to every URI).
- `Change package name` never renames activity/service/receiver/provider class
  names; documented Play Billing / Cast / sign-in limits.
- Heuristic patches (pre-roll gates, autoplay class-name markers, TV UI) are
  default-off or explicitly caveated; the TV-UI patch is opt-in.
