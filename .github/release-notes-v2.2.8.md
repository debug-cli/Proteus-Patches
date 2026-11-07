v2.2.8 (pre-release) — 2026-11-07 (re-cut #5)

New in this build (supersedes the earlier v2.2.8 assets):

- Meesho (com.meesho.shopping) support — XAPK split bundles; app-scoped entries,
  off by default, unverified on device:
  - "Meesho: Block analytics & tracking SDKs"
  - "Meesho: Disable video autoplay (ExoPlayer)"
  - "Meesho: Prefer external browser over Custom Tabs"
  Targets: 29.9, 29.8, 29.7 (vc 872), 29.6 (vc 870), 29.5 (vc 868) — store builds
  observed 2026-11-06/07. No APK decompiled — no sponsored-suppression, AMOLED,
  or integrity-gate patches yet. See docs/MEESHO.md.
- Plus, unchanged from the previous re-cut: Amazon AMOLED resource-encoder fix
  (android:-framework attributes only — resolves the mShop 32.19.0.300
  "Unknown attribute name: colorSurface" build failure); Mereframe diagnostic
  crash log now records app versionName/versionCode; the native-integrity
  bypass logs when its catch is planted.
- Plus everything from earlier re-cuts: universal Disable video autoplay
  (ExoPlayer/Media3) heuristic; Mint/HT app-scoped entries (Block analytics,
  Disable autoplay, Prefer external browser); Indian-market tracker markers;
  the Mint & Hindustan Times analysis doc; all v2.2.7 content (Amazon AMOLED,
  Reddit listing fix, Brave patches).

Known limitations: everything is unverified on device; no binary-specific
Meesho patches until the APK is provided; Premium unlocks are server-side and
impossible; the Meesho compatibility list covers only the versions above.
v2.2.4 remains the latest full release. All v2.2.5+ builds are pre-releases, unchanged.
