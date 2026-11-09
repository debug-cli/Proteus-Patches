package app.template.patches.mereframe

import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.MEREFRAME_COMPATIBILITY
import app.template.patches.shared.logUniversal
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.Reference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private val PRO_RESOLVER_PARAMETERS = listOf("Landroid/content/Context;", "LSv;", "Ljava/util/List;")
private val RELATED_TYPES = setOf("LSv;", "LRu;", "LIl;", "Lns;")
private val LOG_CONTROL_CHARACTERS = Regex("[\\r\\n\\t]")
internal const val MEREFRAME_TRACE_MAX_LINES = 320
private const val MAX_RESOLVER_INSTRUCTIONS = 120
private const val MAX_RESOLVER_HEAD = 80
private const val MAX_CALL_SITES = 6
private const val MAX_RELATED_SITES = 50

private fun MethodReference.isProResolver(): Boolean =
    definingClass == "Lnv;" && name == "e" && returnType == "Ljava/util/Set;" &&
        parameterTypes.map { it.toString() } == PRO_RESOLVER_PARAMETERS

// APK bytecode is untrusted input; do not allow identifiers to create log lines.
private fun safeIdentifier(value: String): String =
    value.replace(LOG_CONTROL_CHARACTERS, "?").take(240)

private fun methodId(method: MethodReference): String = safeIdentifier(
    "${method.definingClass}->${method.name}(${method.parameterTypes.joinToString("")})${method.returnType}",
)

private fun referenceId(reference: Reference?): String = when (reference) {
    is MethodReference -> methodId(reference)
    is FieldReference -> safeIdentifier("${reference.definingClass}->${reference.name}:${reference.type}")
    is TypeReference -> safeIdentifier(reference.type)
    // In particular, never include string literals or call-site payloads.
    else -> "<reference omitted>"
}

private fun referenceOwner(reference: Reference?): String? = when (reference) {
    is MethodReference -> reference.definingClass
    is FieldReference -> reference.definingClass
    is TypeReference -> reference.type
    else -> null
}

private fun instructionText(instruction: Instruction, address: Int): String {
    val registers = when (instruction) {
        is RegisterRangeInstruction -> "v${instruction.startRegister}..+${instruction.registerCount}"
        is FiveRegisterInstruction -> listOf(
            instruction.registerC, instruction.registerD, instruction.registerE,
            instruction.registerF, instruction.registerG,
        ).take(instruction.registerCount).joinToString(",") { "v$it" }
        is ThreeRegisterInstruction -> "v${instruction.registerA},v${instruction.registerB},v${instruction.registerC}"
        is TwoRegisterInstruction -> "v${instruction.registerA},v${instruction.registerB}"
        is OneRegisterInstruction -> "v${instruction.registerA}"
        else -> ""
    }
    val target = (instruction as? OffsetInstruction)?.let { " target=${address + it.codeOffset}" }.orEmpty()
    // Small numeric constants help show boolean control flow; other literals are omitted.
    val literal = (instruction as? NarrowLiteralInstruction)?.narrowLiteral
        ?.takeIf { it in -1..5 }?.let { " value=$it" }.orEmpty()
    val reference = (instruction as? ReferenceInstruction)?.let { " ${referenceId(it.reference)}" }.orEmpty()
    return "@$address ${instruction.opcode.name} $registers$target$literal$reference".trimEnd()
}

private data class ReferenceSite(val method: Method, val instructionIndex: Int, val address: Int, val reference: Reference)

/**
 * Produce a bounded, static-only trace. It contains bytecode identifiers and
 * small integer constants, not string literals, runtime data, photos or account
 * details. A direct-reference search cannot see reflection, native code or
 * prove which path executes when a feature is used.
 */
