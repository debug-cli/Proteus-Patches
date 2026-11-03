package app.template.patches.shared.manifest

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

// Universal "Disable backup" patch: sets android:allowBackup="false" so the
// app's private data is excluded from ADB/cloud backups and device transfers.
// Default = false (users lose backup/restore).

@Suppress("unused")
val disableAllowBackupPatch = resourcePatch(
    name = "Disable app backup (allowBackup=false)",
    description = """
        Sets android:allowBackup="false" so the app's private data is excluded from
        ADB backups, cloud backups and device-to-device transfers.

        Applies to any app. You lose automatic backup/restore of this app's data.
    """.trimIndent(),
    default = false,
) {
    execute {
        document("AndroidManifest.xml").use { doc ->
            val application = doc.getElementsByTagName("application").item(0) as? Element
                ?: return@use
            if (application.getAttribute("android:allowBackup") != "false") {
                application.setAttribute("android:allowBackup", "false")
                println("Disable backup: android:allowBackup set to false.")
            }
        }
    }
}
