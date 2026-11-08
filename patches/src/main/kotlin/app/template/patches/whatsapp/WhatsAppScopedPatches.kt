// Scoped WhatsApp entries — thin wrappers over universal implementations
// (no app-specific fingerprints were written; no WhatsApp APK was decompiled
// in this build environment). Unverified on device.
// WhatsApp ships as XAPK split bundles (ApkFileType.XAPK).
package app.template.patches.whatsapp

import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.WHATSAPP_COMPATIBILITY
import app.template.patches.shared.logUniversal
import app.template.patches.shared.unimplementedAppPatch
import app.template.patches.shared.manifest.removeDangerousPermissionsPatch
import app.template.patches.shared.media.disableAutoplayPatch
import app.template.patches.shared.misc.externalBrowserPatch
import app.template.patches.shared.misc.stripLoggingPatch
import app.template.patches.shared.privacy.blockAdsSdksPatch
import app.template.patches.shared.privacy.blockAnalyticsSdksPatch
import app.template.patches.shared.privacy.blockScreenCapturePatch
import app.template.patches.shared.privacy.emptyTelephonyIdsPatch
import app.template.patches.shared.privacy.universalAllowScreenCapturePatch

@Suppress("unused")
val whatsappBlockAnalyticsSdksPatch = bytecodePatch(
    name = "WhatsApp: Block analytics & tracking SDKs",
    description = """
        Attempts to remove calls to known analytics SDK methods using the
        universal heuristic. Custom WhatsApp/Meta telemetry, obfuscated,
        reflective and native reporting may still operate. Unverified on device.
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
        Attempts to remove known ad SDK load/show calls using the universal
        heuristic. WhatsApp's first-party, obfuscated, reflective and native ads
        may be unaffected; rewarded-ad flows may break. Unverified on device.
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
        Uses the universal permission-removal patch on WhatsApp. Its default
        categories remove camera, microphone, contacts, phone, SMS and other
        permissions, which can break calls, media or registration. For control
        over categories and keep lists, use the universal patch directly.
        Unverified on device.
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
        Attempts to stop autoplay by rewriting literal ExoPlayer/Media3 calls
        using the universal heuristic. Coverage of Status is not established;
        some videos may stop playing entirely. Unverified on device.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(WHATSAPP_COMPATIBILITY)
    dependsOn(disableAutoplayPatch)

    execute {
        logUniversal("WhatsApp: enabling ExoPlayer autoplay block.")
    }
}

@Suppress("unused")
val whatsappKeepDeletedMessagesPatch = bytecodePatch(
    name = "WhatsApp: Keep deleted messages",
    description = """
        UNIMPLEMENTED PLACEHOLDER. Does not preserve deleted messages.
        Selecting this entry fails; app-specific APK analysis is required.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(WHATSAPP_COMPATIBILITY)

    execute {
        unimplementedAppPatch("WhatsApp: Keep deleted messages")
    }
}

@Suppress("unused")
val whatsappReadReceiptsPatch = bytecodePatch(
    name = "WhatsApp: Read receipts (no blue ticks)",
    description = """
        UNIMPLEMENTED PLACEHOLDER. Does not prevent read receipts or blue ticks from being sent.
        Selecting this entry fails; app-specific APK analysis is required.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(WHATSAPP_COMPATIBILITY)

    execute {
        unimplementedAppPatch("WhatsApp: Read receipts (no blue ticks)")
    }
}

@Suppress("unused")
val whatsappTypingIndicatorPatch = bytecodePatch(
    name = "WhatsApp: Typing indicator (hide when typing)",
    description = """
        UNIMPLEMENTED PLACEHOLDER. Does not hide the typing indicator.
        Selecting this entry fails; app-specific APK analysis is required.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(WHATSAPP_COMPATIBILITY)

    execute {
        unimplementedAppPatch("WhatsApp: Typing indicator (hide when typing)")
    }
}

