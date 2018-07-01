package ch.deletescape.lawnchair.gestures

import android.content.Context
import com.android.launcher3.R
import org.json.JSONObject

abstract class GestureHandler(val context: Context, val config: JSONObject?) {

    abstract val displayName: String

    abstract fun onGestureTrigger(controller: GestureController)

    protected open fun saveConfig(config: JSONObject) {

    }

    override fun toString(): String {
        return JSONObject().apply {
            put("class", this::class.java.name)
            put("config", saveConfig(JSONObject()))
        }.toString()
    }
}

class BlankGestureHandler(context: Context) : GestureHandler(context, null) {

    override val displayName = context.getString(R.string.action_none)!!

    override fun onGestureTrigger(controller: GestureController) {

    }
}
