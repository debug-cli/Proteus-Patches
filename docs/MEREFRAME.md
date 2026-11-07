# Mereframe — patch status and crash triage

Mereframe (`app.unnamed.rawrecording`, pinned target **0.8.4 / vc 118**) contains a
self-defense layer that rejects re-signed builds, plus a native integrity check
inside the camera native pipeline (`libunnamed_raw.so`). This document is the
current status page and the triage runbook for field crashes.

## Patches in the bundle

| Patch | Default | Role |
|---|---|---|
| Allow re-signed/sideloaded installs | **on** | Neutralizes the startup install-legality verdict → app starts at all |
| Bypass native integrity checks | **on** | Catches `SecurityException("app integrity check failed")` at the camera session-opening wrapper |
| Diagnostic crash log | off | Writes the full stack to `Downloads/mereframe-crash-*.txt`, then chains to the normal crash path |
| Unlock Pro features | off | Drops the entitlement ownership check (nv.e). **WARNING:** on realme/Android 16 enabling it crashed capture on the one device tested — keep off unless deliberately testing |
| Fix Firebase after re-signing (universal) | off | Needed if Firebase services break after re-signing |

## Reading the 2026-11-07 crash report (realme RMX5110, Android 16)

```
java.lang.SecurityException: app integrity check failed
  at app.unnamed.rawrecording.b.B1e6ad8077f6880e309d8da4b.nd2cccb916f63efbcf8b5b19b(Native Method)
  at app.unnamed.rawrecording.b.B1e6ad8077f6880e309d8da4b.d(SourceFile:8)
  at Jm.m(SourceFile:27) …
```

This is **precisely the frame "Bypass native integrity checks" wraps**
(`Lapp/unnamed/rawrecording/b/B1e6ad8077f6880e309d8da4b;->d(ZZZZFZ)[J`). With the
bypass compiled in, the `SecurityException` is caught *inside* `d` and the
substitution result (`long[]{0L, 1L}`) is returned — the JVM never unwinds, the
log file never gets written. **The existence of any such log file therefore
proves the bypass was not effective in the crashing build.**

So the field outcome says one of these happened:

1. The installed Mereframe APK is **not 0.8.4 (vc 118)** → the manager treats
   all Mereframe patches as incompatible and excludes them (the app still
   starts because "Allow re-signed installs" would also be excluded → check
   whether the app even reached the camera, or crashed at first frame — if
   the refusal screen shows, the gate ran anyway).
   → Send the exact `versionName`/`versionCode` from Settings → Apps →
   Mereframe. The crash log now (after the 2026-11-07 re-cut) prints it as
   `app:` in the header line.
2. The bundle in the manager is **stale** (an older .mpp that predates the
   bypass). → Remove the bundle, re-add it, confirm it reports **v2.2.8**.
3. The bypass **silently did not apply** and the manager shows the failure.
   → The patching log now prints: `Bypass native integrity: session-wrapper
   catch planted…` when it applied. If that line is absent from the patching
   output, the fingerprint did not match the build — capture that output and
   the APK version we need (or the APK itself) to re-pin.
4. The user manually unchecked "Bypass native integrity checks".

## Required device checklist before reporting a crash again

1. Manager shows the bundle as **v2.2.8**.
2. Mereframe target shows **0.8.4 (vc 118)** as supported. If not, STOP —
   no Mereframe patch will apply; send the version.
3. Patches checked: **Allow re-signed/sideloaded installs** + **Bypass
   native integrity checks** + (only if this is a diagnostic run) **Diagnostic
   crash log**. **Unlock Pro features stays OFF.**
4. Check the patching log contains: `Bypass native integrity: session-wrapper
   catch planted…`.
5. If it still crashes: attach the newest `Downloads/mereframe-crash-*.txt`
   (it now contains `app: versionName=… versionCode=…`) — that header tells
   us which build actually ran and which patches were in it.

## AMOLED resource fix (Amazon — same session)

The Amazon report (`Unknown attribute name: <item name="colorSurface">#ff000000</item>`)
was the AMOLED theme pass writing **Material library attribute names** into
`<item>` elements — the reandroid resource encoder cannot re-resolve those and
aborts the whole build. Theme-item writes are now restricted to `android:`
framework attributes (a hard guard also drops any later non-framework additions);
the Material surfaces go black via the `<color>`-token pass and the WebView CSS
instead. Amazon builds after the 2026-11-07 re-cut should now produce a valid
package. AMOLED on Amazon remains unverified on device.
