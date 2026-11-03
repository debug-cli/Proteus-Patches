# Meesho patch plan — analysis of `com.meesho.supply`

Date: 2026-11-03. **App:** Meesho: Online Shopping App ("Meesho – Resell & Earn"),
package **`com.meesho.supply`** (FashNear Technologies, Bangalore).

## 0. Sources used (no Play-Store login available in this environment)

- **Decoded AndroidManifest.xml** of a real Meesho build (Mar 2025, compileSdk 34):
  `github.com/pea1bee/android-manifest-files/Meesho-AndroidManifest.xml` (42 KB, parsed locally).
- **εxodus report 766078** (Meesho **28.8**, Google Play, 2026-08-05): 5 trackers, 39 permissions,
  signing cert `831640ae…` (CN=Sanjeev Barnwal, O=FashNear).
- **APKMirror/AppBrain**: latest **29.7** (uploaded 2026-10-28, base APK 36.7 MB, `BUNDLE`
  variants → Morphe sees it as **APKM**); minSdk **21** (Android 5.0+); older versionCodes:
  29.3 = 864, 28.6 = 846, 28.4 = 841 → **29.7's versionCode must be read from the real
  APKM (≈870)** before writing `AppTarget`.
- **Public security write-ups** (Medium, Jan 2026) confirming class names:
  `SplashNDActivity`/`HomeActivity`, `Constants.DEEPLINK`, extra `"web-deeplink"`.
- **ReVanced request** nullcpy/rvb#168 for `com.meesho.supply` (closed "completed" elsewhere).
- Meesho ads are **first-party** ("Sponsored Products"/Brand Ads, CPC, "Ad" label in
  search / home feed / related listings) — not AdMob. There is no third-party ad SDK in
  the Exodus tracker list.

## 1. Observable component map (from the real manifest)

| Component | Notes |
|---|---|
| `com.meesho.supply.main.SupplyApplication` | Application class; `allowBackup=false` |
| `…main.MainActivity` | launcher, exported |
| `…main.HomeActivity` | home feed |
| `…main.TrampolineActivity` | exported; deep links: `https://<app_links_host>` paths `/..*/p/..*`, `/..*/pl/..*`, `/search`, `/..*/facet_collection/..*`, `/checkout/cart`, `/checkout/wa/cart`, `/`, ``; `https://<app_links_host_superstore>`; `https://<app_links_host_supplier>` + `/panel/v3/new/..*/supplier`; custom scheme `@string/app_uri_scheme` host `@string/uri_host` (values in `res/values/strings.xml` — verify, expected `meesho` / `meesho.com` etc.) |
| `…main.deeplinkresolver.SharedTextRedirectionActivity` | exported, `ACTION_SEND` `text/plain` (share-in) |
| `…main.deeplinkresolver.DeeplinkResolverActivity` | internal router |
| `…search.impl.SearchActivity` | exported, `ACTION_SEARCH` |
| `…account.impl.mybank.MyBankActivity` | exported, `<scheme>://my_bank_details` |
| `…inapppopup.impl.InAppPopupActivity` | **in-app promo/upsell popups** |
| `…notifystore.NotificationStoreActivity` / `…AllActivity` | in-app notification inbox |
| `…reel.impl.ui.ReelsActivity` | short-video reels (autoplay candidate) |
| `…pip.impl.WebViewPipActivity`, `…MultiMediaPipActivity` | PiP players |
| `…web.impl.WebViewActivity`, `…external.ui.ThirdPartyWebViewActivity`, `…external.ui.BNPLWebViewActivity`, `…product.webviewActivity.SingleProductWebView*` | WebViews (CSS/JS-injection surfaces) |
| `…loyalty.impl.CoinsLandingActivity`, `…explore.impl.ExploreActivity`, `…widget.impl.LandingPageActivity` | loyalty / explore / widget |
| Services | `com.meesho.pushnotify.MyFcmService` (exported FCM), `…RealNotificationForegroundService`, GMS `AppMeasurementService/JobService`, `com.google.firebase.messaging.FirebaseMessagingService` |
| Receivers (exported) | `com.appsflyer.SingleInstallBroadcastReceiver` (INSTALL_REFERRER), `com.meesho.supply.main.AppUpdateReceiver` (**MY_PACKAGE_REPLACED** → in-app-update trigger), `com.meesho.login.impl.otp.OtpReceiver` (SMS_RETRIEVED), `com.meesho.download.DownloadCompletionListener`, `…pushnotify.pullnotifications.br.PNPullAlarmReceiver`, `…PNAlarmReceiver`, `…RebootBroadcastReceiver` (BOOT_COMPLETED → **periodic pull-notification alarms**), `com.otpless.main.OtplessZeroTapReceiver`, `FirebaseInstanceIdReceiver` |
| Providers | `com.facebook.FacebookContentProvider` (`@string/facebook_app_authority`), FileProvider `com.meesho.supply.provider`, `…coldstart`, fonts, `FacebookInitProvider`, `FirebaseInitProvider` |
| meta-data | `com.facebook.sdk.ApplicationId/ClientToken`, `com.mixpanel.android.MPConfig.*`, `com.truecaller.android.sdk.ClientId`, `firebase_analytics_collection_deactivated=false`, `firebase_performance_collection_enabled=false`, `com.google.android.gms.version` |
| `<queries>` | CustomTabsService intent; `upi://pay`, `upi://mandate`, `upi://*`; `juspay://pay`; `credpay://checkout`; share packages (WhatsApp, Telegram, …); Truecaller; Facebook; AppsFlyer `INSTALL_PROVIDER` |

