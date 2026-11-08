package app.template.patches.protonpass

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val PLAN_CLASS = "Lproton/android/pass/domain/Plan;"
private const val PLAN_LIMIT = "Lproton/android/pass/domain/PlanLimit;"
private const val PLAN_TYPE = "Lproton/android/pass/domain/PlanType;"
private const val LEGACY_PLAN_TYPE = "Landroidx/credentials/Credential;"
private const val PAID_CLASS = "Lproton/android/pass/domain/PlanType\$Paid;"
private const val PLUS_CLASS = "Lproton/android/pass/domain/PlanType\$Paid\$Plus;"
private const val UNLIMITED_CLASS = "Lproton/android/pass/domain/PlanLimit\$Unlimited;"

/**
 * The injection reuses parameter registers and assumes this exact layout.
 * The release 1.40.3 layout used Credential after R8 optimization; upstream
 * 1.41.5 source declares PlanType. Neither establishes the layout of a 1.41.5
 * APK. Reject any other bytecode rather than corrupt a password-vault app.
 */
internal fun isKnownPlanConstructor(method: Method): Boolean {
    if (method.definingClass != PLAN_CLASS || method.name != "<init>" || method.returnType != "V") return false
    if (!AccessFlags.PUBLIC.isSet(method.accessFlags) || !AccessFlags.CONSTRUCTOR.isSet(method.accessFlags) ||
        AccessFlags.STATIC.isSet(method.accessFlags)
    ) return false
    val parameters = method.parameterTypes.map { it.toString() }
    val planType = parameters.firstOrNull() ?: return false
    if (planType !in setOf(PLAN_TYPE, LEGACY_PLAN_TYPE) ||
        parameters != listOf(planType, "Z", PLAN_LIMIT, PLAN_LIMIT, PLAN_LIMIT, "J")
    ) return false

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
        Triple("planType", planType, Opcode.IPUT_OBJECT),
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

/**
 * Verify every class/member introduced by the injection, including assignment
 * compatibility with the constructor's planType parameter. Missing classes,
 * methods, fields, inaccessible members and superclass cycles fail closed.
 */
internal fun hasSafePlanInjectionReferences(method: Method, classFor: (String) -> ClassDef?): Boolean {
    if (!isKnownPlanConstructor(method)) return false
    val plus = classFor(PLUS_CLASS) ?: return false
    if (!AccessFlags.PUBLIC.isSet(plus.accessFlags) || AccessFlags.ABSTRACT.isSet(plus.accessFlags) ||
        AccessFlags.INTERFACE.isSet(plus.accessFlags)
    ) return false
    val plusConstructor = plus.methods.singleOrNull {
        it.name == "<init>" && it.returnType == "V" &&
            it.parameterTypes.map { type -> type.toString() } == listOf("Ljava/lang/String;", "Ljava/lang/String;") &&
            AccessFlags.PUBLIC.isSet(it.accessFlags) && AccessFlags.CONSTRUCTOR.isSet(it.accessFlags) &&
            !AccessFlags.STATIC.isSet(it.accessFlags) && it.implementation != null
    } ?: return false
    if (plusConstructor.definingClass != PLUS_CLASS) return false

    val targetType = method.parameterTypes.first().toString()
    if (!hasAncestor(PLUS_CLASS, PAID_CLASS, classFor) ||
        !hasAncestor(PLUS_CLASS, targetType, classFor)
    ) return false

    val unlimited = classFor(UNLIMITED_CLASS) ?: return false
    if (!AccessFlags.PUBLIC.isSet(unlimited.accessFlags) ||
        !hasAncestor(UNLIMITED_CLASS, PLAN_LIMIT, classFor)
    ) return false
    return unlimited.fields.any {
        it.definingClass == UNLIMITED_CLASS && it.name == "INSTANCE" && it.type == UNLIMITED_CLASS &&
            AccessFlags.PUBLIC.isSet(it.accessFlags) && AccessFlags.STATIC.isSet(it.accessFlags)
    }
}

private fun hasAncestor(start: String, target: String, classFor: (String) -> ClassDef?): Boolean {
    val pending = ArrayDeque<String>().apply { add(start) }
    val seen = mutableSetOf<String>()
    while (pending.isNotEmpty()) {
        val type = pending.removeFirst()
        if (type == target) return true
        if (!seen.add(type)) continue
        val definition = classFor(type) ?: continue
        definition.superclass?.let { pending.add(it) }
        definition.interfaces.forEach { pending.add(it) }
    }
    return false
}
