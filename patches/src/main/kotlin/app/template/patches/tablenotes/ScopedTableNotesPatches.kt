// Scoped Table Notes entries — all thin wrappers depending on the universal
// implementations (no app-specific fingerprints were written; no Table Notes
// APK was decompiled in this build environment). Unverified on device.
// Table Notes ships as XAPK split bundles (ApkFileType.XAPK).
package app.template.patches.tablenotes

import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.TABLENOTES_COMPATIBILITY
import app.template.patches.shared.logUniversal
import app.template.patches.shared.misc.externalBrowserPatch
import app.template.patches.shared.privacy.blockAdsSdksPatch
import app.template.patches.shared.privacy.blockAnalyticsSdksPatch

@Suppress("unused")
val tablenotesBlockAnalyticsSdksPatch = bytecodePatch(
    name = "Table Notes: Block analytics & tracking SDKs",
    description = """
        Attempts to remove calls to known analytics SDKs (Firebase Analytics,
        AppsFlyer, Adjust, Facebook AppEvents and similar) using the universal
        'Block analytics & tracking SDKs' heuristic.

        Applies to vivekagarwal.playwithdb only. Heuristic; obfuscated or
        reflection-based reporting is not covered. Unverified on device.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(TABLENOTES_COMPATIBILITY)
    dependsOn(blockAnalyticsSdksPatch)

    execute {
        logUniversal("Table Notes: enabling universal heuristic analytics blocking.")
    }
}

@Suppress("unused")
val tablenotesPreferExternalBrowserPatch = bytecodePatch(
    name = "Table Notes: Prefer external browser over Custom Tabs",
    description = """
        Opens links in the default external browser instead of an in-app Custom
        Tab, using the same rewrite as the universal 'Prefer external browser
        over Custom Tabs' patch.

        Applies to vivekagarwal.playwithdb only. Covers Custom Tabs /
        CustomTabsIntent call sites only. Unverified on device.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(TABLENOTES_COMPATIBILITY)
    dependsOn(externalBrowserPatch)

    execute {
        logUniversal("Table Notes: enabling external browser over Custom Tabs.")
    }
}

@Suppress("unused")
val tablenotesBlockAdsSdksPatch = bytecodePatch(
    name = "Table Notes: Block ads & ad SDKs",
    description = """
        Attempts to remove ad SDK load/show calls using the universal heuristic.
        Obfuscated, reflective, native and first-party ads may be unaffected;
        rewarded-ad flows may break. Unverified on device.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(TABLENOTES_COMPATIBILITY)
    dependsOn(blockAdsSdksPatch)

    execute {
        logUniversal("Table Notes: enabling universal heuristic ad-blocking.")
    }
}
