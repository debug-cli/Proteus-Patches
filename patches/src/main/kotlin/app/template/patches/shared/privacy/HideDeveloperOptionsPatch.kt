package app.template.patches.shared.privacy

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

// Universal "Hide developer options & USB debugging" patch.
// Forces Settings getInt/getLong reads of adb_enabled /
// development_settings_enabled / adb_wifi_enabled / wireless_debugging_enabled
// to 0, and SystemProperties ro.debuggable / service.adb.root to "0", at
// literal-key call sites only. Fail-safe; default = false.

private val SETTINGS_CLASSES = setOf(
    "Landroid/provider/Settings\$Global;",
    "Landroid/provider/Settings\$Secure;",
    "Landroid/provider/Settings\$System;",
)

private val ADB_SETTINGS_KEYS = setOf(
    "adb_enabled",
    "development_settings_enabled",
    "adb_wifi_enabled",
    "wireless_debugging_enabled",
)

private val ADB_PROPERTY_KEYS = setOf(
    "ro.debuggable",
    "service.adb.root",
)

private const val SYSTEM_PROPERTIES_CLASS = "Landroid/os/SystemProperties;"
private const val LOOKAHEAD = 6

@Suppress("unused")
val hideDeveloperOptionsPatch = bytecodePatch(
    name = "Hide developer options & USB debugging",
    description = """
        Makes the app think Developer Options, USB (ADB) debugging and wireless
        debugging are disabled, by forcing the settings reads apps use for these
        checks to return "off" / "0".

        Applies to any app. Only affects what the app itself sees.
    """.trimIndent(),
    default = false,
) {
    execute {
        var settingsSitesPatched = 0
        var propertySitesPatched = 0

        classDefForEach { classDef ->
            val mutableClass = mutableClassDefBy(classDef)

            mutableClass.methods.forEach methodLoop@ { method ->
                val instructions = method.implementation?.instructions?.toList() ?: return@methodLoop
                val replacements = sortedMapOf<Int, String>()

                instructions.forEachIndexed { index, instruction ->
                    val key = ((instruction as? ReferenceInstruction)?.reference as? StringReference)
                        ?.string ?: return@forEachIndexed

                    when {
                        key in ADB_SETTINGS_KEYS -> {
                            val end = minOf(index + LOOKAHEAD, instructions.size)
                            for (j in index + 1 until end) {
                                val reference = (instructions[j] as? ReferenceInstruction)?.reference
                                    as? MethodReference ?: continue
                                if (reference.definingClass !in SETTINGS_CLASSES) continue
                                if (reference.name != "getInt" && reference.name != "getLong") continue

                                val moveResult = instructions.getOrNull(j + 1) as? OneRegisterInstruction
                                    ?: break
                                val register = moveResult.registerA
                                when (moveResult.opcode) {
                                    Opcode.MOVE_RESULT ->
                                        replacements[j + 1] = "const/4 v$register, 0x0"
                                    Opcode.MOVE_RESULT_WIDE ->
                                        replacements[j + 1] =
                                            "const/4 v$register, 0x0\nconst/4 v${register + 1}, 0x0"
                                    else -> {}
                                }
                                settingsSitesPatched++
                                break
                            }
                        }

                        key in ADB_PROPERTY_KEYS -> {
                            val end = minOf(index + LOOKAHEAD, instructions.size)
                            for (j in index + 1 until end) {
                                val reference = (instructions[j] as? ReferenceInstruction)?.reference
                                    as? MethodReference ?: continue
                                if (reference.definingClass != SYSTEM_PROPERTIES_CLASS ||
                                    reference.name != "get" ||
                                    reference.parameterTypes != listOf("Ljava/lang/String;")
                                ) continue

                                val moveResult = instructions.getOrNull(j + 1) as? OneRegisterInstruction
                                    ?: break
                                if (moveResult.opcode == Opcode.MOVE_RESULT_OBJECT) {
                                    replacements[j + 1] = "const-string v${moveResult.registerA}, \"0\""
                                    propertySitesPatched++
                                }
                                break
                            }
                        }
                    }
                }

                if (replacements.isNotEmpty()) {
                    replacements.entries
                        .sortedByDescending { it.key }
                        .forEach { (index, smali) ->
                            if (smali.contains('\n')) method.replaceInstructions(index, smali)
                            else method.replaceInstruction(index, smali)
                        }
                }
            }
        }

        println(
            "Hide developer options: $settingsSitesPatched settings read(s) forced to 0, " +
                "$propertySitesPatched system property read(s) forced to \"0\".",
        )
    }
}
