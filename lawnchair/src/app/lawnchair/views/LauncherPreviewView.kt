package app.lawnchair.views

import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.graphics.LauncherPreviewRenderer
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.Themes
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlin.math.min

@SuppressLint("ViewConstructor")
class LauncherPreviewView(
    context: Context,
    private val idp: InvariantDeviceProfile,
    private val dummySmartspace: Boolean = false,
    private val dummyInsets: Boolean = false,
    private val appContext: Context = context.applicationContext,
) : FrameLayout(context) {

    private val onReadyCallbacks = RunnableList()
    private val onDestroyCallbacks = RunnableList()
    private var destroyed = false

    private var rendererView: View? = null

    private val spinner = CircularProgressIndicator(context).apply {
        val themedContext = ContextThemeWrapper(context, Themes.getActivityThemeRes(context))
        val textColor = Themes.getAttrColor(themedContext, R.attr.workspaceTextColor)
        isIndeterminate = true
        setIndicatorColor(textColor)
        trackCornerRadius = 1000
        alpha = 0f
        animate()
            .alpha(1f)
            .withLayer()
            .setStartDelay(100)
            .setDuration(300)
            .start()
    }

    init {
        addView(spinner, LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { gravity = Gravity.CENTER })
        loadAsync()
    }

    fun addOnReadyCallback(runnable: Runnable) {
        onReadyCallbacks.add(runnable)
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
        val inflationContext = ContextThemeWrapper(appContext, Themes.getActivityThemeRes(context))
        LauncherAppState.getInstance(inflationContext).model.loadAsync { dataModel ->
            if (dataModel != null) {
                MAIN_EXECUTOR.execute {
                    renderView(inflationContext, dataModel, null)
                }
            } else {
                onReadyCallbacks.executeAllAndDestroy()
                Log.e("LauncherPreviewView", "Model loading failed")
            }
        }
    }

    @UiThread
    private fun renderView(
        inflationContext: Context,
        dataModel: BgDataModel,
        widgetProviderInfoMap: Map<ComponentKey, AppWidgetProviderInfo>?,
    ) {
        if (destroyed) {
            return
        }

        val renderer = LauncherPreviewRenderer(inflationContext, idp, null, null)
        if (dummySmartspace) {
            renderer.setWorkspaceSearchContainer(R.layout.smartspace_widget_placeholder)
        }

        val view = renderer.getRenderedView(dataModel, widgetProviderInfoMap)
        updateScale(view)
        view.pivotX = if (layoutDirection == LAYOUT_DIRECTION_RTL) view.measuredWidth.toFloat() else 0f
        view.pivotY = 0f
        view.layoutParams = LayoutParams(view.measuredWidth, view.measuredHeight)
        removeView(spinner)
        rendererView = view
        addView(view)
        onReadyCallbacks.executeAllAndDestroy()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        rendererView?.let { updateScale(it) }
    }

    private fun updateScale(view: View) {
        // This aspect scales the view to fit in the surface and centers it
        val scale: Float = min(
            measuredWidth / view.measuredWidth.toFloat(),
            measuredHeight / view.measuredHeight.toFloat(),
        )
        view.scaleX = scale
        view.scaleY = scale
    }
}
