package app.template.patches.mereframe

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.template.patches.shared.Constants.MEREFRAME_COMPATIBILITY
import app.template.patches.shared.clearBody
import app.template.patches.shared.logUniversal
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * The app's native pipeline (libunnamed_raw.so) self-verifies the APK and
 * JNI-throws java.lang.SecurityException("app integrity check failed") on
 * re-signed builds. Field-captured stack (diagnostic build):
 *
 *   B1e6ad8077f6880e309d8da4b.nd2cccb916f63efbcf8b5b19b (Native)
 *     <- B1e6...d(ZZZZFZ)[J <- Jm.m <- Jm.l <- Ul.G <- em.j <- v5.A/z
 *
 * HISTORY (issue #5)
 *
 * 1. The first fix put a try/catch in inline smali. Morphe's inline-smali
 *    compiler compiles snippets into a dummy method and splices back only the
 *    instruction list — the `.catch` lives in the code item's exception table,
 *    so it was silently dropped and the SecurityException escaped.
 * 2. The second fix moved the call inside a REAL JVM try/catch in the
 *    extension (MereframeIntegrityHook.openSessionWithFallback). That works —
 *    but only for `d()`, the session-OPENING wrapper. The native check is not
 *    limited to d(): the bridge class exposes 49 public wrappers and roughly
 *    20 of them validate native output and can throw the same way per call.
 *    As soon as real frames flow, the next wrapper throws and kills the app.
 *
 * WHAT THIS PATCH DOES NOW — two stages
 *
 * Stage 1 (pinned): `d(ZZZZFZ)[J` keeps its shape-aware path. The body is
 * replaced with a single invoke-static into
 * MereframeIntegrityHook.openSessionWithFallback, which calls the native
 * method reflectively, validates the verdict's domain (length 2, element 1 in
 * (0,4)) and degrades to {0L, 1L} on ANY throw or mismatch.
 *
 * Stage 2 (sweep): every OTHER public wrapper on the SAME bridge class — the
 * exact class Stage 1's fingerprint resolves — whose body invokes one of that
 * class's native methods gets a REAL try/catch installed in its own code item:
 *
 *     try { <the original body, untouched> } catch (Throwable t) {
 *         return MereframeIntegrityHook.failOpen<Kind>(t);
 *     }
 *
 * The handler is appended as new instructions and registered with
 * MutableMethodImplementation.addCatch(), i.e. it lands in the code item's
 * exception table (unlike an inline `.catch`, which is dropped). The hook then
 * decides: an integrity failure (SecurityException / "integrity" message) is
 * logged once and answered with a well-formed fail-open value of the wrapper's
 * own return type; ANY other Throwable is re-thrown verbatim, so unrelated
 * bugs keep behaving exactly as before.
 *
 * WHY THE WRAPPER LIST IS RESOLVED AT PATCH TIME
 *
 * The wrappers are R8 output: their names are build-shape specific and their
 * parameter descriptors differ per method, so a Java helper cannot accept them
 * positionally without one overload per signature. The sweep therefore
 * discovers them from the APK being patched (public, non-native, no existing
 * try blocks, body invokes a native method declared by the bridge class) and
 * emits the resolved inventory — method name, parameter descriptor and return
 * enumeration for the build being patched.
 *
 * STAGE 2 USES THE CLASS STAGE 1 ALREADY FOUND
 *
 * An earlier revision re-searched the whole app package for "bridge classes"
 * and joined a wrapper's native call against a set of descriptor strings. Both
 * sides of that join called `parameterTypes.joinToString("")`, but a DEX-backed
 * Method and an invoke-side MethodReference do not stringify parameterTypes the
 * same way, so the keys never matched: the sweep reported
 * "0 wrapper(s) guarded across 0 bridge class(es)" while the on-device crash
 * kept naming unguarded wrappers (q(IJID)D). Stage 2 therefore:
 *
 * - takes the class directly with mutableClassDefBy(BRIDGE_CLASS) — the same
 * type Stage 1's fingerprint matched, so it cannot "match nothing" while
 * Stage 1 succeeds, and it marks the class mutable so the handlers are
 * written out;
 * - identifies the class's native methods by name + parameter count + return
 * type, never by a descriptor string;
 * - logs the class it operated on, the native count, the guarded count and every
 * skipped wrapper with its reason, so a zero count is always explainable.
 *
 * FINGERPRINT DRIFT
 *
 * Stage 1 is pinned to the 0.8.4 (vc 118) shape; on drift it aborts that one
 * injection, logs PINNED SESSION WRAPPER NOT FOUND and lets the sweep cover the
 * session wrapper generically, so the build degrades to the native verdict
 * instead of failing hard. Stage 2 is drift-tolerant per method: each wrapper is
 * a separate best-effort injection wrapped in runCatching — a wrapper that
 * cannot be guarded (unsupported return type, too few registers, pre-existing
 * try/catch) is logged and left running the native verdict, never half-patched
 * and never a hard failure.
 *
 * REGISTER-FORM REQUIREMENT: the ZZZZFZ call has six parameters. `invoke-static`
 * without `/range` is limited to five registers (instruct35c) — the regular
 * form makes InlineSmaliCompiler's dummy method unbuildable, surfacing on-device
 * as PatchException "Collection is empty" at addInstructions (observed
 * 2026-11-07). Stage 1 therefore uses `invoke-static/range {p0 .. p5}`. Every
 * injection made by stage 2 passes exactly ONE source register (the caught
 * Throwable), so the rule is trivially satisfied there.
 */
@Suppress("unused")
val bypassNativeIntegrityPatch = bytecodePatch(
    name = "Bypass native integrity checks",
    description = "libunnamed_raw.so JNI-throws 'app integrity check failed' on re-signed builds, " +
        "killing camera session start (preview) - observed via diagnostic crash log. This patch " +
        "calls the session-opening wrapper inside a real JVM try/catch (extension), and plants the " +
        "same real try/catch in every other public wrapper of that bridge class that calls the " +
        "native engine, substituting a well-formed result on failure. Required together with " +
        "'Allow re-signed/sideloaded installs'.",
    default = true,
) {
    compatibleWith(MEREFRAME_COMPATIBILITY)

    extendWith("extensions/extension.mpe")

    execute {
        // ── Stage 1: pinned session-opening wrapper ───────────────────────
        // Pinned to the 0.8.4 (vc 118) shape. On drift the injection is
        // aborted (never half-patched) and logged loudly; the sweep below then
        // covers the session wrapper generically, so the build degrades to the
        // native verdict instead of failing hard.
        val sessionWrapper = Fingerprint(
            definingClass = BRIDGE_CLASS,
            name = PINNED_METHOD_NAME,
            returnType = PINNED_RETURN_TYPE,
            parameters = PINNED_PARAMETERS,
        ).methodOrNull
        if (sessionWrapper == null) {
            logUniversal(
                "Bypass native integrity: PINNED SESSION WRAPPER NOT FOUND — " +
                    "${BRIDGE_CLASS}->$PINNED_METHOD_NAME(ZZZZFZ)[J does not match this build " +
                    "(app version drifted from 0.8.4 / vc 118). Falling back to the generic sweep; " +
                    "send versionName and versionCode so the fingerprint can be re-pinned.",
            )
        } else {
            sessionWrapper.apply {
                clearBody()
                addInstructions(
                    0,
                    """
                        invoke-static/range {p0 .. p5}, $HOOK_CLASS->openSessionWithFallback(ZZZZFZ)[J
                        move-result-object p0
                        return-object p0
                    """.trimIndent(),
                )
            }

            logUniversal(
                "Bypass native integrity: session wrapper routed through MereframeIntegrityHook " +
                    "(JVM try/catch) in ${BRIDGE_CLASS}->$PINNED_METHOD_NAME(ZZZZFZ)[J — " +
                    "if the crash reappears at this exact frame, this patch is not present in the " +
                    "crashing build.",
            )
        }

        // ── Stage 2: sweep the bridge class Stage 1 resolved ──────────────
        // The same class Stage 1's fingerprint matched — no second search that
        // can silently match nothing. mutableClassDefBy also upgrades the class
        // to mutable, so the injected handlers are written out.
        val bridgeClass = runCatching { mutableClassDefBy(BRIDGE_CLASS) }.getOrNull()
        if (bridgeClass == null) {
            logUniversal(
                "Bypass native integrity: bridge class $BRIDGE_CLASS not found — wrapper sweep skipped " +
                    "(stage 1 still applied). Send the app version + patching log.",
            )
        } else {
            // Native methods declared by the bridge class itself. The key is
            // name + parameter count + return type: a DEX-backed Method and an
            // invoke-side MethodReference stringify parameterTypes differently,
            // so a descriptor-string key never matched (that was the no-op bug).
            val natives = HashSet<String>()
            bridgeClass.methods.forEach { method ->
                if (AccessFlags.NATIVE.isSet(method.accessFlags)) {
                    natives += "${method.name}#${method.parameterTypes.size}#${method.returnType}"
                }
            }

            if (natives.isEmpty()) {
                logUniversal(
                    "Bypass native integrity: $BRIDGE_CLASS declares no native methods — wrapper sweep skipped.",
                )
            } else {
                val guarded = ArrayList<String>()
                val skipped = ArrayList<String>()

                bridgeClass.methods.toList().forEach { method ->
                    val descriptor = method.descriptor()
                    val reason = method.guardSkipReason(natives, BRIDGE_CLASS)
                    if (reason != null) {
                        skipped += "$descriptor ($reason)"
                    } else {
                        runCatching { plantIntegrityHandler(method, method.returnType) }
                            .onSuccess { guarded += descriptor }
                            .onFailure { skipped += "$descriptor (${it.message})" }
                    }
                }

                logUniversal(
                    "Bypass native integrity: bridge $BRIDGE_CLASS declares ${natives.size} native " +
                        "method(s) — ${guarded.size} wrapper(s) guarded, ${skipped.size} left on the " +
                        "native verdict.",
                )
                if (guarded.isNotEmpty()) {
                    logUniversal(
                        "Bypass native integrity: guarded in $BRIDGE_CLASS: ${guarded.joinToString(", ")}",
                    )
                }
                if (skipped.isNotEmpty()) {
                    logUniversal(
                        "Bypass native integrity: left in $BRIDGE_CLASS: ${skipped.joinToString(", ")}",
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Fingerprint pins (Mereframe 0.8.4 / vc 118)
// ─────────────────────────────────────────────────────────────────────────

private const val BRIDGE_CLASS = "Lapp/unnamed/rawrecording/b/B1e6ad8077f6880e309d8da4b;"

private const val PINNED_METHOD_NAME = "d"
private const val PINNED_RETURN_TYPE = "[J"
private val PINNED_PARAMETERS = listOf("Z", "Z", "Z", "Z", "F", "Z")

private const val HOOK_CLASS = "Lapp/template/extension/extension/MereframeIntegrityHook;"
private const val THROWABLE = "Ljava/lang/Throwable;"

// ─────────────────────────────────────────────────────────────────────────
// Discovery helpers
// ─────────────────────────────────────────────────────────────────────────

/** `name(ParameterDescriptors)ReturnType`, e.g. `d(ZZZZFZ)[J`. */
private fun Method.descriptor(): String =
    "$name(${parameters.joinToString("") { it.type }})$returnType"

/** Identity of a native method: name + parameter count + return type. */
private fun MethodReference.nativeKey(): String = "$name#${parameterTypes.size}#$returnType"

/**
 * @return `null` when the method must be guarded, otherwise the reason it is
 * left running the native verdict. Every reason is logged verbatim, so a zero
 * guarded count is always explainable from the patching log.
 */
private fun Method.guardSkipReason(natives: Set<String>, bridgeClass: String): String? {
    if (name.startsWith("<")) return "constructor/initializer"
    if (AccessFlags.NATIVE.isSet(accessFlags)) return "native method"
    if (AccessFlags.ABSTRACT.isSet(accessFlags)) return "abstract"
    if (!AccessFlags.PUBLIC.isSet(accessFlags)) return "not public"

    val implementation = implementation ?: return "no code"
    if (!implementation.tryBlocks.isNullOrEmpty()) return "already has a try/catch"
    if (implementation.instructions.any { it.opcode.name.endsWith("payload", ignoreCase = true) }) {
        return "switch/array payload in body"
    }

    if (definingClass == bridgeClass &&
        name == PINNED_METHOD_NAME &&
        returnType == PINNED_RETURN_TYPE &&
        parameterTypes.size == PINNED_PARAMETERS.size
    ) {
        return "already guarded by stage 1"
    }
    if (!invokesBridgeNative(natives, bridgeClass)) return "body does not call a bridge native"
    return null
}

/** True when the body directly invokes a native method declared by [bridgeClass]. */
private fun Method.invokesBridgeNative(natives: Set<String>, bridgeClass: String): Boolean {
    val implementation = implementation ?: return false
    return implementation.instructions.any { instruction ->
        if (!instruction.opcode.name.startsWith("invoke", ignoreCase = true)) return@any false
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            ?: return@any false
        reference.definingClass == bridgeClass && natives.contains(reference.nativeKey())
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Handler injection
// ─────────────────────────────────────────────────────────────────────────

/**
 * Append an exception handler to [method] and register a catch-all try block
 * covering the method's original body.
 *
 * The handler uses a single scratch register (`v0`, plus `v1` for wide
 * returns), so it needs no register-count growth and passes exactly one source
 * register to the hook — well under the five-register limit of the regular
 * `invoke-static` form.
 */
private fun plantIntegrityHandler(method: MutableMethod, returnType: String) {
    val implementation: MutableMethodImplementation = method.implementation
        ?: throw IllegalStateException("no implementation")

    val handler = handlerSmali(returnType)
        ?: throw IllegalStateException("unsupported return type $returnType")

    val registersNeeded = if (returnType == "J" || returnType == "D") 2 else 1
    check(implementation.registerCount >= registersNeeded) {
        "registerCount ${implementation.registerCount} < $registersNeeded"
    }

    // Labels must be resolved against the ORIGINAL instruction list: `to` is
    // the end of the method body, before the handler is appended.
    val endIndex = implementation.instructions.size
    val from = implementation.newLabelForIndex(0)
    val to = implementation.newLabelForIndex(endIndex)

    method.addInstructions(endIndex, handler)

    // Catch-all: the hook re-throws anything that is not an integrity failure,
    // so the wide net costs nothing but the handler call.
    implementation.addCatch(from, to, implementation.newLabelForIndex(endIndex))
}

private fun handlerSmali(returnType: String): String? = when (returnType) {
    "V" -> """
        move-exception v0
        invoke-static {v0}, $HOOK_CLASS->failOpenVoid($THROWABLE)V
        return-void
    """.trimIndent()

    "Z" -> scalarHandler("failOpenBoolean", "Z")
    "B" -> scalarHandler("failOpenByte", "B")
    "S" -> scalarHandler("failOpenShort", "S")
    "C" -> scalarHandler("failOpenChar", "C")
    "I" -> scalarHandler("failOpenInt", "I")
    "F" -> scalarHandler("failOpenFloat", "F")
    "J" -> wideHandler("failOpenLong", "J")
    "D" -> wideHandler("failOpenDouble", "D")
    "Ljava/lang/String;" -> objectHandler("failOpenString", "Ljava/lang/String;")
    "[Z" -> objectHandler("failOpenBooleanArray", "[Z")
    "[B" -> objectHandler("failOpenByteArray", "[B")
    "[S" -> objectHandler("failOpenShortArray", "[S")
    "[C" -> objectHandler("failOpenCharArray", "[C")
    "[I" -> objectHandler("failOpenIntArray", "[I")
    "[J" -> objectHandler("failOpenLongArray", "[J")
    "[F" -> objectHandler("failOpenFloatArray", "[F")
    "[D" -> objectHandler("failOpenDoubleArray", "[D")

    // Unknown reference/array-of-reference shape: there is no way to invent a
    // well-formed instance of it, so hand back null instead of crashing.
    else -> if (returnType.startsWith("L") || returnType.startsWith("[")) {
        """
            move-exception v0
            invoke-static {v0}, $HOOK_CLASS->failOpenNull($THROWABLE)V
            const/4 v0, 0x0
            return-object v0
        """.trimIndent()
    } else {
        null
    }
}

private fun scalarHandler(hookName: String, returnType: String) = """
    move-exception v0
    invoke-static {v0}, $HOOK_CLASS->$hookName($THROWABLE)$returnType
    move-result v0
    return v0
""".trimIndent()

private fun wideHandler(hookName: String, returnType: String) = """
    move-exception v0
    invoke-static {v0}, $HOOK_CLASS->$hookName($THROWABLE)$returnType
    move-result-wide v0
    return-wide v0
""".trimIndent()

private fun objectHandler(hookName: String, returnType: String) = """
    move-exception v0
    invoke-static {v0}, $HOOK_CLASS->$hookName($THROWABLE)$returnType
    move-result-object v0
    return-object v0
""".trimIndent()
