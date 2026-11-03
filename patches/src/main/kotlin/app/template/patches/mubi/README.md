# MUBI patches

**App:** MUBI: Curated Cinema (`com.mubi`)
**Bundle format:** APKM (split APKs, base + config)
**Targets:** 100.0 (29145546), 99.1 (29145545)
**Min SDK:** 24 · **Target SDK:** 36 (100.0)

MUBI's streaming entitlement is server-side: the API only returns a playable
manifest and a Widevine licence to a subscribed account. None of these patches
attempt to change that, and no patch here makes an unsubscribed account play.

What these patches do is remove nag/promo surfaces, restore player/platform
features, and stop tracking — all locally, using structures that survive MUBI's
frequent releases.

## Patch list

| Patch | Default | What it changes |
|---|---|---|
| Allow screenshots & screen recording | off | Clears `FLAG_SECURE` from `Window.addFlags`/`setFlags` and `SurfaceView.setSecure(true)` call sites. |
| Block analytics & tracking | **on** | No-ops void logging calls into Braze/Appboy, Firebase Analytics, GMS AppMeasurement, AppsFlyer, Adjust, Facebook App Events and Segment. |
| Block upgrade & sale deep links | **on** | Makes `UpgradeSubscriptionsActivity` non-exported; points `upgrade-yearly` / `blackfriday` hosts and flash-sale paths at a sentinel. |
| Enable picture-in-picture | **on** | Marks the phone player/trailer as PiP-capable, adds the required `configChanges`, and enters PiP from `onUserLeaveHint`. |
| Force highest video quality | off | Reports 1 Gbps from ExoPlayer/Media3 `DefaultBandwidthMeter.getBitrateEstimate()`. Server caps still apply; can increase buffering. |
| Bypass forced update | off | Rewrites Play Core `AppUpdateInfo.updateAvailability()` / `isUpdateTypeAllowed()` results so the blocking update dialog never triggers. |
| Change package name | off | Clones the app: rewrites manifest package, permission ids, provider authorities, task affinity and launcher label. Play Billing/Cast/sign-in may fail on the clone. |
| Force TV (leanback) UI | off | Rewrites `UiModeManager.getCurrentModeType()` to `UI_MODE_TYPE_TELEVISION`. Do not enable on phones. |
| Remove promo cards & upsell banners | **on** | Disables Braze Content Cards/feed activities and no-ops card refreshes. Push notifications stay intact. |
| Suppress subscription paywall | off | Closes `UpgradeSubscriptionsActivity` right after its `onCreate` super call. Hides the screen; does not unlock anything. |
| Skip pre-roll promos | **on** | Forces app-side boolean pre-roll gates to `false` when MUBI names the concept. Server-injected pre-roll is unaffected. |
| Disable autoplay previews | **on** | Turns `setPlayWhenReady(true)` into `false` in home/browse/feed/discover classes only. Opening a title still plays. |
| Prefer external browser over Custom Tabs | **on** | Removes the Custom Tabs service `<queries>` entry so provider probing can fall back to a normal `ACTION_VIEW` browser. Best-effort. |
| Packaging toolkit (signature, install source, MicroG) | off | Applies the shared `Spoof app signature`, `Spoof install source` and `GmsCore support (MicroG)` patches. |
| Spoof Widevine level for MUBI | off | Applies the shared DRM-level patch. Local checks only; licence server still decides resolution. |
| Skip TV confirmation dialog | off | Closes `TvConfirmationDialogActivity` before it draws. Only useful on Android TV. |

## Design rules

1. **Structural, not fingerprinted.** MUBI ships a build every 1–3 weeks. Scans walk
   the DEX for stable API calls (`Window.addFlags`, `Player.setPlayWhenReady`,
   `MediaDrm.getPropertyString`, Play Core `AppUpdateInfo`, Braze class names) instead
   of pinning obfuscated methods.
2. **Fail-safe.** Every patch checks for its target before writing. A renamed class,
   a missing method or a non-literal argument means that site is left stock — never a
   guessed edit.
3. **Void call sites only** for call-site `nop` patches. Replacing an invoke that
   returns a value with `nop` would leave a dangling `move-result`.
4. **Manifest edits keep matching semantics.** Promo hosts/paths are rewritten to a
   sentinel rather than deleted, because deleting the only host of a `mubi://` filter
   would broaden it to every URI.
5. **No component class renaming.** `Change package name` rewrites permission ids,
   authorities and affinities, but never `android:name` on activities/services/providers.

## Known limitations

- **No APK was available while writing these patches.** Class/activity names come from
  public static analysis (taptrap, tabbed-out, MediaHub) of a merged `com.mubi` build.
  The structural scans are written to no-op rather than mis-patch, but method-level
  anchors should be confirmed against 100.0 (29145546) with jadx/apktool.
- **Entitlement, 4K/HDR, region locks and downloaded-file decryption are server-side.**
  No client patch can grant them.
- **Play Billing/Cast/Google sign-in on a re-signed clone** are signature-bound. The
  packaging toolkit helps with local checks only.
- **Custom Tabs** is best-effort: if MUBI calls `CustomTabsIntent.launchUrl` directly,
  removing the query does not change the handoff.

## Adding a new MUBI version

1. Add an `AppTarget(version = "...", versionCode = ...)` to `MUBI_COMPATIBILITY` in
   `shared/Constants.kt`.
2. Patch 100.0 and check that every default-on patch still applies (the patcher logs
   unmatched fingerprints; structural patches simply do less).
3. Drop targets older than a few months — MUBI forces updates quickly.
4. If an activity or deep-link host changed, update the constants at the top of the
   relevant patch, not the scan logic.

See `MUBI_PATCH_PLAN.md` at the repository root for the full research dossier.
