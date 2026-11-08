package app.template.patches.protonpass

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val PLAN_CLASS = "Lproton/android/pass/domain/Plan;"
private const val PLAN_LIMIT = "Lproton/android/pass/domain/PlanLimit;"

/**
 * The existing injection reuses parameter registers and assumes this exact
 * constructor layout. Reject changed bytecode rather than corrupt a vault app.
 * This is a structural guard, not proof that any particular APK was tested.
 */
internal fun isKnownPlanConstructor(method: Method): Boolean {
    if (method.definingClass != PLAN_CLASS || method.name != "<init>" || method.returnType != "V") return false
    val implementation = method.implementation ?: return false
    if (implementation.registerCount != 8) return false
    val instructions = implementation.instructions.toList()
    if (instructions.size < 7) return false

    val first = instructions[0]
    val superCall = (first as? ReferenceInstruction)?.reference as? MethodReference ?: return false
    val superRegisters = first as? FiveRegisterInstruction ?: return false
    if (first.opcode != Opcode.INVOKE_DIRECT || superCall.definingClass != "Ljava/lang/Object;" ||
        superCall.name != "<init>" || superCall.returnType != "V" || superCall.parameterTypes.isNotEmpty() ||
        superRegisters.registerCount != 1 || superRegisters.registerC != 0
    ) return false

    val expected = listOf(
        Triple("planType", "Landroidx/credentials/Credential;", Opcode.IPUT_OBJECT),
        Triple("hideUpgrade", "Z", Opcode.IPUT_BOOLEAN),
        Triple("vaultLimit", PLAN_LIMIT, Opcode.IPUT_OBJECT),
        Triple("aliasLimit", PLAN_LIMIT, Opcode.IPUT_OBJECT),
        Triple("totpLimit", PLAN_LIMIT, Opcode.IPUT_OBJECT),
        Triple("updatedAt", "J", Opcode.IPUT_WIDE),
    )
    return expected.withIndex().all { (index, wanted) ->
        val instruction = instructions[index + 1]
        val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
        val registers = instruction as? TwoRegisterInstruction
        instruction.opcode == wanted.third && field != null && field.definingClass == PLAN_CLASS &&
            field.name == wanted.first && field.type == wanted.second && registers != null &&
            registers.registerA == index + 1 && registers.registerB == 0
    }
}
