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
import com.android.tools.smali.dexlib2.iface.ClassDef
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
 * Stage 2 (sweep): every OTHER public wrapper on the bridge class — and on any
 * sibling bridge class in the same app package — whose body invokes a native
 * method gets a REAL try/catch installed in its own code item:
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
 * try blocks, body invokes a native method declared in the app's own bridge
 * package) and emits the resolved inventory — class, method name, parameter
 * descriptor and return type — into the patching log on every run. That log
 * line is the authoritative enumeration for the build being patched.
 *
 * FINGERPRINT DRIFT
 *
 * Stage 1 is pinned to the 0.8.4 (vc 118) shape and fails loudly if it drifts
 * (the manager reports the patch, which is how issue #5 was diagnosed in the
 * first place). Stage 2 is drift-tolerant by design: each wrapper is a separate
 * best-effort injection wrapped in runCatching — a wrapper that cannot be
 * guarded (unsupported return type, too few registers, pre-existing try/catch)
 * is logged and left running the native verdict, never half-patched and never
 * a hard failure.
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
        "same real try/catch in every other public bridge wrapper that calls the native engine, " +
        "substituting a well-formed result on failure. Required together with " +
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
                    "if the crash reappears at this exact frame, this patch is not present in the crashing build.",
            )
        }

        // ── Stage 2: sweep every other native-calling public wrapper ───────
        val nativeMethods = HashSet<String>()
        classDefForEach { classDef ->
            if (!classDef.type.startsWith(BRIDGE_PACKAGE_PREFIX)) return@classDefForEach
            classDef.methods.forEach { method ->
                if (AccessFlags.NATIVE.isSet(method.accessFlags)) {
                    nativeMethods += "${method.definingClass}->${method.signature()}"
                }
            }
        }

        if (nativeMethods.isEmpty()) {
            logUniversal(
                "Bypass native integrity: no native methods found under $BRIDGE_PACKAGE_PREFIX — " +
                    "wrapper sweep skipped (stage 1 still applied). Send the app version + patching log.",
            )
        } else {
            // Discover first (immutable view), mutate second. mutableClassDefBy
            // upgrades a whole class to mutable, so it is only called for the
            // bridge classes that actually carry wrappers.
            val bridges = LinkedHashMap<String, Pair<ClassDef, List<Method>>>()
            classDefForEach { classDef ->
                if (!classDef.type.startsWith(BRIDGE_PACKAGE_PREFIX)) return@classDefForEach
                val wrappers = classDef.methods.filter { method ->
                    method.isGuardableWrapper(nativeMethods)
                }
                if (wrappers.isNotEmpty()) bridges[classDef.type] = classDef to wrappers
            }

            val guarded = ArrayList<String>()
            val skipped = ArrayList<String>()

            bridges.forEach { (classType, entry) ->
                val (classDef, wrappers) = entry
                val mutableClass = runCatching { mutableClassDefBy(classDef) }.getOrNull()
                if (mutableClass == null) {
                    wrappers.forEach { method ->
                        skipped += "$classType->${method.signature()} (class not mutable)"
                    }
                    return@forEach
                }
                val mutableMethods = mutableClass.methods.associateBy { it.signature() }
                wrappers.forEach { wrapper ->
                    val key = "$classType->${wrapper.signature()}"
                    val target = mutableMethods[wrapper.signature()]
                    if (target == null) {
                        skipped += "$key (no mutable method)"
                        return@forEach
                    }
                    runCatching { plantIntegrityHandler(target, wrapper.returnType) }
                        .onSuccess { guarded += key }
                        .onFailure { skipped += "$key (${it.message})" }
                }
            }

            guarded.groupBy { it.substringBefore("->") }.forEach { (classType, methods) ->
                logUniversal(
                    "Bypass native integrity: guarded ${methods.size} native-calling wrapper(s) in $classType: " +
                        methods.joinToString(", ") { method -> method.substringAfter("->") },
                )
            }
            if (skipped.isNotEmpty()) {
                logUniversal(
                    "Bypass native integrity: left ${skipped.size} wrapper(s) on the native verdict: " +
                        skipped.joinToString(", "),
                )
            }
            logUniversal(
                "Bypass native integrity: sweep complete — ${guarded.size} wrapper(s) guarded across " +
                    "${bridges.size} bridge class(es), ${skipped.size} left unguarded. " +
                    "Guarded wrappers now fail open on SecurityException(\"app integrity check failed\"); " +
                    "every other Throwable is re-thrown unchanged.",
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Fingerprint pins (Mereframe 0.8.4 / vc 118)
// ─────────────────────────────────────────────────────────────────────────

private const val BRIDGE_PACKAGE_PREFIX = "Lapp/unnamed/rawrecording/"

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
private fun Method.signature(): String = "$name(${parameterTypes.joinToString("")})$returnType"

private fun MethodReference.referenceKey(): String = "$name(${parameterTypes.joinToString("")})$returnType"

/**
 * A method is guardable when it is a thin public wrapper around one of the
 * bridge's native methods and can take a whole-body try/catch:
 *
 * - public, not native, not abstract, has a body, is not a constructor;
 * - its body invokes a native method declared in the bridge package;
 * - it has no try/catch of its own — an overlapping range would produce a
 *   partially overlapping try block and a VerifyError ("bad exception entry"),
 *   so such methods are left alone rather than half-patched;
 * - its body contains no switch/array payload pseudo-instruction — a whole-body
 *   try range would stretch over the payload data, which ART refuses to verify;
 * - it is not the pinned session wrapper, which stage 1 already replaced.
 */
private fun Method.isGuardableWrapper(nativeMethods: Set<String>): Boolean {
    if (name.startsWith("<")) return false
    if (!AccessFlags.PUBLIC.isSet(accessFlags)) return false
    if (AccessFlags.NATIVE.isSet(accessFlags)) return false
    if (AccessFlags.ABSTRACT.isSet(accessFlags)) return false

    val implementation = implementation ?: return false
    if (!implementation.tryBlocks.isNullOrEmpty()) return false
    if (implementation.instructions.any { it.opcode.name.endsWith("_PAYLOAD") }) return false

    if (definingClass == BRIDGE_CLASS &&
        name == PINNED_METHOD_NAME &&
        signature() == "$PINNED_METHOD_NAME(${PINNED_PARAMETERS.joinToString("")})$PINNED_RETURN_TYPE"
    ) {
        return false
    }

    return implementation.instructions.any { instruction ->
        if (!instruction.opcode.name.startsWith("INVOKE")) return@any false
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            ?: return@any false
        nativeMethods.contains("${reference.definingClass}->${reference.referenceKey()}")
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
