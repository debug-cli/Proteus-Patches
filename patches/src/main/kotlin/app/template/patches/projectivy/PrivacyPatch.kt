package app.template.patches.projectivy

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private val projectivyCompatibility = Compatibility(
    name = "Projectivy Launcher",
    packageName = "com.spocky.projengmenu",
    apkFileType = ApkFileType.APK,
    targets = listOf(AppTarget(version = "4.73", versionCode = 98)),
)

/** Limits known Google telemetry; it does not remove SDK code or block all network tracking. */
@Suppress("unused")
val projectivyLimitTelemetryPatch = resourcePatch(
    name = "Limit Google telemetry",
    description = "Disables Firebase analytics and performance collection and removes advertising ID and install-referrer permissions.",
    default = true,
) {
    compatibleWith(projectivyCompatibility)

    execute {
        document("AndroidManifest.xml").use { doc ->
            val permissions = setOf(
                "com.google.android.gms.permission.AD_ID",
                "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE",
                "android.permission.ACCESS_ADSERVICES_AD_ID",
                "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
            )
            val nodes = doc.getElementsByTagName("uses-permission")
            for (i in nodes.length - 1 downTo 0) {
                val element = nodes.item(i) as Element
                if (element.getAttribute("android:name") in permissions) {
                    element.parentNode.removeChild(element)
                }
            }

            val application = doc.getElementsByTagName("application").item(0) as Element
            val values = mapOf(
                "firebase_analytics_collection_deactivated" to "true",
                "firebase_analytics_collection_enabled" to "false",
                "firebase_performance_collection_deactivated" to "true",
                "firebase_performance_collection_enabled" to "false",
                "firebase_crashlytics_collection_enabled" to "false",
            )
            for ((name, value) in values) {
                val children = application.childNodes
                var entry: Element? = null
                for (i in 0 until children.length) {
                    val child = children.item(i) as? Element ?: continue
                    if (child.tagName == "meta-data" && child.getAttribute("android:name") == name) {
                        check(entry == null) { "Duplicate application metadata: $name" }
                        entry = child
                    }
                }
                val metadata = entry ?: doc.createElement("meta-data").also {
                    it.setAttribute("android:name", name)
                    application.appendChild(it)
                }
                metadata.setAttribute("android:value", value)
            }
        }
    }
}