@Suppress("unused")
val whatsappUsageDataPatch = bytecodePatch(
    name = "WhatsApp: Limit usage data (diagnostics & analytics)",
    description = """
        Limits diagnostic and analytics data WhatsApp sends, using the same
        universal heuristic as 'Block analytics & tracking SDKs'.

        Applies to com.whatsapp only. Heuristic; obfuscated or reflection-based
        reporting is not covered. Unverified on device.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(WHATSAPP_COMPATIBILITY)
    dependsOn(blockAnalyticsSdksPatch)

    execute {
        logUniversal("WhatsApp: enabling universal analytics blocking for usage data.")
    }
}

@Suppress("unused")
val whatsappViewOnceMediaPatch = bytecodePatch(
    name = "WhatsApp: View once media (allow reopen & save)",
    description = """
        UNIMPLEMENTED PLACEHOLDER. Does not enable reopening or saving view-once media.
        Selecting this entry fails; app-specific APK analysis is required.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(WHATSAPP_COMPATIBILITY)

    execute {
        unimplementedAppPatch("WhatsApp: View once media (allow reopen & save)")
    }
}

@Suppress("unused")
val whatsappAppIconPatch = bytecodePatch(
    name = "WhatsApp: App icon (classic or premium style)",
    description = """
        UNIMPLEMENTED PLACEHOLDER. Does not change the launcher icon.
        Selecting this entry fails; app-specific APK analysis is required.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(WHATSAPP_COMPATIBILITY)

    execute {
        unimplementedAppPatch("WhatsApp: App icon (classic or premium style)")
    }
}

@Suppress("unused")
val whatsappModsSettingsEntryPatch = bytecodePatch(
    name = "WhatsApp: Mods settings entry",
    description = """
        UNIMPLEMENTED PLACEHOLDER. Does not add an entry to WhatsApp Settings.
        Selecting this entry fails; app-specific APK analysis is required.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(WHATSAPP_COMPATIBILITY)

    execute {
        unimplementedAppPatch("WhatsApp: Mods settings entry")
    }
}

@Suppress("unused")
val whatsappModsSettingsResourcesPatch = bytecodePatch(
    name = "WhatsApp: Mods settings resources",
    description = """
        UNIMPLEMENTED PLACEHOLDER. Does not add settings resources or strings.
        Selecting this entry fails; app-specific APK analysis is required.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(WHATSAPP_COMPATIBILITY)

    execute {
        unimplementedAppPatch("WhatsApp: Mods settings resources")
    }
}

@Suppress("unused")
val whatsappStripLoggingPatch = bytecodePatch(
    name = "WhatsApp: Strip logging (android.util.Log)",
    description = """
        Attempts to remove matching android.util.Log calls using the universal
        heuristic. Other logging paths can still leak data. Unverified on device.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(WHATSAPP_COMPATIBILITY)
    dependsOn(stripLoggingPatch)

    execute {
        logUniversal("WhatsApp: enabling universal logging strip.")
    }
}

@Suppress("unused")
val whatsappBlockScreenCapturePatch = bytecodePatch(
    name = "WhatsApp: Block screenshots & screen recording",
    description = """
        Attempts to add FLAG_SECURE to recognizable activity windows and to
        secure SurfaceViews using the universal heuristic. It cannot guarantee
        protection of every screen. Do not combine with 'WhatsApp: Allow
        screenshots & screen recording'. Unverified on device.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(WHATSAPP_COMPATIBILITY)
    dependsOn(blockScreenCapturePatch)

    execute {
        logUniversal("WhatsApp: enabling universal screen-capture block.")
    }
}

@Suppress("unused")
val whatsappEmptyTelephonyIdsPatch = bytecodePatch(
    name = "WhatsApp: Empty telephony identifiers",
    description = """
        Replaces TelephonyManager ID getters (getDeviceId, getImei, getMeid,
        getSubscriberId, getLine1Number, getSimSerialNumber,
        getDeviceSoftwareVersion) with empty strings, using the same universal
        heuristic as 'Empty telephony identifiers'.

        Applies to com.whatsapp only. Heuristic; may break phone-number-based
        registration or re-verification. Unverified on device.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(WHATSAPP_COMPATIBILITY)
    dependsOn(emptyTelephonyIdsPatch)

    execute {
        logUniversal("WhatsApp: enabling universal telephony ID emptying.")
    }
}
