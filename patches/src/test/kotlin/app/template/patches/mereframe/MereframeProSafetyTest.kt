package app.template.patches.mereframe

import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream

/** Synthetic DEX tests only; no Mereframe APK or device behavior is exercised. */
object MereframeProSafetyTest {
    fun run() {
        branchGuardAndMutation()
        traceIsBoundedAndReadOnly()
    }

    private fun branchGuardAndMutation() {
        val valid = ownershipMethod()
        val instructions = valid.implementation!!.instructions.toList()
        check(findMereframeProBranch(instructions) == 2)
        check(patchMereframeProBranch(valid) == 2)
        check(roundTrip(valid).implementation!!.instructions.map { it.opcode }.toList() == listOf(
            Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT, Opcode.NOP, Opcode.RETURN_VOID,
        ))
        check(runCatching { patchMereframeProBranch(valid) }.isFailure) // no branch to patch twice

        for (invalid in listOf(
            ownershipMethod(owner = "Ljava/util/List;"),
            ownershipMethod(owner = "Lfake/ArrayList;"),
            ownershipMethod(name = "containsAll"),
            ownershipMethod(parameters = listOf("Ljava/lang/String;")),
            ownershipMethod(result = "I"),
            ownershipMethod(resultRegister = 2, branchRegister = 3),
            ownershipMethod(branchOpcode = Opcode.IF_NEZ),
            ownershipMethod(moveOpcode = Opcode.MOVE_RESULT_OBJECT),
        )) {
            val before = invalid.implementation!!.instructions.map { it.opcode }.toList()
            check(runCatching { patchMereframeProBranch(invalid) }.isFailure)
            check(invalid.implementation!!.instructions.map { it.opcode }.toList() == before)
        }
        check(runCatching { findMereframeProBranch(emptyList()) }.isFailure)
        check(runCatching { findMereframeProBranch(instructions.take(1)) }.isFailure)
        check(runCatching { findMereframeProBranch(instructions.take(2)) }.isFailure)
        check(runCatching { findMereframeProBranch(instructions + instructions) }.isFailure)
    }

    private fun ownershipMethod(
        owner: String = "Ljava/util/ArrayList;",
        name: String = "contains",
        parameters: List<String> = listOf("Ljava/lang/Object;"),
        result: String = "Z",
        resultRegister: Int = 2,
        branchRegister: Int = 2,
        branchOpcode: Opcode = Opcode.IF_EQZ,
        moveOpcode: Opcode = Opcode.MOVE_RESULT,
    ): MutableMethod {
        val ref = ImmutableMethodReference(owner, name, parameters, result)
        val body = MutableMethodImplementation(4).apply {
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 0, 1, 0, 0, 0, ref))
            addInstruction(BuilderInstruction11x(moveOpcode, resultRegister))
            addInstruction(BuilderInstruction10x(Opcode.NOP))
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
            replaceInstruction(2, BuilderInstruction21t(branchOpcode, branchRegister, newLabelForIndex(3)))
        }
        return MutableMethod(method("Ltest/Fixture;", "guard", emptyList(), "V", body))
    }

    private fun traceIsBoundedAndReadOnly() {
        val missing = collectMereframeProTrace(emptyList())
        check(missing.any { it.contains("resolver matches=0") })
        check(missing.any { it.contains("expected exactly one") })

        val resolverBody = MutableMethodImplementation(5).apply {
            addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 0,
                ImmutableStringReference("PRIVATE_LITERAL_MUST_NOT_APPEAR")))
            repeat(200) { addInstruction(BuilderInstruction10x(Opcode.NOP)) }
            addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, 1))
        }
        val resolver = method("Lnv;", "e", resolverParameters(), "Ljava/util/Set;", resolverBody)
        val target = ImmutableMethodReference("Lnv;", "e", resolverParameters(), "Ljava/util/Set;")
        val related = ImmutableMethodReference("LSv;", "a", emptyList<String>(), "Ljava/util/List;")
        val callerBody = MutableMethodImplementation(5).apply {
            repeat(10) {
                addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 3, 0, 1, 2, 0, 0, target))
                addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 3))
            }
            repeat(60) {
                addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 0, 0, 0, 0, 0, related))
            }
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        }
        val caller = method("Ltest/Caller;", "check", emptyList(), "V", callerBody)
        val classes = listOf(classDef("Lnv;", resolver), classDef("Ltest/Caller;", caller))
        val before = classes.flatMap { cls -> cls.methods.flatMap { it.implementation!!.instructions.map { i -> i.opcode } } }
        val trace = collectMereframeProTrace(classes)
        val after = classes.flatMap { cls -> cls.methods.flatMap { it.implementation!!.instructions.map { i -> i.opcode } } }
        check(before == after) // diagnostic does not rewrite app code
        check(trace == collectMereframeProTrace(classes.reversed())) // deterministic
        check(trace.size <= MEREFRAME_TRACE_MAX_LINES)
        check(trace.any { it.contains("resolver matches=1") })
        check(trace.any { it.contains("RESOLVER ... instructions omitted") })
        check(trace.any { it.contains("CALLS to resolver: 10 (showing 6)") })
        check(trace.any { it.contains("RELATED references: 60 (showing 50)") })
        check(trace.any { it.contains("Ltest/Caller;->check()V") })
        check(trace.none { it.contains("PRIVATE_LITERAL_MUST_NOT_APPEAR") })
        check(trace.all { '\n' !in it && '\r' !in it })
        check(trace.last().contains("reflection, native code"))

        val duplicate = collectMereframeProTrace(listOf(classes[0], classes[0]))
        check(duplicate.any { it.contains("resolver matches=2") })
        check(duplicate.none { it.startsWith("RESOLVER @") })
    }

    private fun resolverParameters() = listOf("Landroid/content/Context;", "LSv;", "Ljava/util/List;")

    private fun method(
        owner: String, name: String, parameters: List<String>, returnType: String,
        body: MutableMethodImplementation,
    ): Method = ImmutableMethod(
        owner, name, parameters.map { ImmutableMethodParameter(it, null, null) }, returnType,
        AccessFlags.PUBLIC.value or AccessFlags.STATIC.value, null, null, body,
    )

    private fun classDef(type: String, method: Method): ClassDef = ImmutableClassDef(
        type, AccessFlags.PUBLIC.value, "Ljava/lang/Object;", emptyList(), null,
        emptyList(), emptyList(), listOf(method),
    )

    private fun roundTrip(method: Method): Method {
        val store = MemoryDataStore()
        val opcodes = Opcodes.getDefault()
        DexPool.writeTo(store, ImmutableDexFile(opcodes, listOf(classDef(method.definingClass, method))))
        return DexBackedDexFile.fromInputStream(opcodes, BufferedInputStream(ByteArrayInputStream(store.data)))
            .classes.single().methods.single()
    }
}
