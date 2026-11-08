package app.template.patches.shared.privacy
import app.template.patches.shared.logUniversal

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.template.patches.shared.constValue
import app.template.patches.shared.getFreeRegisterProvider
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// Universal "Block screenshots & screen recording" patch.
// Attempts FLAG_SECURE (0x2000) on recognizable Activity windows using existing
// free registers and flips literal SurfaceView.setSecure(false) to true. A
// missed site is left unchanged; default = false.

private const val ACTIVITY_CLASS = "Landroid/app/Activity;"
private const val WINDOW_CLASS = "Landroid/view/Window;"
private const val SURFACE_VIEW_CLASS = "Landroid/view/SurfaceView;"
private const val BUNDLE_CLASS = "Landroid/os/Bundle;"

private const val FLAG_SECURE = 0x2000

private val INVOKE_OPCODES = setOf(
    Opcode.INVOKE_VIRTUAL,
    Opcode.INVOKE_VIRTUAL_RANGE,
    Opcode.INVOKE_INTERFACE,
    Opcode.INVOKE_INTERFACE_RANGE,
)

// The platform Activity class is normally absent from the app's DEX; compare
// the superclass descriptor before attempting to resolve it in the app.
internal fun isActivitySubclass(classDef: ClassDef, classFor: (String) -> ClassDef?): Boolean {
    val seen = mutableSetOf<String>()
    var current: ClassDef? = classDef
    while (current != null && seen.add(current.type)) {
        if (current.type == ACTIVITY_CLASS) return true
        val superType = current.superclass ?: return false
        if (superType == ACTIVITY_CLASS) return true
        current = classFor(superType)
    }
    return false
}

/** Return false when a normal invoke cannot encode the selected registers. */
internal fun injectSecureWindowFlag(method: MutableMethod, windowReg: Int, flagsReg: Int): Boolean {
    if (windowReg !in 0..15 || flagsReg !in 0..15 || windowReg == flagsReg) return false
    method.addInstructions(
        0,
        """
            invoke-virtual/range {p0 .. p0}, $ACTIVITY_CLASS->getWindow()$WINDOW_CLASS
            move-result-object v$windowReg
            ${constValue(flagsReg, FLAG_SECURE)}
            invoke-virtual {v$windowReg, v$flagsReg}, $WINDOW_CLASS->addFlags(I)V
        """.trimIndent(),
    )
    return true
}

@Suppress("unused")
val blockScreenCapturePatch = bytecodePatch(
    name = "Block screenshots & screen recording",
    description = """
        Attempts to add FLAG_SECURE to activity windows and secure SurfaceViews.
        This can make screenshots and screen recordings show a black surface.
        Only recognizable code with available registers can be changed, so this
        is not a guarantee that all screens or capture methods are protected.

        Applies to any app. Do not combine with "Allow screenshots & screen recording".
    """.trimIndent(),
    default = false,
) {
    execute {
        var activitiesPatched = 0
        var surfaceViewsPatched = 0

        classDefForEach { classDef ->
            val mutableClass = mutableClassDefBy(classDef)

            if (isActivitySubclass(classDef) { classDefByOrNull(it) }) {
                val onCreate = mutableClass.methods.firstOrNull { method ->
                    method.name == "onCreate" &&
                        method.returnType == "V" &&
                        method.parameters.size == 1 &&
                        method.parameters[0].type == BUNDLE_CLASS
                }
                if (onCreate != null && onCreate.implementation != null) {
                    // 2 scratch registers at index 0 without touching p0/p1 and
                    // WITHOUT growing registerCount (would shift params while
                    // existing instructions reference old absolute registers).
                    val registerCount = onCreate.implementation!!.registerCount
                    val parameterRegisters = listOf(registerCount - 2, registerCount - 1)
                    val registerProvider = try {
                        onCreate.getFreeRegisterProvider(0, 2, parameterRegisters)
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                    if (registerProvider != null) {
                        val windowReg = registerProvider.getFreeRegister()
                        val flagsReg = registerProvider.getFreeRegister()
                        if (injectSecureWindowFlag(onCreate, windowReg, flagsReg)) {
                            activitiesPatched++
                        }
                    }
                }
            }

            mutableClass.methods.forEach methodLoop@ { method ->
                val instructions = method.implementation?.instructions?.toList() ?: return@methodLoop
                val replacements = sortedMapOf<Int, String>()

                instructions.forEachIndexed { index, instruction ->
                    if (instruction.opcode !in INVOKE_OPCODES) return@forEachIndexed
                    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                        ?: return@forEachIndexed
                    if (reference.definingClass != SURFACE_VIEW_CLASS || reference.name != "setSecure") {
                        return@forEachIndexed
                    }
                    if (instruction !is FiveRegisterInstruction) return@forEachIndexed
                    val valueRegister = instruction.registerD
                    var literalIndex = -1
                    var literalValue = 0
                    for (lookBack in index - 1 downTo 0) {
                        val candidate = instructions[lookBack]
                        if (candidate is NarrowLiteralInstruction &&
                            candidate is OneRegisterInstruction &&
                            candidate.registerA == valueRegister
                        ) {
                            literalIndex = lookBack
                            literalValue = candidate.narrowLiteral
                            break
                        }
                    }
                    if (literalIndex >= 0 && literalValue == 0) {
                        replacements[literalIndex] = constValue(valueRegister, 1)
                    }
                }

                if (replacements.isNotEmpty()) {
                    replacements.entries
                        .sortedByDescending { it.key }
                        .forEach { (index, smali) -> method.replaceInstruction(index, smali) }
                    surfaceViewsPatched += replacements.size
                }
            }
        }

        logUniversal(
            "Block screen capture: $activitiesPatched activity onCreate(s) forced FLAG_SECURE, " +
                "$surfaceViewsPatched SurfaceView.setSecure(true) site(s).",
        )
    }
}
