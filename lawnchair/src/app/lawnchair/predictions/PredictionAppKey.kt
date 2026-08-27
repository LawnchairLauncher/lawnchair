package app.lawnchair.predictions

import android.content.ComponentName

object PredictionAppKey {
    private const val DELIMITER = "/"

    data class TargetInfo(
        val packageName: String,
        val className: String,
        val userToken: String,
    )

    fun create(componentName: ComponentName, userToken: String): String = "${componentName.packageName}$DELIMITER${componentName.className}$DELIMITER$userToken"

    fun packageName(key: String): String = key.substringBefore(DELIMITER)

    fun parse(key: String): TargetInfo? {
        val parts = key.split(DELIMITER)
        if (parts.size != 3 || parts.any { it.isEmpty() }) return null
        return TargetInfo(parts[0], parts[1], parts[2])
    }
}
