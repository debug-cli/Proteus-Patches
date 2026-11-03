package app.template.patches.shared.manifest

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

// Universal "Block cleartext traffic" patch: sets
// android:usesCleartextTraffic="false" on <application>. Default = false
// (breaks apps with legacy HTTP endpoints).

@Suppress("unused")
val blockCleartextTrafficPatch = resourcePatch(
    name = "Block cleartext (HTTP) traffic",
    description = """
        Sets android:usesCleartextTraffic="false" so the app cannot send plaintext
        HTTP requests. Breaks apps that still use legacy HTTP endpoints.

        Applies to any app.
    """.trimIndent(),
    default = false,
) {
    execute {
        document("AndroidManifest.xml").use { doc ->
            val application = doc.getElementsByTagName("application").item(0) as? Element
                ?: return@use
            if (application.getAttribute("android:usesCleartextTraffic") != "false") {
                application.setAttribute("android:usesCleartextTraffic", "false")
                println("Block cleartext: android:usesCleartextTraffic set to false.")
            }
        }
    }
}
