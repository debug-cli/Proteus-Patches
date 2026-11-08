# Table Notes — patch status and analysis

Table Notes - Mobile Excel (`vivekagarwal.playwithdb`, Solution Developers /
Vivek Agrawal, Mumbai) is a productivity spreadsheet/notes app for Android.
This document is the current status page for the Table Notes entries in the
Proteus Patches bundle.

## Distribution facts (public listings, observed 2026-11-08)

| Field | Value |
|---|---|
| Package | `vivekagarwal.playwithdb` |
| Latest seen | **20261030** (XAPK, 38.8 MB, Android 8.0+), **20260916**, **20260831**, **20260524**, **9000200**, older 9000xxx builds |
| Form | **XAPK split bundles** — patch in XAPK mode |
| Min SDK | API 26 / Android 8.0+ (listings) |
| Version codes | Not published in the listings — pinned version-only |

**No `vivekagarwal.playwithdb` APK/XAPK has been decompiled in this build environment.**

## What the bundle ships for Table Notes

App-scoped entries (off by default, unverified on device — thin wrappers over
the universal heuristics):

- **Table Notes: Block analytics & tracking SDKs** — Firebase Analytics,
  AppsFlyer, Adjust, Facebook AppEvents and similar markers (same universal
  heuristic as 'Block analytics & tracking SDKs')
- **Table Notes: Block ads & ad SDKs** — AdMob, Unity Ads, Facebook Audience
  Network, AppLovin, IronSource and similar ad load/show call sites (same
  universal heuristic as 'Block ads & ad SDKs')
- **Table Notes: Prefer external browser over Custom Tabs** — opens links in the
  default external browser instead of an in-app Custom Tab

Plus, app-agnostic and visible for Table Notes today with no new code: universal
**Block analytics & tracking SDKs**, **Disable video autoplay (ExoPlayer/Media3)**,
**Remove dangerous permissions**, **Disable PairIP license check**,
**Fix Firebase after re-signing**, **Prefer external browser over Custom Tabs**.

## Not in the bundle, and why

- **Premium / export unlock** (PDF · Word · XLS · XLSX reports, cloud backup,
  collaborator limits) — Table Notes gates reports behind "pay or watch ad" and
  a premium plan; the paywall code paths were not inspected (no APK decompiled),
  so there is nothing to safely target. A premium-unlock patch would require
  bytecode-level analysis of the actual build (as the Reddit "Unlock Premium"
  patch was) and is intentionally not shipped as a placeholder.
- **Targeted ad suppression around report generation** — the heuristic
  'Block ads & ad SDKs' entry above covers common ad SDKs generally, but
  suppressing the specific interstitials/rewarded ads that gate report
  generation needs the APK (which ad SDK renders them, and whether a premium
  flag exists on the report models). Unverified on device.
- **Cloud sync / collaboration bypass** — sync and multi-user editing are
  server-side; nothing client-side to patch without inspecting the build
- **Integrity self-defense bypass** — if re-signed Table Notes builds crash or
  black-screen, it needs obfuscated wrapper-flow analysis on an APK; capture
  `logcat` around launch instead

## Verification checklist (device)

1. Bundle reports the current version in the manager.
2. Table Notes shows as supported (target must match your installed build:
   20261030 / 20260916 / 20260831) and the XAPK form is used.
3. Patch list under Table Notes shows the three scoped entries + universals.
4. If the patcher rejects the file (single .apk selected instead of the
   XAPK/bundle), pull **all** splits (`adb shell pm path vivekagarwal.playwithdb`),
   or use the universal variant.
5. If install fails or the app crashes after patching, capture `logcat` around
   launch and send it — no app-specific crash fingerprint exists yet.
