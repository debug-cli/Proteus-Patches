# Projectivy Launcher 4.73 (98)

`Limit Google telemetry` removes the manifest permissions for advertising IDs,
Ad Services attribution and the Play install referrer. It also sets Firebase
Analytics and Performance to deactivated and disables Crashlytics automatic
collection. The supplied 4.73 APK already disables automatic collection for all
three; the deactivation flags add protection against runtime re-enablement for
Analytics and Performance.

This is **not** a complete tracker removal: SDK code, Firebase Installations,
Remote Config, explicit Crashlytics sends and other network traffic may still be
present. Confirm behavior on a device and audit network traffic before relying
on it for privacy. Removing the install-referrer permission can affect attribution.
The patch is restricted to the supplied APK version and has not been device-tested.

`Unlock local premium features` is an experimental, opt-in bytecode patch.
In the supplied APK, `PTApplication.y` exposes a boolean restricted-mode
StateFlow. The patch initializes it to false and forces the local setter to
publish false. Inspection shows that the built-in custom-font loader, parental
control app limit and wallpaper-related code consult this state. This does not
add new features or create a server-side purchase, and other entitlement checks
may remain. Test font selection, wallpaper providers and parental controls on a
TV before relying on them; in particular, do not treat untested parental controls
as a security boundary. Neither patch has been built or tested on a device here.

For a static regression check of the supplied APK's fingerprint assumptions, run
`python patches/src/test/projectivy/verify_fingerprint.py /path/to/ProjectivyLauncher-4.73-c98-xda-release.apk`
with `androguard` installed. This check does not apply the patches or test the app.
