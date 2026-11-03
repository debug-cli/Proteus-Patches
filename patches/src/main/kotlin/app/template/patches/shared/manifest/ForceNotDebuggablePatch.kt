package app.template.patches.shared.manifest

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

// Universal "Force not debuggable" patch: sets android:debuggable="false" on
// <application>. Safe (release builds should never be debuggable). The patcher
// forces universal patches to default=false.

@Suppress("unused")
val forceNotDebuggablePatch = resourcePatch(
    name = "Force not debuggable",
    description = """
        Sets android:debuggable="false" in the manifest so the patched app cannot be
        inspected or modified over ADB/USB debugging.

        Applies to any app. Safe for release builds.
    """.trimIndent(),
    default = false, // universal patches are forced to default=false by the patcher
) {
    execute {
        document("AndroidManifest.xml").use { doc ->
            val application = doc.getElementsByTagName("application").item(0) as? Element
                ?: return@use
            if (application.getAttribute("android:debuggable") != "false") {
                application.setAttribute("android:debuggable", "false")
                println("Force not debuggable: android:debuggable set to false.")
            }
        }
    }
}
