package app.lawnchair.smartspace.provider

import android.content.Context
import app.lawnchair.smartspace.model.SmartspaceTarget
import com.android.launcher3.util.MainThreadInitializedObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

typealias TargetsFlow = Flow<List<SmartspaceTarget>>

class SmartspaceProvider private constructor(context: Context) {

    private val flows = mutableListOf<TargetsFlow>()
    val targets: TargetsFlow

    init {
        flows.add(SmartspaceWidgetReader(context).targets)
        flows.add(BatteryStatusProvider(context).targets)
        flows.add(NowPlayingProvider(context).targets)
        targets = flows.reduce { acc, flow -> flow.combine(acc) { a, b -> a + b } }
    }

    companion object {
        @JvmField val INSTANCE = MainThreadInitializedObject(::SmartspaceProvider)
    }
}
