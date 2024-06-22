package app.lawnchair.search.algorithms.data

import android.provider.Settings
import android.util.Log
import java.lang.reflect.Modifier
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SettingInfo(
    val id: String,
    val name: String,
    val action: String,
    val requiresUri: Boolean = false,
)

suspend fun findSettingsByNameAndAction(query: String, max: Int): List<SettingInfo> = try {
    if (query.isBlank() || max <= 0) {
        emptyList()
    } else {
        withContext(
            Dispatchers.IO + CoroutineExceptionHandler { _, e ->
                Log.e("SettingSearch", "Something went wrong ", e)
            },
        ) {
            Settings::class.java.fields
                .asSequence()
                .filter {
                    it.type == String::class.java && Modifier.isStatic(it.modifiers) && it.name.startsWith(
                        "ACTION_",
                    )
                }
                .map { it.name to it.get(null) as String }
                .filter { (name, action) ->
                    name.contains(query, ignoreCase = true) &&
                        !action.contains("REQUEST", ignoreCase = true) &&
                        !name.contains("REQUEST", ignoreCase = true) &&
                        !action.contains("PERMISSION", ignoreCase = true) &&
                        !name.contains("DETAIL", ignoreCase = true) &&
                        !name.contains("REMOTE", ignoreCase = true)
                }
                .map { (name, action) ->
                    val id = name + action
                    val requiresUri = action.contains("URI")
                    SettingInfo(id, name, action, requiresUri)
                }
                .toList().take(max)
        }
    }
} catch (e: Exception) {
    Log.e("SettingSearch", "Something went wrong ", e)
    emptyList()
}
