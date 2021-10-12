package app.lawnchair.views

import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.util.Log
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import app.lawnchair.DeviceProfileOverrides
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites.*
import com.android.launcher3.R
import com.android.launcher3.graphics.LauncherPreviewRenderer
import com.android.launcher3.model.*
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.RunnableList
import kotlin.math.min

@SuppressLint("ViewConstructor")
class LauncherPreviewView(
    context: Context,
    options: DeviceProfileOverrides.Options
) : FrameLayout(context) {

    private val onDestroyCallbacks = RunnableList()
    private var destroyed = false

    private val idp = InvariantDeviceProfile(context, options)

    init {
        loadAsync()
    }

    @UiThread
    fun destroy() {
        destroyed = true
        onDestroyCallbacks.executeAllAndDestroy()
        removeAllViews()
    }

    private fun loadAsync() {
        MODEL_EXECUTOR.execute(this::loadModelData)
    }

    @WorkerThread
    private fun loadModelData() {
        val migrated = doGridMigrationIfNecessary()

        val inflationContext = ContextThemeWrapper(context, R.style.AppTheme)
        if (migrated) {
            val previewContext = LauncherPreviewRenderer.PreviewContext(inflationContext, idp)
            object : LoaderTask(
                LauncherAppState.getInstance(previewContext),
                null,
                BgDataModel(),
                ModelDelegate(), null
            ) {
                override fun run() {
                    loadWorkspace(
                        emptyList(), PREVIEW_CONTENT_URI,
                        "$SCREEN = 0 or $CONTAINER = $CONTAINER_HOTSEAT"
                    )
                    MAIN_EXECUTOR.execute {
                        renderView(previewContext, mBgDataModel, mWidgetProvidersMap)
                        onDestroyCallbacks.add { previewContext.onDestroy() }
                    }
                }
            }.run()
        } else {
            object : ModelPreload() {
                override fun onComplete(isSuccess: Boolean) {
                    if (isSuccess) {
                        MAIN_EXECUTOR.execute {
                            renderView(inflationContext, bgDataModel, null)
                        }
                    } else {
                        Log.e("LauncherPreviewView", "Model loading failed")
                    }
                }
            }.start(inflationContext)
        }
    }

    @WorkerThread
    private fun doGridMigrationIfNecessary(): Boolean {
        val needsToMigrate = GridSizeMigrationTaskV2.needsToMigrate(context, idp)
        if (!needsToMigrate) {
            return false
        }
        return GridSizeMigrationTaskV2.migrateGridIfNeeded(context, idp)
    }

    @UiThread
    private fun renderView(
        inflationContext: Context,
        dataModel: BgDataModel,
        widgetProviderInfoMap: Map<ComponentKey, AppWidgetProviderInfo>?
    ) {
        if (destroyed) {
            return
        }

        val view = LauncherPreviewRenderer(inflationContext, idp, null)
            .getRenderedView(dataModel, widgetProviderInfoMap)
        // This aspect scales the view to fit in the surface and centers it
        val scale: Float = min(
            measuredWidth / view.measuredWidth.toFloat(),
            measuredHeight / view.measuredHeight.toFloat()
        )
        view.scaleX = scale
        view.scaleY = scale
        view.pivotX = 0f
        view.pivotY = 0f
        view.layoutParams = LayoutParams(view.measuredWidth, view.measuredHeight)
        addView(view)
    }
}
