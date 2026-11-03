package app.template.patches.shared.manifest

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import org.w3c.dom.Element

// Universal "Remove dangerous permissions" patch.
// Deletes <uses-permission>/<uses-permission-sdk-23> entries in the
// configurable removal set (default: package visibility, phone state,
// accounts, location, contacts, mic, screen-capture detection, ad ID —
// storage & SMS kept) plus the ACCESS_ADSERVICES_* prefix. Fail-safe;
// default = false.

private val DEFAULT_PERMISSIONS_TO_REMOVE = setOf(
    "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.READ_PHONE_STATE",
    "android.permission.READ_BASIC_PHONE_STATE",
    "android.permission.READ_PHONE_NUMBERS",
    "android.permission.GET_ACCOUNTS",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.ACCESS_BACKGROUND_LOCATION",
    "android.permission.READ_CONTACTS",
    "android.permission.WRITE_CONTACTS",
    "android.permission.RECORD_AUDIO",
    "android.permission.DETECT_SCREEN_CAPTURE",
    "com.google.android.gms.permission.AD_ID",
)

private const val ADSERVICES_PREFIX = "android.permission.ACCESS_ADSERVICES_"

@Suppress("unused")
val removeDangerousPermissionsPatch = resourcePatch(
    name = "Remove dangerous permissions",
    description = """
        Removes privacy-invasive permissions (package visibility, phone state, accounts,
        location, contacts, microphone, screen-capture detection, advertising ID) from any
        app's manifest. Storage and SMS permissions are kept by default.

        The removal list is configurable. Removing a permission the app actually needs can
        break features (login, calls, address auto-detect).
    """.trimIndent(),
    default = false,
) {
    val permissionsToRemove by stringOption(
        key = "permissionsToRemove",
        default = DEFAULT_PERMISSIONS_TO_REMOVE.joinToString(","),
        title = "Permissions to remove",
        description = "Comma-separated list of permission names to strip from the manifest.",
        required = false,
    ) { it == null || it.split(",").all { token -> token.trim().isNotEmpty() } }

    execute {
        val removalSet = (permissionsToRemove ?: DEFAULT_PERMISSIONS_TO_REMOVE.joinToString(","))
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        document("AndroidManifest.xml").use { doc ->
            var removed = 0
            listOf("uses-permission", "uses-permission-sdk-23").forEach { tag ->
                val nodes = doc.getElementsByTagName(tag)
                for (index in nodes.length - 1 downTo 0) {
                    val node = nodes.item(index) as? Element ?: continue
                    val name = node.getAttribute("android:name")
                    if (name in removalSet || name.startsWith(ADSERVICES_PREFIX)) {
                        node.parentNode?.removeChild(node)
                        removed++
                    }
                }
            }
            println("Remove dangerous permissions: $removed permission(s) removed.")
        }
    }
}
