package app.lawnchair.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.util.Pair
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.core.graphics.createBitmap
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import app.lawnchair.LawnchairLauncher
import app.lawnchair.launcher
import com.android.app.animation.Interpolators
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.CellLayout
import com.android.launcher3.GestureNavContract
import com.android.launcher3.Insettable
import com.android.launcher3.LauncherAnimUtils
import com.android.launcher3.QuickstepTransitionManager.CONTENT_SCALE_DURATION
import com.android.launcher3.QuickstepTransitionManager.LaunchDepthController
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.Executors
import com.android.launcher3.util.MultiPropertyFactory
import com.android.launcher3.util.window.RefreshRateTracker.Companion.getSingleFrameMs
import com.android.launcher3.views.FloatingIconView.getLocationBoundsForView
import com.android.launcher3.views.FloatingIconViewCompanion.setPropertiesVisible
import java.util.function.Consumer
import kotlin.math.roundToInt

class LawnchairFloatingSurfaceView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractFloatingView(context, attrs, defStyleAttr),
    OnGlobalLayoutListener,
    Insettable,
    SurfaceHolder.Callback2 {
    private val mTmpPosition = RectF()

    private val mLauncher: LawnchairLauncher = context!!.launcher
    private val mIconPosition = RectF()
    private val mDeviceProfile = mLauncher.deviceProfile

    private val mIconBounds: Rect = Rect()
    private val mRemoveViewRunnable = Runnable { this.removeViewFromParent() }

    private val mSurfaceView: SurfaceView = SurfaceView(context)

    private var mIcon: View? = null
    private var mIconBitmap: Bitmap? = null
    private var mContract: GestureNavContract? = null

    init {
        mSurfaceView.setLayerType(LAYER_TYPE_HARDWARE, null)
        mSurfaceView.setZOrderOnTop(true)

        mSurfaceView.holder.setFormat(PixelFormat.TRANSLUCENT)
        mSurfaceView.holder.addCallback(this)

        mIsOpen = true
        addView(mSurfaceView)
    }

    override fun handleClose(animate: Boolean) {
        setCurrentIconVisible(true)
        mLauncher.viewCache.recycleView(R.layout.floating_surface_view, this)
        mContract = null
        mIcon = null
        mIsOpen = false

        // Remove after some time, to avoid flickering
        Executors.MAIN_EXECUTOR.handler.postDelayed(
            mRemoveViewRunnable,
            mLauncher.getSingleFrameMs().toLong(),
        )
    }

    private fun removeViewFromParent() {
        if (mIconBitmap != null) {
            mIconBitmap!!.recycle()
            mIconBitmap = null
        }
        mLauncher.dragLayer.removeViewInLayout(this)
    }

    private fun removeViewImmediate() {
        // Cancel any pending remove
        Executors.MAIN_EXECUTOR.handler.removeCallbacks(mRemoveViewRunnable)
        if (isAttachedToWindow) {
            removeViewFromParent()
        }
    }

    override fun isOfType(type: Int): Boolean {
        return (type and TYPE_ICON_SURFACE) != 0
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent?): Boolean {
        close(false)
        removeViewImmediate()
        return false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        getViewTreeObserver().addOnGlobalLayoutListener(this)
        updateIconLocation()
    }

    fun getLauncherContentAnimator(
        startDelay: Int,
    ): Pair<AnimatorSet?, Runnable?> {
        val launcherAnimator = AnimatorSet()
        val endListener: Runnable?

        val scales = floatArrayOf(mDeviceProfile.workspaceContentScale, 1f)

        mLauncher.pauseExpensiveViewUpdates()

        val viewsToAnimate: MutableList<View?> = ArrayList<View?>()
        val workspace = mLauncher.workspace
        workspace.forEachVisiblePage(
            Consumer { view: View? -> viewsToAnimate.add((view as CellLayout).shortcutsAndWidgets) },
        )
        viewsToAnimate.add(mLauncher.hotseat)

        viewsToAnimate.forEach(
            Consumer { view: View? ->
                val scaleAnim =
                    ObjectAnimator.ofFloat<View?>(view, LauncherAnimUtils.SCALE_PROPERTY, *scales)
                        .setDuration(CONTENT_SCALE_DURATION.toLong() * 3)
                scaleAnim.interpolator = Interpolators.DECELERATE_1_5
                launcherAnimator.play(scaleAnim)
            },
        )

        endListener = Runnable {
            viewsToAnimate.forEach(
                Consumer { view: View? ->
                    LauncherAnimUtils.SCALE_PROPERTY.set(view, 1f)
                    view!!.setLayerType(LAYER_TYPE_NONE, null)
                },
            )
            mLauncher.resumeExpensiveViewUpdates()
        }

        launcherAnimator.setStartDelay(startDelay.toLong())
        return Pair<AnimatorSet?, Runnable?>(launcherAnimator, endListener)
    }

    private fun getBackgroundAnimator(): ObjectAnimator {
        val depthController = LaunchDepthController(mLauncher)
        val targetDepth = mLauncher.stateManager.state.getDepth<LawnchairLauncher?>(mLauncher)

        val backgroundRadiusAnim = createDepthAnimator(
            depthController,
            targetDepth,
            onEnd = {
                if (Utilities.ATLEAST_R) {
                    val viewRootImpl = mLauncher.dragLayer.getViewRootImpl()
                    val parent = viewRootImpl?.surfaceControl
                    val dimLayer: SurfaceControl = SurfaceControl.Builder()
                        .setName("Blur layer")
                        .setParent(parent)
                        .setOpaque(false)
                        .setEffectLayer()
                        .build()

                    createDepthAnimator(
                        depthController,
                        mLauncher.depthController.stateDepth.value,
                    ) {
                        depthController.dispose()
                        SurfaceControl.Transaction().remove(dimLayer).apply()
                    }.start()
                } else {
                    createDepthAnimator(
                        depthController,
                        mLauncher.depthController.stateDepth.value,
                    ) {
                        depthController.dispose()
                    }.start()
                }
            },
        )

        return backgroundRadiusAnim
    }

    private fun createDepthAnimator(
        depthController: LaunchDepthController,
        targetDepth: Float,
        onEnd: (() -> Unit)? = null,
    ): ObjectAnimator {
        return ObjectAnimator.ofFloat(
            depthController.stateDepth,
            MultiPropertyFactory.MULTI_PROPERTY_VALUE,
            targetDepth,
        ).apply {
            duration = CONTENT_SCALE_DURATION.toLong() * 2
            interpolator = Interpolators.DECELERATE_2
            onEnd?.let {
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            it()
                        }
                    },
                )
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        getViewTreeObserver().removeOnGlobalLayoutListener(this)
        setCurrentIconVisible(true)
    }

    override fun onGlobalLayout() {
        updateIconLocation()
    }

    fun getIcon(): View? {
        return mLauncher.getFirstHomeElementForAppClose(
            null, /* StableViewInfo */
            mContract!!.componentName.packageName,
            mContract!!.user,
        )
    }

    override fun setInsets(insets: Rect?) {}

    private fun updateIconLocation() {
        if (mContract == null) {
            return
        }

        synchronized(this) {
            val icon = getIcon()

            val iconChanged = mIcon !== icon
            if (iconChanged) {
                setCurrentIconVisible(true)
                mIcon = icon
                setCurrentIconVisible(false)
            }

            if (icon != null) {
                getLocationBoundsForView(mLauncher, icon, false, mTmpPosition, mIconBounds)
                if (mTmpPosition != mIconPosition) {
                    mIconPosition.set(mTmpPosition)
                    updateSurfaceViewLayout()
                }
            }

            sendIconInfo()

            if (mIcon != null && iconChanged && !mIconBounds.isEmpty) {
                if (mIconBitmap == null || mIconBitmap!!.getWidth() != mIconBounds.width() || mIconBitmap!!.getHeight() != mIconBounds.height()) {
                    if (mIconBitmap != null) mIconBitmap!!.recycle()
                    mIconBitmap = createBitmap(
                        mIconBounds.width(),
                        mIconBounds.height(),
                        Bitmap.Config.ARGB_8888,
                    )
                }
                postInvalidateIconDrawing()
            }
        }
    }

    private fun postInvalidateIconDrawing() {
        synchronized(this) {
            post {
                if (mIcon == null) return@post
                drawIconOnBitmap()
                drawOnSurface()
                bouncyIcon()
            }
        }
    }

    private fun updateSurfaceViewLayout() {
        post {
            val lp = mSurfaceView.layoutParams as LayoutParams
            lp.width = mIconPosition.width().roundToInt()
            lp.height = mIconPosition.height().roundToInt()
            lp.leftMargin = mIconPosition.left.roundToInt()
            lp.topMargin = mIconPosition.top.roundToInt()
        }
    }

    private fun drawIconOnBitmap() {
        if (mIcon != null && !mIconBounds.isEmpty) {
            setCurrentIconVisible(true)
            try {
                val c = Canvas(mIconBitmap!!)
                c.translate(-mIconBounds.left.toFloat(), -mIconBounds.top.toFloat())
                mIcon!!.draw(c)
            } catch (t: Throwable) {
                Log.e(this.javaClass.name, "drawIconOnBitmap: ", t)
            }
            setCurrentIconVisible(false)
        }
    }

    private fun bouncyIcon() {
        mIcon ?: return

        val (startX, startY) = mIconPosition.left to mIconPosition.top - ((height * 0.2f) / 3)

        listOf(
            SpringAnimation(mIcon, DynamicAnimation.TRANSLATION_X, 1f).apply {
                spring = SpringForce(1f).setStiffness(SpringForce.STIFFNESS_LOW)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_HIGH_BOUNCY)
                setStartVelocity((mIconPosition.left - startX) * 2)
            },
            SpringAnimation(mIcon, DynamicAnimation.TRANSLATION_Y, 1f).apply {
                spring = SpringForce(1f).setStiffness(SpringForce.STIFFNESS_LOW)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_HIGH_BOUNCY)
                setStartVelocity((mIconPosition.top - startY) * 3)
            },
        ).forEach { it.start() }
    }

    private fun sendIconInfo() {
        if (mContract != null && Utilities.ATLEAST_Q) {
            mContract!!.sendEndPosition(mIconPosition, mLauncher, mSurfaceView.surfaceControl)
        }
    }

    override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
        drawOnSurface()
        sendIconInfo()
    }

    override fun surfaceChanged(
        surfaceHolder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int,
    ) {
        updateIconLocation()
    }

    override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {}

    override fun surfaceRedrawNeeded(surfaceHolder: SurfaceHolder) {
        drawOnSurface()
    }

    private fun drawOnSurface() {
        val surfaceHolder = mSurfaceView.holder
        if (!surfaceHolder.surface.isValid || mIconBitmap == null) return

        synchronized(this) {
            val c = surfaceHolder.lockHardwareCanvas()
            if (c != null) {
                try {
                    c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    c.drawBitmap(mIconBitmap!!, 0f, 0f, null)
                } finally {
                    surfaceHolder.unlockCanvasAndPost(c)
                }
            }
        }
    }

    private fun setCurrentIconVisible(isVisible: Boolean) {
        if (mIcon != null) {
            setPropertiesVisible(mIcon, isVisible)
        }
    }

    companion object {
        /**
         * Shows the surfaceView for the provided contract
         */
        fun show(launcher: LawnchairLauncher, contract: GestureNavContract?) {
            val view: LawnchairFloatingSurfaceView =
                launcher.viewCache.getView<LawnchairFloatingSurfaceView?>(
                    R.layout.floating_surface_view,
                    launcher,
                    launcher.dragLayer,
                )
            view.mContract = contract
            view.mIsOpen = true

            val anim = AnimatorSet()
            val startDelay = launcher.getSingleFrameMs()
            val launcherContentAnimator: Pair<AnimatorSet?, Runnable?> =
                view.getLauncherContentAnimator(startDelay)
            anim.playTogether(launcherContentAnimator.first, view.getBackgroundAnimator())
            anim.addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        launcherContentAnimator.second!!.run()
                    }
                },
            )

            view.removeViewImmediate()
            launcher.dragLayer.addView(view)
            anim.start()
            view.getIcon()?.let {
                launcher.showFullScreenOverlay(endView = it) {}
            }
        }
    }
}
