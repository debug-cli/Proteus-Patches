package app.template.patches.mereframe

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.template.patches.shared.Constants.MEREFRAME_COMPATIBILITY
import app.template.patches.shared.logUniversal
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * The existing 0.8.4 patch removes one conditional branch following an
 * ArrayList.contains call in Lnv;->e(Context, Sv, List)Set. The original
 * analysis identified that as an ownership check; it left a separate Ru.a()Z
 * availability check untouched. Issue #5 subsequently reported that a Pro
 * feature still displayed the purchase prompt. The actual remaining gate and
 * whether this branch is responsible have not been established.
 *
 * Match the documented ArrayList call exactly and fail rather than guessing
 * when it is ambiguous, truncated, or the branch checks a different register.
 * These checks prevent some incorrect edits, but do not prove that this is the
 * ownership check, that it governs all consumers, or that Pro works on device.
 */
internal fun findMereframeProBranch(instructions: List<Instruction>): Int {
    val containsSites = instructions.withIndex().filter { (_, instruction) ->
        if (instruction.opcode != Opcode.INVOKE_VIRTUAL) return@filter false
        val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            ?: return@filter false
        ref.definingClass == "Ljava/util/ArrayList;" &&
            ref.name == "contains" &&
            ref.parameterTypes.map { it.toString() } == listOf("Ljava/lang/Object;") &&
            ref.returnType == "Z"
    }
    require(containsSites.size == 1) {
        "Mereframe Pro: expected one ArrayList.contains(Object)Z in nv.e, found ${containsSites.size}"
    }
    val invokeIndex = containsSites.single().index
    val move = instructions.getOrNull(invokeIndex + 1) as? OneRegisterInstruction
    val branch = instructions.getOrNull(invokeIndex + 2) as? OneRegisterInstruction
    require(move != null && branch != null && move.opcode == Opcode.MOVE_RESULT &&
        branch.opcode == Opcode.IF_EQZ && move.registerA == branch.registerA) {
        "Mereframe Pro: contains result is not followed by if-eqz on the same register"
    }
    return invokeIndex + 2
}

internal fun patchMereframeProBranch(method: MutableMethod): Int {
    val instructions = requireNotNull(method.implementation) {
        "Mereframe Pro: nv.e has no implementation"
    }.instructions.toList()
    val index = findMereframeProBranch(instructions)
    method.replaceInstruction(index, "nop")
    return index
}

@Suppress("unused")
val unlockProPatch = bytecodePatch(
    name = "Unlock Pro features",
    description = "EXPERIMENTAL: issue #5 reports that Pro features still show the purchase prompt. " +
        "Attempts to remove one contains-branch in nv.e; successful patching does not prove Pro access. " +
        "Off by default: capture also reportedly crashed on one device with this patch enabled.",
    default = false,
) {
    compatibleWith(MEREFRAME_COMPATIBILITY)

    execute {
        val method = Fingerprint(
            definingClass = "Lnv;",
            name = "e",
            returnType = "Ljava/util/Set;",
            parameters = listOf("Landroid/content/Context;", "LSv;", "Ljava/util/List;"),
        ).method
        val branchIndex = patchMereframeProBranch(method)
        logUniversal("Mereframe Pro: removed structurally matched branch at instruction $branchIndex; " +
            "Pro access is not verified.")
    }
}