internal fun collectMereframeProTrace(classes: Iterable<ClassDef>): List<String> {
    val methods = classes.asSequence().flatMap { it.methods.asSequence() }
        .filter { it.implementation != null }
        .sortedBy(::methodId)
        .toList()
    val resolvers = methods.filter { it.isProResolver() }
    val output = mutableListOf(
        "TRACE v1: static DEX identifiers only; strings and most literals omitted; not runtime entitlement evidence",
        "resolver matches=${resolvers.size}",
    )
    if (resolvers.size != 1) {
        output += "END: expected exactly one 0.8.4 resolver; trace cannot identify the gate"
        return output
    }

    val resolver = resolvers.single()
    val body = resolver.implementation!!.instructions.toList()
    val addresses = mutableListOf<Int>()
    var address = 0
    body.forEach { instruction ->
        addresses += address
        address += instruction.codeUnits
    }
    output += "RESOLVER ${methodId(resolver)} instructions=${body.size}"
    val shownIndices = if (body.size <= MAX_RESOLVER_INSTRUCTIONS) {
        body.indices.toList()
    } else {
        // Retain both the early gate and final set/return flow.
        (0 until MAX_RESOLVER_HEAD).toList() +
            (body.size - (MAX_RESOLVER_INSTRUCTIONS - MAX_RESOLVER_HEAD) until body.size).toList()
    }
    var previous = -1
    shownIndices.forEach { index ->
        if (previous >= 0 && index != previous + 1) output += "RESOLVER ... instructions omitted ..."
        output += "RESOLVER ${instructionText(body[index], addresses[index])}"
        previous = index
    }

    val callers = mutableListOf<ReferenceSite>()
    val related = mutableListOf<ReferenceSite>()
    var callerCount = 0
    var relatedCount = 0
    methods.forEach { method ->
        var pc = 0
        method.implementation!!.instructions.forEachIndexed { index, instruction ->
            val ref = (instruction as? ReferenceInstruction)?.reference
            if (ref is MethodReference && ref.isProResolver()) {
                callerCount++
                if (callers.size < MAX_CALL_SITES) callers += ReferenceSite(method, index, pc, ref)
            } else if (ref != null && referenceOwner(ref)?.let { it in RELATED_TYPES } == true) {
                relatedCount++
                if (related.size < MAX_RELATED_SITES) related += ReferenceSite(method, index, pc, ref)
            }
            pc += instruction.codeUnits
        }
    }
    output += "CALLS to resolver: $callerCount (showing ${callers.size}); surrounding instructions are static"
    callers.forEach { site ->
        output += "CALLER ${methodId(site.method)} at=${site.address}"
        val instructions = site.method.implementation!!.instructions.toList()
        val callerAddresses = mutableListOf<Int>()
        var pc = 0
        instructions.forEach { instruction -> callerAddresses += pc; pc += instruction.codeUnits }
        val first = (site.instructionIndex - 4).coerceAtLeast(0)
        val last = (site.instructionIndex + 10).coerceAtMost(instructions.lastIndex)
        for (index in first..last) {
            output += "CALLER ${instructionText(instructions[index], callerAddresses[index])}"
        }
    }
    output += "RELATED references: $relatedCount (showing ${related.size}); leads only, not proven gates"
    related.forEach { site ->
        output += "RELATED ${methodId(site.method)} @${site.address} -> ${referenceId(site.reference)}"
    }
    output += "END: direct references only; reflection, native code and runtime decisions are not traced"
    check(output.size <= MEREFRAME_TRACE_MAX_LINES) { "Mereframe trace exceeded its line limit" }
    return output
}

@Suppress("unused")
val traceProEntitlementPatch = bytecodePatch(
    name = "Mereframe: Trace Pro entitlement (diagnostic)",
    description = "DIAGNOSTIC ONLY: logs a bounded, static trace of the 0.8.4 Pro resolver and direct " +
        "references while patching. Does not unlock Pro, change the app, or log string literals or " +
        "runtime data. Off by default; share only the MEREFRAME_PRO_TRACE lines after reviewing them.",
    default = false,
) {
    compatibleWith(MEREFRAME_COMPATIBILITY)

    execute {
        val classes = mutableListOf<ClassDef>()
        classDefForEach { classes += it }
        collectMereframeProTrace(classes).forEach { logUniversal("MEREFRAME_PRO_TRACE $it") }
    }
}
