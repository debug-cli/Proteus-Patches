package app.template.patches.projectivy

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

// In 4.73 this method updates the application's restricted-mode StateFlow.
// Passing false bypasses its timestamp branch and publishes false (unrestricted).
private object RestrictedModeSetter : Fingerprint(
    definingClass = "Lcom/spocky/projengmenu/PTApplication;",
    name = "d",
    returnType = "V",
    parameters = listOf("Z"),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    filters = listOf(
        // Fingerprint filters are ordered: the clock read precedes the StateFlow update.
        methodCall(definingClass = "Ljava/lang/System;", name = "currentTimeMillis"),
        methodCall(definingClass = "Llf4;", name = "h"),
    ),
)

private object ApplicationConstructor : Fingerprint(
    definingClass = "Lcom/spocky/projengmenu/PTApplication;",
    name = "<init>",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(definingClass = "Lts1;", name = "a"),
        methodCall(definingClass = "Lzv4;", name = "c"),
    ),
)

@Suppress("unused")
val projectivyUnlockPremiumPatch = bytecodePatch(
    name = "Unlock local premium features",
    description = "Disables the local restricted mode used by premium features in Projectivy Launcher 4.73.",
    default = false,
) {
    compatibleWith(
        Compatibility(
            name = "Projectivy Launcher",
            packageName = "com.spocky.projengmenu",
            apkFileType = ApkFileType.APK,
            targets = listOf(AppTarget(version = "4.73", versionCode = 98)),
        ),
    )

    execute {
        // The application initializes the same StateFlow to TRUE before the first
        // entitlement update. Start unrestricted as well as keeping updates unrestricted.
        val constructor = ApplicationConstructor.method
        val instructions = constructor.implementation!!.instructions
        val matches = instructions.withIndex().filter { (_, instruction) ->
            val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
            field?.definingClass == "Ljava/lang/Boolean;" && field.name == "TRUE" &&
                field.type == "Ljava/lang/Boolean;"
        }
        check(matches.size == 1) { "Expected exactly one initial restricted-mode value" }
        val match = matches.single()
        val register = (match.value as OneRegisterInstruction).registerA
        constructor.replaceInstruction(match.index, "sget-object v$register, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;")
        RestrictedModeSetter.method.addInstructions(0, "const/4 p1, 0x0")
    }
}
