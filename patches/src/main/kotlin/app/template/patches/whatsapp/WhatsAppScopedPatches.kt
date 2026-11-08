// Scoped WhatsApp entries — thin wrappers over universal implementations
// (no app-specific fingerprints were written; no WhatsApp APK was decompiled
// in this build environment). Unverified on device.
// WhatsApp ships as XAPK split bundles (ApkFileType.XAPK).
package app.template.patches.whatsapp

import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.WHATSAPP_COMPATIBILITY
import app.template.patches.shared.logUniversal
import app.template.patches.shared.manifest.removeDangerousPermissionsPatch
import app.template.patches.shared.media.disableAutoplayPatch
import app.template.patches.shared.misc.externalBrowserPatch
import app.template.patches.shared.privacy.blockAdsSdksPatch
import app.template.patches.shared.privacy.blockAnalyticsSdksPatch
import app.template.patches.shared.privacy.universalAllowScreenCapturePatch

@Suppress("unused")
val whatsappBlockAnalyticsSdksPatch = bytecodePatch(
    name = "WhatsApp: Block analytics & tracking SDKs",
    description = """
        Blocks WhatsApp's analytics & tracking SDKs (Firebase Analytics,
        Meta/Graph API event reporting, Adjust, AppEvents and similar) using
        the same universal heuristic as 'Block analytics & tracking SDKs'.

        Applies to com.whatsapp only. Heuristic; obfuscated or
        reflection-based reporting is not covered. Unverified on device.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(WHATSAPP_COMPATIBILITY)
    dependsOn(blockAnalyticsSdksPatch)

    execute {
        logUniversal("WhatsApp: enabling universal heuristic analytics blocking.")
    }
}

@Suppress("unused")
val whatsappBlockAdsSdksPatch = bytecodePatch(
    name = "WhatsApp: Block ads & ad SDKs",
    description = """
        Blocks WhatsApp ad SDK call sites (AdMob, Unity Ads, Audience Network
        and similar) using the same universal heuristic as 'Block ads & ad
        SDKs'. Relevant to business messaging and click-to-chat surfaces.

        Applies to com.whatsapp only. Heuristic; obfuscated or reflection-based
        ad loading is not covered. Unverified on device.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(WHATSAPP_COMPATIBILITY)
    dependsOn(blockAdsSdksPatch)

    execute {
        logUniversal("WhatsApp: enabling universal heuristic ad-blocking.")
    }
}

@Suppress("unused")
val whatsappPreferExternalBrowserPatch = bytecodePatch(
    name = "WhatsApp: Prefer external browser over Custom Tabs",
    description = """
        Opens links in the default external browser instead of an in-app Custom
        Tab, using the same rewrite as the universal 'Prefer external browser
        over Custom Tabs' patch.

        Applies to com.whatsapp only. Covers Custom Tabs /
        CustomTabsIntent call sites only. Unverified on device.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(WHATSAPP_COMPATIBILITY)
    dependsOn(externalBrowserPatch)

    execute {
        logUniversal("WhatsApp: enabling external browser over Custom Tabs.")
    }
}

@Suppress("unused")
val whatsappAllowScreenCapturePatch = bytecodePatch(
    name = "WhatsApp: Allow screenshots & screen recording",
    description = """
        Attempts to remove FLAG_SECURE / secure SurfaceView settings in
        WhatsApp so screenshots and screen recording can work in chat and
        status screens, using the same universal heuristic as 'Allow
        screenshots & screen recording'.

        Applies to com.whatsapp only. Some screens (chat lock, payment, media
        viewer) may still block capture independently. Unverified on device.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(WHATSAPP_COMPATIBILITY)
    dependsOn(universalAllowScreenCapturePatch)

    execute {
        logUniversal("WhatsApp: enabling universal screen-capture allowance.")
    }
}

@Suppress("unused")
val whatsappRemoveDangerousPermissionsPatch = bytecodePatch(
    name = "WhatsApp: Remove dangerous permissions",
    description = """
        Removes dangerous permission declarations from WhatsApp's manifest
        using the same universal heuristic as 'Remove dangerous permissions'.

        Applies to com.whatsapp only. Review the keep list before enabling —
        removing Camera, Microphone, Location or Storage declarations may
        disable features that depend on them. Unverified on device.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(WHATSAPP_COMPATIBILITY)
    dependsOn(removeDangerousPermissionsPatch)

    execute {
        logUniversal("WhatsApp: enabling universal dangerous-permission removal.")
    }
}

@Suppress("unused")
val whatsappDisableAutoplayPatch = bytecodePatch(
    name = "WhatsApp: Disable video autoplay (Status)",
    description = """
        Stops video autoplay in WhatsApp Status and media viewers (ExoPlayer/
        Media3) using the same heuristic as 'Disable video autoplay
        (ExoPlayer/Media3)'.

        Applies to com.whatsapp only. Heuristic; see the universal entry for
        limitations. Unverified on device.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(WHATSAPP_COMPATIBILITY)
    dependsOn(disableAutoplayPatch)

    execute {
        logUniversal("WhatsApp: enabling ExoPlayer autoplay block.")
    }
}
