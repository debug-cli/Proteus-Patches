package app.template.patches.shared.privacy

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.template.patches.shared.logUniversal
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// Match actual package boundaries, not arbitrary substrings in unrelated app code.
private val AD_SDK_PREFIXES = listOf(
    "Lcom/google/android/gms/ads/",
    "Lcom/google/ads/",
    "Lcom/unity3d/ads/",
    "Lcom/facebook/ads/",
    "Lcom/applovin/",
    "Lcom/ironsource/",
    "Lcom/startapp/",
    "Lcom/mintegral/",
    "Lcom/mbridge/msdk/",
    "Lcom/mopub/",
    "Lcom/chartboost/",
    "Lcom/vungle/",
    "Lcom/smaato/",
    "Lcom/inmobi/",
    "Lcom/tapjoy/",
)

private val AD_METHODS = setOf(
    "load", "show", "loadAd", "loadAds", "showAd",
    "loadInterstitial", "showInterstitial",
    "loadRewardedAd", "showRewardedAd", "loadRewardedVideo", "showRewardedVideo",
    "loadRewardedInterstitialAd", "showRewardedInterstitial",
    "loadAppOpenAd", "showAppOpenAd", "loadBanner", "showBanner",
)

private val INVOKE_OPCODES = setOf(
    Opcode.INVOKE_VIRTUAL,
    Opcode.INVOKE_VIRTUAL_RANGE,
    Opcode.INVOKE_INTERFACE,
    Opcode.INVOKE_INTERFACE_RANGE,
    Opcode.INVOKE_STATIC,
    Opcode.INVOKE_STATIC_RANGE,
)

/** Only void calls can be replaced with a nop without orphaning a move-result. */
internal fun isBlockedAdSdkCall(instruction: Instruction): Boolean {
    if (instruction.opcode !in INVOKE_OPCODES) return false
    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: return false
    return reference.returnType == "V" &&
        reference.name in AD_METHODS &&
        AD_SDK_PREFIXES.any { reference.definingClass.startsWith(it) }
}

internal fun blockAdSdkCalls(method: MutableMethod): Int {
    val instructions = method.implementation?.instructions?.toList() ?: return 0
    val indexes = instructions.indices.filter { isBlockedAdSdkCall(instructions[it]) }
    // Work backwards so index changes cannot affect calls still to be replaced.
    indexes.asReversed().forEach { method.replaceInstruction(it, "nop") }
    return indexes.size
}

@Suppress("unused")
val blockAdsSdksPatch = bytecodePatch(
    name = "Block ads & ad SDKs",
    description = """
        Attempts to remove void ad load/show calls to known SDK packages, such as
        AdMob, Unity Ads and Facebook Audience Network. Obfuscated, reflective,
        native and first-party ads are not covered. Apps that require an ad or its
        callback, including rewarded flows, may stop working. Unverified on device.
    """.trimIndent(),
    default = false,
) {
    execute {
        var adsNop = 0
        classDefForEach { classDef ->
            mutableClassDefBy(classDef).methods.forEach { method ->
                adsNop += blockAdSdkCalls(method)
            }
        }
        logUniversal("Block ads & ad SDKs: $adsNop ad call(s) NOPed.")
    }
}
