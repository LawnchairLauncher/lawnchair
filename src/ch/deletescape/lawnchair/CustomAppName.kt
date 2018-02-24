package ch.deletescape.lawnchair

import android.content.ComponentName
import org.json.JSONObject

class CustomAppName(private val prefs: LawnchairPreferences) {

    private val appMap = HashMap<ComponentName, String>()

    init {
        val obj = JSONObject(prefs.appNameMap)
        obj.keys().forEach {
            val componentName = ComponentName.unflattenFromString(it)
            appMap[componentName] = obj.getString(it)
        }
    }

    operator fun set(componentName: ComponentName, value: String?) {
        if (value != null) {
            appMap[componentName] = value
        } else {
            appMap.remove(componentName)
        }
        val obj = JSONObject()
        appMap.entries.forEach {
            obj.put(it.key.flattenToString(), it.value)
        }
        prefs.appNameMap = obj.toString()
    }

    operator fun get(componentName: ComponentName): String? {
        return appMap[componentName]
    }
}