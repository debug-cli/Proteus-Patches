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

@Suppress("unused")
val protonPassRemoveNagUIPatch = bytecodePatch(
    name = "Proton Pass: Remove upgrade nag UI",
    description = """
        Hides Proton Pass upgrade-to-Plus banners, paywall buttons and nag
        interstitials in the vault list, profile and security center screens.

        Requires app-specific bytecode analysis of the nag UI call sites — no
        Proton Pass APK was decompiled in this build environment. Unverified on
        device.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(PROTON_PASS_COMPATIBILITY)

    execute {
        logUniversal(
            "Proton Pass: remove upgrade nag UI — awaiting app-specific bytecode " +
                "analysis (no APK decompiled)."
        )
    }
}

@Suppress("unused")
val protonPassAutoLockPatch = bytecodePatch(
    name = "Proton Pass: Disable auto-lock / extend timeout",
    description = """
        Forces the vault auto-lock timer to never trigger, keeping the vault
        unlocked while the app is foregrounded.

        Requires app-specific bytecode analysis of the auto-lock timer fields —
        no Proton Pass APK was decompiled in this build environment. Unverified
        on device. Reduces at-rest protection; enable at your own discretion.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(PROTON_PASS_COMPATIBILITY)

    execute {
        logUniversal(
            "Proton Pass: disable auto-lock — awaiting app-specific bytecode " +
                "analysis (no APK decompiled)."
        )
    }
}

@Suppress("unused")
val protonPassClipboardPatch = bytecodePatch(
    name = "Proton Pass: Disable clipboard auto-clear",
    description = """
        Prevents Proton Pass from clearing copied credentials from the
        clipboard after the default timeout.

        Requires app-specific bytecode analysis of the clipboard-manager call
        sites — no Proton Pass APK was decompiled in this build environment.
        Unverified on device. Clipboard lingering is a local info-leak risk;
        enable at your own discretion.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(PROTON_PASS_COMPATIBILITY)

    execute {
        logUniversal(
            "Proton Pass: disable clipboard auto-clear — awaiting app-specific " +
                "bytecode analysis (no APK decompiled)."
        )
    }
}

@Suppress("unused")
val protonPassMultiVaultPatch = bytecodePatch(
    name = "Proton Pass: Unlock multiple vaults",
    description = """
        Removes the single-vault limit so free-tier users can create additional
        vaults.

        Requires app-specific bytecode analysis of the vault-limit fields — no
        Proton Pass APK was decompiled in this build environment. Unverified on
        device. The server may reject sync of extra vaults.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(PROTON_PASS_COMPATIBILITY)

    execute {
        logUniversal(
            "Proton Pass: unlock multiple vaults — awaiting app-specific " +
                "bytecode analysis (no APK decompiled)."
        )
    }
}

@Suppress("unused")
val protonPassBiometricPatch = bytecodePatch(
    name = "Proton Pass: Force biometric always-on",
    description = """
        Removes the "require PIN after restart" gate so biometric unlock works
        immediately without a prior PIN entry.

        Requires app-specific bytecode analysis of the biometric/PIN gate — no
        Proton Pass APK was decompiled in this build environment. Unverified on
        device. Weakens restart protection; enable at your own discretion.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(PROTON_PASS_COMPATIBILITY)

    execute {
        logUniversal(
            "Proton Pass: force biometric always-on — awaiting app-specific " +
                "bytecode analysis (no APK decompiled)."
        )
    }
}

@Suppress("unused")
val protonPassGeneratorPatch = bytecodePatch(
    name = "Proton Pass: Unlock password-generator options",
    description = """
        Removes the Plus-gated character-set and password-length options in the
        password generator.

        Requires app-specific bytecode analysis of the generator-options call
        sites — no Proton Pass APK was decompiled in this build environment.
        Unverified on device.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(PROTON_PASS_COMPATIBILITY)

    execute {
        logUniversal(
            "Proton Pass: unlock password-generator options — awaiting app-" +
                "specific bytecode analysis (no APK decompiled)."
        )
    }
}

@Suppress("unused")
val protonPassDarkWebPatch = bytecodePatch(
    name = "Proton Pass: Dark web monitoring local-only (cosmetic)",
    description = """
        Disables the server call for dark-web monitoring and shows a
        locally-cached "no breaches found" state instead of a paywall.

        Requires app-specific bytecode analysis of the dark-web monitoring call
        sites — no Proton Pass APK was decompiled in this build environment.
        Unverified on device. Cosmetic only; does not perform real monitoring.
    """.trimIndent(),
    default = false,
) {
    compatibleWith(PROTON_PASS_COMPATIBILITY)

    execute {
        logUniversal(
            "Proton Pass: dark web monitoring local-only — awaiting app-" +
                "specific bytecode analysis (no APK decompiled)."
        )
    }
}
