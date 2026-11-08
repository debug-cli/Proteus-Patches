// Proton Pass feature entries — thin wrappers over universal implementations
// (no app-specific fingerprints were written; no Proton Pass APK was decompiled
// in this build environment). Unverified on device.
// Proton Pass ships as XAPK split bundles (ApkFileType.XAPK).
package app.template.patches.protonpass

import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.PROTON_PASS_COMPATIBILITY
import app.template.patches.shared.logUniversal
import app.template.patches.shared.privacy.blockAdsSdksPatch
import app.template.patches.shared.privacy.blockAnalyticsSdksPatch
import app.template.patches.shared.privacy.universalAllowScreenCapturePatch

@Suppress("unused")
val protonPassAllowScreenCapturePatch = bytecodePatch(
    name = "Proton Pass: Allow screenshots & screen recording",
    description = """
        Attempts to remove FLAG_SECURE / secure SurfaceView settings in Proton
        Pass so screenshots and screen recording can work in the vault and
        profile screens, using the same universal heuristic as 'Allow screenshots
        & screen recording'.

        Applies to proton.android.pass only. Some screens (payment, biometric,
        banking) may still block capture independently. Unverified on device.
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
        Blocks Proton Pass telemetry and crash-reporting call sites using the
        same universal heuristics as 'Block analytics & tracking SDKs' and
        'Block ads & ad SDKs'.

        Applies to proton.android.pass only. Heuristic; obfuscated or
        reflection-based reporting is not covered. Unverified on device.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(PROTON_PASS_COMPATIBILITY)
    dependsOn(blockAnalyticsSdksPatch, blockAdsSdksPatch)

    execute {
        logUniversal("Proton Pass: enabling universal telemetry blocking.")
    }
}
