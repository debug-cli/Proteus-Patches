// Proton Pass feature entries — thin wrappers over universal implementations
// (no app-specific fingerprints were written; no Proton Pass APK was decompiled
// in this build environment). Unverified on device.
// Proton Pass ships as XAPK split bundles (ApkFileType.XAPK).
package app.template.patches.protonpass

import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.PROTON_PASS_COMPATIBILITY
import app.template.patches.shared.logUniversal
import app.template.patches.shared.unimplementedAppPatch
import app.template.patches.shared.privacy.blockAdsSdksPatch
import app.template.patches.shared.privacy.blockAnalyticsSdksPatch
import app.template.patches.shared.privacy.universalAllowScreenCapturePatch

@Suppress("unused")
val protonPassAllowScreenCapturePatch = bytecodePatch(
    name = "Proton Pass: Allow screenshots & screen recording",
    description = """
        Attempts to remove FLAG_SECURE / secure SurfaceView settings using the
        universal heuristic. Some screens may still block capture; allowing it
        can expose vault contents to other software. Unverified on device.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(PROTON_PASS_COMPATIBILITY)
    dependsOn(universalAllowScreenCapturePatch)

    execute {
        logUniversal("Proton Pass: enabling universal screen-capture allowance.")
    }
}

@Suppress("unused")
val protonPassBlockTelemetryPatch = bytecodePatch(
    name = "Proton Pass: Block telemetry & crash reporting",
    description = """
        Attempts to remove calls matching the universal analytics and ad SDK
        heuristics. This does not guarantee that Proton telemetry or crash
        reporting is blocked; custom, native, reflective and obfuscated paths
        are not covered. Unverified on device.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(PROTON_PASS_COMPATIBILITY)
    dependsOn(blockAnalyticsSdksPatch, blockAdsSdksPatch)

    execute {
        logUniversal("Proton Pass: enabling universal telemetry blocking.")
    }
}

@Suppress("unused")
val protonPassRemoveNagUIPatch = bytecodePatch(
    name = "Proton Pass: Remove upgrade nag UI",
    description = """
        UNIMPLEMENTED PLACEHOLDER. Does not hide upgrade banners or paywall screens.
        Selecting this entry fails; app-specific APK analysis is required.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(PROTON_PASS_COMPATIBILITY)

    execute {
        unimplementedAppPatch("Proton Pass: Remove upgrade nag UI")
    }
}

@Suppress("unused")
val protonPassAutoLockPatch = bytecodePatch(
    name = "Proton Pass: Disable auto-lock / extend timeout",
    description = """
        UNIMPLEMENTED PLACEHOLDER. Does not change the vault auto-lock behavior.
        Selecting this entry fails; app-specific APK analysis is required.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(PROTON_PASS_COMPATIBILITY)

    execute {
        unimplementedAppPatch("Proton Pass: Disable auto-lock / extend timeout")
    }
}

@Suppress("unused")
val protonPassClipboardPatch = bytecodePatch(
    name = "Proton Pass: Disable clipboard auto-clear",
    description = """
        UNIMPLEMENTED PLACEHOLDER. Does not change clipboard clearing.
        Selecting this entry fails; app-specific APK analysis is required.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(PROTON_PASS_COMPATIBILITY)

    execute {
        unimplementedAppPatch("Proton Pass: Disable clipboard auto-clear")
    }
}

@Suppress("unused")
val protonPassMultiVaultPatch = bytecodePatch(
    name = "Proton Pass: Unlock multiple vaults",
    description = """
        UNIMPLEMENTED PLACEHOLDER. Does not change vault limits or server entitlements.
        Selecting this entry fails; app-specific APK analysis is required.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(PROTON_PASS_COMPATIBILITY)

    execute {
        unimplementedAppPatch("Proton Pass: Unlock multiple vaults")
    }
}

@Suppress("unused")
val protonPassBiometricPatch = bytecodePatch(
    name = "Proton Pass: Force biometric always-on",
    description = """
        UNIMPLEMENTED PLACEHOLDER. Does not change biometric or PIN requirements.
        Selecting this entry fails; app-specific APK analysis is required.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(PROTON_PASS_COMPATIBILITY)

    execute {
        unimplementedAppPatch("Proton Pass: Force biometric always-on")
    }
}

@Suppress("unused")
val protonPassGeneratorPatch = bytecodePatch(
    name = "Proton Pass: Unlock password-generator options",
    description = """
        UNIMPLEMENTED PLACEHOLDER. Does not unlock password-generator options.
        Selecting this entry fails; app-specific APK analysis is required.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(PROTON_PASS_COMPATIBILITY)

    execute {
        unimplementedAppPatch("Proton Pass: Unlock password-generator options")
    }
}

@Suppress("unused")
val protonPassDarkWebPatch = bytecodePatch(
    name = "Proton Pass: Dark web monitoring local-only (cosmetic)",
    description = """
        UNIMPLEMENTED PLACEHOLDER. Does not monitor for breaches or provide a local monitoring result.
        Selecting this entry fails; app-specific APK analysis is required.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(PROTON_PASS_COMPATIBILITY)

    execute {
        unimplementedAppPatch("Proton Pass: Dark web monitoring local-only (cosmetic)")
    }
}
