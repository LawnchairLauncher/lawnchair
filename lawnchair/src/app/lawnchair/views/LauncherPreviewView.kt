package app.lawnchair.views

import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.UiThread
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.preview.LauncherPreviewRenderer
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.Themes
import com.android.launcher3.widget.LauncherWidgetHolder
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlin.math.min

@SuppressLint("ViewConstructor")
class LauncherPreviewView(
    context: Context,
    private val idp: InvariantDeviceProfile,
    private val dummySmartspace: Boolean = false,
    private val dummyInsets: Boolean = false, // Note: New Renderer calculates insets internally based on Context
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
        // Note: The new LauncherPreviewRenderer manages its own lifecycle observer via the Context.
        // If the Renderer exposes a close/destroy method in the future, call it here to prevent Model callback leaks.
    }

    private fun loadAsync() {
        // The new Renderer requires the LauncherModel to be passed in,
        // and it handles the loading callbacks internally.
        val model = LauncherAppState.getInstance(appContext).model

        // Create the renderer on the Main Thread (it initializes handlers)
        // Workspace.FIRST_SCREEN_ID is typically 0
        val workspaceScreenId = 0
        val themeRes = Themes.getActivityThemeRes(context)

        // We use the current context. The Renderer extends BaseContext,
        // so it will wrap this context internally.
        val renderer = LauncherPreviewRenderer(
            context,
            workspaceScreenId,
            null, // Wallpaper colors
            model,
            themeRes,
        )

        /*
           pE-TODO(QPR1/Collision-Gemini3Pro): MIGRATION NOTE
           The new LauncherPreviewRenderer provided is the AOSP upstream version.
           It does not yet have the Lawnchair methods 'setWorkspaceSearchContainer'.

           To support 'dummySmartspace', you must re-apply the Lawnchair patch to
           com.android.launcher3.preview.LauncherPreviewRenderer.java:

           1. Add field: private int mWorkspaceSearchContainer = R.layout.qsb_preview;
           2. Add method: public void setWorkspaceSearchContainer(int resId) { mWorkspaceSearchContainer = resId; }
           3. Use 'mWorkspaceSearchContainer' inside 'bindCompleteModel' when inflating the QSB.
         */
        if (dummySmartspace) {
            // renderer.setWorkspaceSearchContainer(R.layout.smartspace_widget_placeholder)
        }

        // The renderer exposes a CompletableFuture that completes when the model is bound and view is measured
        renderer.initialRender.thenAcceptAsync({ view ->
            if (destroyed) return@thenAcceptAsync

            if (view != null) {
                configureAndAttachView(view)
            } else {
                onReadyCallbacks.executeAllAndDestroy()
                Log.e("LauncherPreviewView", "Model loading failed or View is null")
            }
        }, MAIN_EXECUTOR)
    }

    @UiThread
    private fun configureAndAttachView(view: View) {
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
        if (view.measuredWidth == 0 || view.measuredHeight == 0) return

        val scale: Float = min(
            measuredWidth / view.measuredWidth.toFloat(),
            measuredHeight / view.measuredHeight.toFloat(),
        )
        view.scaleX = scale
        view.scaleY = scale
    }
}
