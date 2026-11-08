package app.template.patches.release

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.template.patches.protonpass.hasSafePlanInjectionReferences
import app.template.patches.protonpass.injectPlanOverrides
import app.template.patches.protonpass.isKnownPlanConstructor
import app.template.patches.shared.privacy.blockAdSdkCalls
import app.template.patches.shared.privacy.injectSecureWindowFlag
import app.template.patches.shared.privacy.isActivitySubclass
import app.template.patches.shared.unimplementedAppPatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream

/** Synthetic DEX regression tests; these do not substitute for device testing. */
object ReleaseSafetyTest {
    @JvmStatic
    fun main(args: Array<String>) {
        testAdSdkCalls()
        testScreenCapture()
        testProtonPlanGuard()
        val failure = runCatching { unimplementedAppPatch("test feature") }.exceptionOrNull()
        check(failure is PatchException && failure.message.orEmpty().contains("unimplemented placeholder"))
        println("Release safety DEX and placeholder tests passed")
    }

    private fun ref(owner: String, name: String, result: String = "V") =
        ImmutableMethodReference(owner, name, emptyList<String>(), result)

    private fun testAdSdkCalls() {
        val body = MutableMethodImplementation(3).apply {
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 0, 0, 0, 0, 0,
                ref("Lcom/google/android/gms/ads/AdView;", "loadAd")))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0,
                ref("Lcom/unity3d/ads/UnityAds;", "show")))
            addInstruction(BuilderInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE, 0, 1,
                ref("Lcom/facebook/ads/InterstitialAd;", "loadAd")))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0,
                ref("Lcom/applovin/Ad;", "loadAd", "I")))
            addInstruction(BuilderInstruction11x(Opcode.MOVE_RESULT, 1))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 0, 0, 0, 0, 0,
                ref("Lcom/google/android/gms/adsnot/Other;", "loadAd")))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 0, 0, 0, 0, 0,
                ref("Lapp/com/google/android/gms/ads/Other;", "loadAd")))
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_DIRECT, 1, 0, 0, 0, 0, 0,
                ref("Lcom/applovin/Ad;", "loadAd")))
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        }
        val method = mutableMethod("Ltest/Ads;", "run", emptyList(), true, body)
        check(blockAdSdkCalls(method) == 3)
        val opcodes = roundTrip(method).implementation!!.instructions.map { it.opcode }
        check(opcodes == listOf(Opcode.NOP, Opcode.NOP, Opcode.NOP, Opcode.INVOKE_STATIC,
            Opcode.MOVE_RESULT, Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL,
            Opcode.INVOKE_DIRECT, Opcode.RETURN_VOID))
        check(blockAdSdkCalls(method) == 0) // idempotent
    }

    private fun testScreenCapture() {
        val direct = classDef("Ltest/Direct;", "Landroid/app/Activity;")
        val middle = classDef("Ltest/Middle;", "Landroid/app/Activity;")
        val indirect = classDef("Ltest/Indirect;", "Ltest/Middle;")
        val cycleA = classDef("Ltest/CycleA;", "Ltest/CycleB;")
        val cycleB = classDef("Ltest/CycleB;", "Ltest/CycleA;")
        val classes = listOf(direct, middle, indirect, cycleA, cycleB).associateBy { it.type }
        val lookup: (String) -> ClassDef? = { classes[it] }
        check(isActivitySubclass(direct, lookup)) // framework Activity is absent from the map
        check(isActivitySubclass(indirect, lookup))
        check(!isActivitySubclass(cycleA, lookup))
        check(!isActivitySubclass(classDef("Ltest/Other;", "Lmissing/Parent;"), lookup))

        val body = MutableMethodImplementation(20).apply {
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        }
        val method = mutableMethod("Ltest/Direct;", "onCreate", listOf("Landroid/os/Bundle;"), false, body)
        check(!injectSecureWindowFlag(method, 16, 1))
        check(!injectSecureWindowFlag(method, 1, 1))
        check(method.implementation!!.instructions.count() == 1)
        check(injectSecureWindowFlag(method, 0, 1))
        val instructions = roundTrip(method).implementation!!.instructions.toList()
        check(instructions[0].opcode == Opcode.INVOKE_VIRTUAL_RANGE)
        check((instructions[0] as RegisterRangeInstruction).startRegister == 18) // p0, above v15
        check((instructions[2] as NarrowLiteralInstruction).narrowLiteral == 0x2000)
        val call = instructions[3]
        val target = (call as ReferenceInstruction).reference as MethodReference
        val registers = call as FiveRegisterInstruction
        check(target.definingClass == "Landroid/view/Window;" && target.name == "addFlags")
        check(target.parameterTypes.map { it.toString() } == listOf("I") && target.returnType == "V")
        check(registers.registerCount == 2 && registers.registerC == 0 && registers.registerD == 1)
    }

    private fun testProtonPlanGuard() {
        val legacyType = "Landroidx/credentials/Credential;"
        val sourceType = "Lproton/android/pass/domain/PlanType;"
        for (type in listOf(legacyType, sourceType)) {
            val constructor = planConstructor(planType = type)
            check(isKnownPlanConstructor(constructor))
            check(isKnownPlanConstructor(roundTrip(constructor)))
            val refs = planReferences(type)
            check(hasSafePlanInjectionReferences(constructor) { refs[it] })
            check(!hasSafePlanInjectionReferences(constructor) { null })

            val plus = "Lproton/android/pass/domain/PlanType\$Paid\$Plus;"
            val paid = "Lproton/android/pass/domain/PlanType\$Paid;"
            val unlimited = "Lproton/android/pass/domain/PlanLimit\$Unlimited;"
            check(!hasSafePlanInjectionReferences(constructor) { if (it == plus) null else refs[it] })
            check(!hasSafePlanInjectionReferences(constructor) { if (it == paid) null else refs[it] })
            check(!hasSafePlanInjectionReferences(constructor) { if (it == unlimited) null else refs[it] })
            val wrongPaid = refs + (paid to classDef(paid, "Ljava/lang/Object;"))
            check(!hasSafePlanInjectionReferences(constructor) { wrongPaid[it] })
            val cycle = refs + (paid to classDef(paid, plus))
            check(!hasSafePlanInjectionReferences(constructor) { cycle[it] })
            val wrongLimit = refs + (unlimited to classDef(unlimited, "Ljava/lang/Object;"))
            check(!hasSafePlanInjectionReferences(constructor) { wrongLimit[it] })
            val noConstructor = refs + (plus to classDef(plus, paid))
            check(!hasSafePlanInjectionReferences(constructor) { noConstructor[it] })
            val noInstance = refs + (unlimited to ImmutableClassDef(
                unlimited, AccessFlags.PUBLIC.value, "Ljava/lang/Object;",
                listOf("Lproton/android/pass/domain/PlanLimit;"), null,
                emptyList(), emptyList(), emptyList(),
            ))
            check(!hasSafePlanInjectionReferences(constructor) { noInstance[it] })

            // Assemble and serialize the real injection for both supported types.
            injectPlanOverrides(constructor)
            val opcodes = roundTrip(constructor).implementation!!.instructions.map { it.opcode }.toList()
            check(opcodes.take(10) == listOf(
                Opcode.INVOKE_DIRECT, Opcode.NEW_INSTANCE, Opcode.CONST_STRING, Opcode.CONST_STRING,
                Opcode.INVOKE_DIRECT, Opcode.MOVE_OBJECT, Opcode.SGET_OBJECT, Opcode.SGET_OBJECT,
                Opcode.SGET_OBJECT, Opcode.CONST_4,
            ))
            check(opcodes[10] == Opcode.IPUT_OBJECT)
        }
        check(!isKnownPlanConstructor(planConstructor(registers = 9)))
        check(!isKnownPlanConstructor(planConstructor(alteredField = true)))
        check(!isKnownPlanConstructor(planConstructor(planType = "Ltest/Unexpected;")))
    }

    private fun planConstructor(
        registers: Int = 8,
        alteredField: Boolean = false,
        planType: String = "Landroidx/credentials/Credential;",
    ): MutableMethod {
        val plan = "Lproton/android/pass/domain/Plan;"
        val limit = "Lproton/android/pass/domain/PlanLimit;"
        val fields = listOf(
            Triple("planType", planType, Opcode.IPUT_OBJECT),
            Triple("hideUpgrade", "Z", Opcode.IPUT_BOOLEAN),
            Triple(if (alteredField) "otherLimit" else "vaultLimit", limit, Opcode.IPUT_OBJECT),
            Triple("aliasLimit", limit, Opcode.IPUT_OBJECT),
            Triple("totpLimit", limit, Opcode.IPUT_OBJECT),
            Triple("updatedAt", "J", Opcode.IPUT_WIDE),
        )
        val body = MutableMethodImplementation(registers).apply {
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_DIRECT, 1, 0, 0, 0, 0, 0,
                ref("Ljava/lang/Object;", "<init>")))
            fields.forEachIndexed { index, (name, type, opcode) ->
                addInstruction(BuilderInstruction22c(opcode, index + 1, 0,
                    ImmutableFieldReference(plan, name, type)))
            }
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        }
        return mutableMethod(plan, "<init>", listOf(planType, "Z", limit, limit, limit, "J"),
            false, body, constructor = true)
    }

    private fun planReferences(planType: String): Map<String, ClassDef> {
        val plus = "Lproton/android/pass/domain/PlanType\$Paid\$Plus;"
        val paid = "Lproton/android/pass/domain/PlanType\$Paid;"
        val limit = "Lproton/android/pass/domain/PlanLimit;"
        val unlimited = "Lproton/android/pass/domain/PlanLimit\$Unlimited;"
        val plusBody = MutableMethodImplementation(3).apply {
            addInstruction(BuilderInstruction35c(Opcode.INVOKE_DIRECT, 1, 0, 0, 0, 0, 0,
                ref(paid, "<init>")))
            addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
        }
        val plusConstructor = mutableMethod(plus, "<init>",
            listOf("Ljava/lang/String;", "Ljava/lang/String;"), false, plusBody, constructor = true)
        val plusClass = ImmutableClassDef(plus, AccessFlags.PUBLIC.value, paid,
            emptyList(), null, emptyList(), emptyList(), listOf(plusConstructor))
        val instance = ImmutableField(unlimited, "INSTANCE", unlimited,
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.FINAL.value,
            null, null, null)
        val unlimitedClass = ImmutableClassDef(unlimited, AccessFlags.PUBLIC.value, "Ljava/lang/Object;",
            listOf(limit), null, emptyList(), listOf(instance), emptyList())
        return listOf(plusClass, classDef(paid, planType), unlimitedClass).associateBy { it.type }
    }

    private fun mutableMethod(
        owner: String, name: String, params: List<String>, static: Boolean,
        body: MutableMethodImplementation, constructor: Boolean = false,
    ): MutableMethod {
        var flags = AccessFlags.PUBLIC.value
        if (static) flags = flags or AccessFlags.STATIC.value
        if (constructor) flags = flags or AccessFlags.CONSTRUCTOR.value
        return MutableMethod(ImmutableMethod(owner, name,
            params.map { ImmutableMethodParameter(it, null, null) }, "V", flags, null, null, body))
    }

    private fun classDef(type: String, parent: String) = ImmutableClassDef(
        type, AccessFlags.PUBLIC.value, parent, emptyList(), null, emptyList(), emptyList(), emptyList(),
    )

    private fun roundTrip(method: Method): Method {
        val cls = ImmutableClassDef(method.definingClass, AccessFlags.PUBLIC.value, "Ljava/lang/Object;",
            emptyList(), null, emptyList(), emptyList(), listOf(method))
        val store = MemoryDataStore()
        val opcodes = Opcodes.getDefault()
        DexPool.writeTo(store, ImmutableDexFile(opcodes, listOf(cls)))
        return DexBackedDexFile.fromInputStream(opcodes, BufferedInputStream(ByteArrayInputStream(store.data)))
            .classes.single().methods.single()
    }
}
