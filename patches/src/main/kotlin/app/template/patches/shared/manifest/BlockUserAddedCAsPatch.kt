package app.template.patches.shared.manifest

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

// Universal "Block user-added CAs" patch: if the app declares its own
// android:networkSecurityConfig, removes every <certificates src="user"/>
// element from the referenced res/xml. No config -> no-op (Android already
// ignores user CAs by default for API 24+). Fail-safe; default = false.

@Suppress("unused")
val blockUserAddedCAsPatch = resourcePatch(
    name = "Block user-added certificate authorities",
    description = """
        Removes user-added certificate authorities from the app's network security
        config (if it declares one), so the app does not trust certificates installed
        on the device.

        Applies to any app. No effect if the app has no network security config.
    """.trimIndent(),
    default = false,
) {
    execute {
        document("AndroidManifest.xml").use { doc ->
            val application = doc.getElementsByTagName("application").item(0) as? Element
                ?: return@use
            val nsc = application.getAttribute("android:networkSecurityConfig")
            if (nsc.isEmpty() || !nsc.startsWith("@xml/")) {
                println("Block user-added CAs: no app network security config — nothing to do.")
                return@use
            }

            val path = "res/xml/" + nsc.removePrefix("@xml/") + ".xml"
            try {
                document(path).use { nscDoc ->
                    val certs = nscDoc.getElementsByTagName("certificates")
                    var removed = 0
                    for (index in certs.length - 1 downTo 0) {
                        val element = certs.item(index) as? Element ?: continue
                        if (element.getAttribute("src") == "user") {
                            element.parentNode?.removeChild(element)
                            removed++
                        }
                    }
                    println("Block user-added CAs: removed $removed user-CA entr(ies) from $path.")
                }
            } catch (e: Exception) {
                println("Block user-added CAs: could not read $path (${e.message}) — left stock.")
            }
        }
    }
}
