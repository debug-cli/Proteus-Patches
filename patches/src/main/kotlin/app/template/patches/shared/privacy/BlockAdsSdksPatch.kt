// Universal heuristic "Block ads & ad SDKs" patch.
// NOPs ad load/show calls whose defining class matches a known ad SDK package
// marker (AdMob, Unity Ads, Facebook Audience Network, AppLovin, IronSource
// and similar). Heuristic; obfuscated or reflection-based ad loading is not
// covered. Applies to any app. Default = false.

package app.template.patches.shared.privacy

import app.template.patches.shared.logUniversal

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private val AD_SDK_MARKERS = listOf(
    "com/google/android/gms/ads",
    "com/google/ads",
    "com/unity3d/ads",
    "com/facebook/ads",
    "com/applovin",
    "com/ironsource",
    "com/startapp",
    "com/mintegral",
    "com/mopub",
    "com/chartboost",
    "com/vungle",
    "com/smaato",
    "com/inmobi",
    "com/tapjoy",
)

private val AD_METHODS = setOf(
    "loadAd",
    "loadAds",
    "showAd",
    "showInterstitial",
    "showRewardedAd",
    "showRewardedInterstitial",
    "loadRewardedAd",
    "loadRewardedInterstitialAd",
    "showAppOpenAd",
    "loadAppOpenAd",
    "showBanner",
    "loadBanner",
)

private val INVOKE_OPCODES = setOf(
    Opcode.INVOKE_VIRTUAL,
    Opcode.INVOKE_VIRTUAL_RANGE,
    Opcode.INVOKE_INTERFACE,
    Opcode.INVOKE_INTERFACE_RANGE,
    Opcode.INVOKE_STATIC,
    Opcode.INVOKE_STATIC_RANGE,
)

@Suppress("unused")
val blockAdsSdksPatch = bytecodePatch(
    name = "Block ads & ad SDKs",
    description = """
        Blocks common ad SDKs (AdMob, Unity Ads, Facebook Audience Network,
        AppLovin, IronSource and similar) by removing their ad load/show call
        sites.

        Heuristic: obfuscated or reflection-based ad loading is not covered.
        Applies to any app.
    """.trimIndent(),
    default = false,
) {
    var adsNop = 0

    classDefForEach { classDef ->
        val mutableClass = mutableClassDefBy(classDef)

        mutableClass.methods.forEach methodLoop@ { method ->
            val instructions = method.implementation?.instructions?.toList() ?: return@methodLoop
            val replacements = sortedMapOf<Int, String>()

            instructions.forEachIndexed { index, instruction ->
                if (instruction.opcode !in INVOKE_OPCODES) return@forEachIndexed

                val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    ?: return@forEachIndexed

                val isAdCall = AD_SDK_MARKERS.any { reference.definingClass.contains(it) }

                if (isAdCall && reference.name in AD_METHODS && reference.returnType == "V") {
                    replacements[index] = "nop"
                    adsNop++
                }
            }

            if (replacements.isNotEmpty()) {
                replacements.entries
                    .sortedByDescending { it.key }
                    .forEach { (i, smali) -> method.replaceInstruction(i, smali) }
            }
        }
    }

    logUniversal("Block ads & ad SDKs: $adsNop ad call(s) NOPed.")
}