### Trackers (εxodus 28.8)
AppsFlyer, Facebook Analytics, Facebook Share, Google Crashlytics, Google Firebase Analytics.
Manifest additionally shows **Mixpanel**, **Truecaller SDK**, **Otpless** (login), **JusPay** (payments),
**Verloop** (support chat), **com.shield.android** (anti-fraud).

### Permissions (39 in 28.8; dangerous ones)
`READ_SMS`, `SEND_SMS`, `READ_CONTACTS`, `WRITE_CONTACTS`, `READ_PHONE_STATE`,
`READ_PHONE_NUMBERS`, `RECORD_AUDIO`/`AUDIO_CAPTURE`, `ACCESS_FINE_LOCATION` +
`ACCESS_COARSE_LOCATION` (foreground), `READ/WRITE_EXTERNAL_STORAGE`,
`DETECT_SCREEN_CAPTURE`, `SCHEDULE_EXACT_ALARM`, `AD_ID`, `ACCESS_ADSERVICES_*`,
`POST_NOTIFICATIONS`, `FOREGROUND_SERVICE(+DATA_SYNC)`, `GET_SIGNATURES`,
`com.truecaller.permission.sdk.internal.read_account_state`, custom
`com.meesho.supply.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.

## 2. Patches that can be created (proposal, Morphe conventions)

All structural/fail-safe; none touch payments, login or server-side entitlement.
`com.shield.android` (anti-fraud) is deliberately **not** targeted — breaking it breaks checkout.

| # | Patch | Default | Mechanism (anchors above) | Limitations |
|---|---|---|---|---|
| 1 | **Block analytics & tracking** | on | nop void call sites into `com.appsflyer.AppsFlyerLib` (logEvent/logPurchase), `com.mixpanel.android.mpmetrics.MixpanelAPI` (track/identify), `com.facebook.appevents.AppEventsLogger`, `FirebaseAnalytics`/`AppMeasurement` (logEvent/setUserProperty/setUserId/setCurrentScreen); optionally manifest `android:enabled=false` on `AppMeasurementService/JobService` | FCM push (`MyFcmService`) untouched; Shield may flag missing telemetry — cosmetic |
| 2 | **Remove dangerous permissions** | off | resource patch: delete `uses-permission` entries (SMS/contacts/phone/audio/location/storage/AD_ID/AD_SERVICES/DETECT_SCREEN_CAPTURE/SCHEDULE_EXACT_ALARM) | OTP autofill (OtpReceiver uses SMS_RETRIEVED — no READ_SMS needed), support voice calls (Verloop), Truecaller login and location-based delivery may degrade; runtime prompts disappear |
| 3 | **Hide sponsored listings / ad slots** | on (verify) | first-party ads: force boolean ad gates (`isAd`/`isSponsored`/`shouldShowAd`) false in classes referencing "sponsored"/"adslot" strings; web surfaces via CSS/JS injection into `WebViewActivity` etc. (Amazon-style) | Server still returns ad items; patch hides/filters client-side. Needs DEX anchors from 29.7 |
| 4 | **Disable in-app popup upsells** | off | manifest `android:enabled=false` on `InAppPopupActivity`, or finish-after-onCreate | May block required flows (e.g. mandates); verify which popups route there |
| 5 | **Stop pull-notification alarms** | off | manifest `enabled=false` on `PNPullAlarmReceiver`/`PNAlarmReceiver`/`RebootBroadcastReceiver` | FCM push keeps working; pull-based marketing nags stop |
| 6 | **Bypass forced update** | off | structural rewrite of Play Core `AppUpdateInfo.updateAvailability()`→1 / `isUpdateTypeAllowed()`→false (same as MUBI); `AppUpdateReceiver` left alone | Old builds can break; API changes break playback-independent features |
| 7 | **Prefer external browser over Custom Tabs** | on | remove the `CustomTabsService` `<queries><intent>` entry (present in manifest) | No effect where `CustomTabsIntent.launchUrl` is called unconditionally |
| 8 | **Make share-in / bank deep-link activities non-exported** | off | `SharedTextRedirectionActivity`, `MyBankActivity` → `exported=false` | Security hardening; internal navigation unaffected |
| 9 | **Disable reels autoplay** | on (verify) | `setPlayWhenReady(true)`→`false` in `…reel…` classes (ExoPlayer/Media3 expected — verify) | Opening a reel plays normally |
| 10 | **Allow screenshots & screen recording** | off | clear `FLAG_SECURE` (0x2000) literals at `Window.addFlags/setFlags`, flip `SurfaceView.setSecure(true)` | Payment screens (JusPay/MyBank) may intentionally set it; capture may be black there anyway |
| 11 | **Change package name** | off | manifest package, `com.meesho.supply.*` permissions/authorities, taskAffinity, label (never component class names) | Re-signed clone: JusPay, Truecaller, Otpless, Google sign-in, Shield may fail |
| 12 | **Packaging toolkit** | off | `dependsOn` shared spoof-signature / install-source / GmsCore patches | No Play Integrity bypass; no billing fix |

Deliberately **excluded**: unlocking coins/balance ("Unlimited Money" mod claims are server-side
and fake), Shield removal, SMS/OTP interception changes, region or price manipulation — all
server-side or anti-fraud protected.

## 3. Anchors to verify against the real APKM 29.7 (jadx/apktool) before implementing

1. `versionCode` of 29.7 (≈870) and `minSdk 21`, `targetSdk` (compileSdk 34 in the Mar-2025 manifest).
2. `res/values/strings.xml`: `app_uri_scheme`, `uri_host`, `app_links_host`,
   `app_links_host_supplier`, `app_links_host_superstore`, `facebook_app_authority`.
3. DEX: ad-slot model — search strings `sponsored`, `adslot`, `"Ad"`, `isSponsored`; confirm class names.
4. DEX: ExoPlayer/Media3 presence for reels (`DefaultBandwidthMeter`, `setPlayWhenReady`).
5. DEX: Mixpanel API owner (`com.mixpanel.android.mpmetrics.MixpanelAPI` methods and return types — only void call sites may be nop'ed).
6. DEX: `FLAG_SECURE` call sites (which activities set it).
7. DEX: Play Core `AppUpdateInfo` usage + `AppUpdateReceiver` logic.
8. Manifest of 29.7 vs the Mar-2025 one above: exported list may have changed.
9. Whether `InAppPopupActivity` hosts only marketing popups.
