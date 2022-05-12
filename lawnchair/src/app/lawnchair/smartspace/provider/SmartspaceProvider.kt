package app.lawnchair.smartspace.provider

import android.content.Context
import app.lawnchair.smartspace.model.SmartspaceTarget
import com.android.launcher3.util.MainThreadInitializedObject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class SmartspaceProvider private constructor(context: Context) {

    private val scope = MainScope()
    private val targetsFlow = MutableStateFlow<List<SmartspaceTarget>>(emptyList())
    val targets get() = targetsFlow

    init {
        val widgetReader = SmartspaceWidgetReader(context)
        widgetReader.targets
            .onEach { targetsFlow.value = it }
            .launchIn(scope)
    }

    companion object {
        @JvmField val INSTANCE = MainThreadInitializedObject(::SmartspaceProvider)
    }
}
