package app.template.patches.shared.misc

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.findFreeRegister
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// Universal "Prefer external browser over Custom Tabs" patch: rewrites
// CustomTabsIntent.launchUrl(Context, String|Uri) call sites into
// ACTION_VIEW + startActivity, using a free register for scratch. Fail-safe
// (site skipped if no free register); default = false.

private const val CUSTOM_TABS_INTENT = "Landroidx/browser/customtabs/CustomTabsIntent;"
private const val CONTEXT_CLASS = "Landroid/content/Context;"
private const val INTENT_CLASS = "Landroid/content/Intent;"
private const val URI_CLASS = "Landroid/net/Uri;"
private const val ACTION_VIEW = "android.intent.action.VIEW"

private val INVOKE_OPCODES = setOf(
    Opcode.INVOKE_VIRTUAL,
    Opcode.INVOKE_VIRTUAL_RANGE,
    Opcode.INVOKE_INTERFACE,
    Opcode.INVOKE_INTERFACE_RANGE,
)

@Suppress("unused")
val externalBrowserPatch = bytecodePatch(
    name = "Prefer external browser over Custom Tabs",
    description = """
        Opens links in the default external browser instead of an in-app Custom Tab,
        by rewriting CustomTabsIntent.launchUrl call sites into ACTION_VIEW intents.

        Applies to any app. Some apps rely on the Custom Tab session (shared cookies,
        return results) and may misbehave.
    """.trimIndent(),
    default = false,
) {
    execute {
        var patched = 0

        classDefForEach { classDef ->
            val mutableClass = mutableClassDefBy(classDef)

            mutableClass.methods.forEach methodLoop@ { method ->
                val instructions = method.implementation?.instructions?.toList() ?: return@methodLoop

                instructions.forEachIndexed { index, instruction ->
                    if (instruction.opcode !in INVOKE_OPCODES) return@forEachIndexed
                    if (instruction !is FiveRegisterInstruction) return@forEachIndexed

                    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                        ?: return@forEachIndexed
                    if (reference.definingClass != CUSTOM_TABS_INTENT || reference.name != "launchUrl") {
                        return@forEachIndexed
                    }

                    val thisReg = instruction.registerC
                    val contextReg = instruction.registerD
                    val urlReg = instruction.registerE

                    val urlIsString = reference.parameterTypes == listOf(CONTEXT_CLASS, "Ljava/lang/String;")
                    val urlIsUri = reference.parameterTypes == listOf(CONTEXT_CLASS, URI_CLASS)
                    if (!urlIsString && !urlIsUri) return@forEachIndexed

                    val scratch: Int = try {
                        method.findFreeRegister(index, thisReg, contextReg, urlReg)
                    } catch (_: IllegalArgumentException) {
                        return@forEachIndexed // fail-safe: leave this site stock
                    }

                    val urlParse = if (urlIsString) {
                        """
                            invoke-static {v$urlReg}, $URI_CLASS->parse(Ljava/lang/String;)$URI_CLASS
                            move-result-object v$urlReg
                        """.trimIndent()
                    } else ""

                    method.replaceInstructions(
                        index,
                        """
                            $urlParse
                            const-string v$scratch, "$ACTION_VIEW"
                            new-instance v$thisReg, $INTENT_CLASS
                            invoke-direct {v$thisReg, v$scratch, v$urlReg}, $INTENT_CLASS-><init>(Ljava/lang/String;$URI_CLASS)V
                            invoke-virtual {v$contextReg, v$thisReg}, $CONTEXT_CLASS->startActivity($INTENT_CLASS)V
                        """.trimIndent(),
                    )
                    patched++
                }
            }
        }

        println("External browser: $patched CustomTabsIntent.launchUrl call site(s) rewritten.")
    }
}
