package app.template.patches.shared

import app.morphe.patcher.patch.PatchException

/** A placeholder must never report a successful privacy or security change. */
internal fun unimplementedAppPatch(name: String): Nothing = throw PatchException(
    "$name is an unimplemented placeholder and makes no changes. " +
        "App-specific APK analysis is required before this patch can be used.",
)
