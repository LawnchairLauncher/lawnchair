package app.lawnchair.smartspace.provider

import android.content.Context
import com.android.launcher3.util.MainThreadInitializedObject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.shareIn

class SmartspaceProvider private constructor(context: Context) {

    private val dataSources = listOf(
        SmartspaceWidgetReader(context),
        BatteryStatusProvider(context),
        NowPlayingProvider(context)
    )

    val targets = dataSources
        .map { it.targets }
        .reduce { acc, flow -> flow.combine(acc) { a, b -> a + b } }
        .shareIn(
            MainScope(),
            SharingStarted.WhileSubscribed(),
            replay = 1
        )

    companion object {
        @JvmField val INSTANCE = MainThreadInitializedObject(::SmartspaceProvider)
    }
}
