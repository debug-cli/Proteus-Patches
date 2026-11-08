# WhatsApp — patch status and analysis

WhatsApp Messenger (`com.whatsapp`, WhatsApp LLC / Meta) is a communication
app for Android. This document is the current status page for the WhatsApp
entries in the Proteus Patches bundle.

## Distribution facts (public listings, observed 2026-11-08)

| Field | Value |
|---|---|
| Package | `com.whatsapp` |
| Latest seen | **2.26.43.76** (versionCode 264307627, XAPK, 153.1 MB arm64-v8a), **2.26.43.74**, **2.26.41.77** (versionCode 264107700) |
| Form | **XAPK split bundles** — patch in XAPK mode; standalone APK variants also exist |
| Min SDK | API 23 / Android 6.0+ (listings) |
| Version codes | 2.26.43.76=264307627, 2.26.41.77=264107700 (observed); 2.26.43.74 ships without public code |

**No `com.whatsapp` APK/XAPK has been decompiled in this build environment.**

## What the bundle ships for WhatsApp

App-scoped entries (off by default, unverified on device — thin wrappers over
the universal heuristics):

- **WhatsApp: Block analytics & tracking SDKs** — Firebase Analytics,
  Meta/Graph API event reporting, Adjust, AppEvents and similar markers
- **WhatsApp: Block ads & ad SDKs** — AdMob, Unity Ads, Audience Network
  and similar ad load/show call sites
- **WhatsApp: Prefer external browser over Custom Tabs** — opens links in the
  default external browser instead of an in-app Custom Tab
- **WhatsApp: Allow screenshots & screen recording** — attempts to clear
  `FLAG_SECURE` / `setSecure(true)` literals
- **WhatsApp: Remove dangerous permissions** — manifest permission declarations
  (contacts, location, microphone, camera, storage)
- **WhatsApp: Disable video autoplay (Status)** — ExoPlayer/Media3
  `setPlayWhenReady(true)` sites in Status and media viewers

Plus, app-agnostic and visible for WhatsApp today with no new code: universal
**Block analytics & tracking SDKs**, **Block ads & ad SDKs**,
**Disable video autoplay (ExoPlayer/Media3)**, **Remove dangerous permissions**,
**Fix Firebase after re-signing**, **Prefer external browser over Custom Tabs**,
**Allow screenshots & screen recording**.

## Not in the bundle, and why

- **Premium / subscription unlock** — WhatsApp is a free app with no consumer
  premium tier. There is nothing to unlock. WhatsApp Business has some paid
  features, but those are server-validated entitlements and cannot be patched
  client-side.
- **Hide online status / last seen** — status is negotiated server-side;
  local UI suppression would need app-specific bytecode analysis of the
  `UserStatus` / `PrivacySettings` classes
- **Disable read receipts (blue ticks)** — read-receipt state is negotiated
  server-side per message; local patch would need the exact message-model
  field and ack-send call sites
- **Anti-delete / prevent message deletion** — requires locating the
  message-delete RPC handler and the local DB (SQLite/Room) deletion path
- **Status downloader / save viewer** — needs the Status media cache path and
  `FileProvider` / `ContentResolver` call sites
- **Chat lock / fingerprint bypass** — biometric prompt is wired to the app's
  own `BiometricPrompt` wrapper; needs the auth-callback class names
- **WhatsApp Pay / UPI unlock** — entitlements are server-issued; not
  patchable client-side
- **Disable disappearing messages enforcement** — server-controlled per-chat
  timer; local suppression of the UI toggle only

## Verification checklist (device)

1. Bundle reports the current version in the manager.
2. WhatsApp shows as supported (target must match your installed build:
   2.26.43.76 / 2.26.43.74 / 2.26.41.77) and the XAPK form is used.
3. Patch list under WhatsApp shows the six scoped entries + universals.
4. If the patcher rejects the file (single .apk selected instead of the
   XAPK/bundle), pull **all** splits (`adb shell pm path com.whatsapp`),
   or use the universal variant.
5. WhatsApp uses end-to-end encryption — no local patch can bypass E2EE or
   read another user's messages. Server-validated entitlements (Pay, verified
   business badges) are not patchable.
6. If install fails or the app crashes after patching, capture `logcat` around
   launch and send it — no app-specific crash fingerprint exists yet.

## Verification ceiling

This analysis is static research only — public listings (APKPure, APKFab,
APK20) were consulted for version and package metadata. **No
`com.whatsapp` APK/XAPK has been decompiled in this build environment** (no
JVM/Gradle, no jadx/apktool/baksmali, and the APK mirrors are not reachable
from the sandbox). All register-level, class-name, and method-name claims for
app-specific patches are unknown until an APK is dumped and audited.

Only the six universal-heuristic wrappers above should be considered shippable
without further analysis. Everything else is deferred pending on-device
verification.
